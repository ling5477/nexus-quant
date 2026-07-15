package com.guidinglight.nexusquant.adapter.okx.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdkOkxPrivateReadTransportTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-13T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void sendsOnlyFixedGlobalGetAndParsesSanitizedConfiguration() {
        AtomicReference<URI> uri = new AtomicReference<>();
        AtomicReference<Map<String, String>> headers = new AtomicReference<>();
        JdkOkxPrivateReadTransport transport = transport((requestUri, requestHeaders, timeout) -> {
            uri.set(requestUri);
            headers.set(Map.copyOf(requestHeaders));
            return response(200, "{\"code\":\"0\",\"data\":[{\"perm\":\"read_only\",\"ip\":\"[REDACTED]\",\"uid\":\"ignored\"}]}");
        });

        OkxPrivateReadResult result = execute(transport, OkxPrivateReadRequest.accountConfiguration(), OkxPrivateEnvironment.DEMO);

        assertEquals(URI.create("https://openapi.okx.com/api/v5/account/config"), uri.get());
        assertEquals(java.util.Set.of("READ_ONLY"), result.normalizedPermissions());
        assertTrue(result.complete());
        assertTrue(result.ipAllowlistConfigured());
        assertEquals("1", headers.get().get("x-simulated-trading"));
        assertTrue(headers.get().containsKey("OK-ACCESS-SIGN"));
    }

    @Test
    void keepsIpAllowlistContentInsideTransportAndFailsClosedForEmptyBinding() {
        JdkOkxPrivateReadTransport transport = transport((uri, headers, timeout) -> response(
                200,
                "{\"code\":\"0\",\"data\":[{\"perm\":\"read_only\",\"ip\":\"\"}]}"
        ));

        OkxPrivateReadResult result = execute(
                transport,
                OkxPrivateReadRequest.accountConfiguration(),
                OkxPrivateEnvironment.PRODUCTION
        );

        assertTrue(result.complete());
        assertFalse(result.ipAllowlistConfigured());
        assertEquals(9, OkxPrivateReadResult.class.getRecordComponents().length);
        assertTrue(Arrays.stream(OkxPrivateReadResult.class.getRecordComponents())
                .noneMatch(component -> component.getType().equals(String.class)));
    }

    @Test
    void comparesExpectedIpByNormalizedExactLiteralWithoutReturningAllowlist() {
        Map<String, OkxIpAllowlistStatus> cases = Map.of(
                "203.0.113.9,203.0.113.8", OkxIpAllowlistStatus.MATCHED,
                "203.0.113.9", OkxIpAllowlistStatus.MISMATCHED,
                "", OkxIpAllowlistStatus.MISSING,
                "203.0.113.0/24", OkxIpAllowlistStatus.UNKNOWN,
                "203.0.113.8,", OkxIpAllowlistStatus.UNKNOWN
        );
        for (Map.Entry<String, OkxIpAllowlistStatus> entry : cases.entrySet()) {
            JdkOkxPrivateReadTransport transport = transport((uri, headers, timeout) -> response(
                    200,
                    "{\"code\":\"0\",\"data\":[{\"perm\":\"read_only\",\"ip\":\""
                            + entry.getKey() + "\"}]}"
            ));

            OkxPrivateReadResult result = execute(
                    transport,
                    OkxPrivateReadRequest.accountConfiguration("203.0.113.8"),
                    OkxPrivateEnvironment.PRODUCTION
            );

            assertEquals(entry.getValue(), result.ipAllowlistStatus());
        }

        JdkOkxPrivateReadTransport missingField = transport((uri, headers, timeout) -> response(
                200,
                "{\"code\":\"0\",\"data\":[{\"perm\":\"read_only\"}]}"
        ));
        assertEquals(
                OkxIpAllowlistStatus.UNKNOWN,
                execute(missingField, OkxPrivateReadRequest.accountConfiguration("203.0.113.8"),
                        OkxPrivateEnvironment.PRODUCTION).ipAllowlistStatus()
        );
        assertThrows(IllegalArgumentException.class,
                () -> OkxPrivateReadRequest.accountConfiguration("example.com"));
        assertThrows(IllegalArgumentException.class,
                () -> OkxPrivateReadRequest.accountConfiguration("203.0.113.0/24"));
    }

    @Test
    void parsesOnlyAssetCountAndMarksMissingBalanceFieldsPartial() {
        JdkOkxPrivateReadTransport transport = transport((uri, headers, timeout) -> response(200,
                "{\"code\":\"0\",\"data\":[{\"details\":["
                        + "{\"ccy\":\"BTC\",\"cashBal\":\"1\",\"availBal\":\"1\",\"frozenBal\":\"0\",\"uTime\":\"1\"},"
                        + "{\"ccy\":\"ETH\",\"cashBal\":\"\",\"availBal\":\"1\",\"frozenBal\":\"0\",\"uTime\":\"1\"}]}]}"));

        OkxPrivateReadResult result = execute(
                transport,
                OkxPrivateReadRequest.accountBalance(List.of("ETH", "BTC")),
                OkxPrivateEnvironment.PRODUCTION
        );

        assertEquals(2, result.assetCount());
        assertFalse(result.complete());
    }

    @Test
    void marksMalformedBalanceValuesPartial() {
        JdkOkxPrivateReadTransport transport = transport((uri, headers, timeout) -> response(200,
                "{\"code\":\"0\",\"data\":[{\"details\":["
                        + "{\"ccy\":\"BTC\",\"cashBal\":\"not-a-number\",\"availBal\":\"1\","
                        + "\"frozenBal\":\"0\",\"uTime\":\"not-a-time\"}]}]}"));

        OkxPrivateReadResult result = execute(
                transport,
                OkxPrivateReadRequest.accountBalance(List.of("BTC")),
                OkxPrivateEnvironment.PRODUCTION
        );

        assertFalse(result.complete());
    }

    @Test
    void marksEmptyOrMultipleBalanceRowsIncompleteWithoutClaimingZeroAssets() {
        for (String responseBody : List.of(
                "{\"code\":\"0\",\"data\":[{\"details\":[]}]}",
                "{\"code\":\"0\",\"data\":[{\"details\":[]},{\"details\":[]}]}"
        )) {
            JdkOkxPrivateReadTransport transport = transport((uri, headers, timeout) -> response(200, responseBody));

            OkxPrivateReadResult result = execute(
                    transport,
                    OkxPrivateReadRequest.accountBalance(List.of("BTC")),
                    OkxPrivateEnvironment.PRODUCTION
            );
            assertFalse(result.complete());
        }
    }

    @Test
    void rejectsRedirectOversizeMalformedAndDoesNotRetryHttpFailure() {
        assertCategory(OkxPrivateReadError.REDIRECT_REJECTED,
                transport((uri, headers, timeout) -> response(302, "")));
        String providerMarker = "provider-raw-marker-should-not-escape";
        JdkOkxPrivateReadTransport malformed = transport((uri, headers, timeout) ->
                response(200, providerMarker));
        OkxPrivateReadException malformedException = assertThrows(
                OkxPrivateReadException.class,
                () -> execute(malformed, OkxPrivateReadRequest.accountConfiguration(), OkxPrivateEnvironment.PRODUCTION)
        );
        assertEquals(OkxPrivateReadError.MALFORMED_RESPONSE, malformedException.category());
        assertEquals(null, malformedException.getCause());
        assertFalse(malformedException.toString().contains(providerMarker));
        byte[] oversized = new byte[JdkOkxPrivateReadTransport.MAX_RESPONSE_BYTES + 1];
        assertCategory(OkxPrivateReadError.RESPONSE_TOO_LARGE,
                transport((uri, headers, timeout) -> new OkxPrivateHttpExchange.Response(200, oversized)));

        AtomicInteger calls = new AtomicInteger();
        JdkOkxPrivateReadTransport noRetry = transport((uri, headers, timeout) -> {
            calls.incrementAndGet();
            return response(400, "ignored");
        });
        assertCategory(OkxPrivateReadError.HTTP_ERROR, noRetry);
        assertEquals(1, calls.get());
    }

    @Test
    void clearsProviderBodyOnHttpErrorAndRejectsTimeoutWithoutRetry() {
        byte[] body = "provider-error-marker".getBytes(StandardCharsets.UTF_8);
        AtomicInteger calls = new AtomicInteger();
        JdkOkxPrivateReadTransport httpError = transport((uri, headers, timeout) -> {
            calls.incrementAndGet();
            return new OkxPrivateHttpExchange.Response(500, body);
        });

        assertCategory(OkxPrivateReadError.HTTP_ERROR, httpError);
        assertEquals(1, calls.get());
        assertTrue(allZero(body));

        JdkOkxPrivateReadTransport timeout = transport((uri, headers, requestTimeout) -> {
            throw new HttpTimeoutException("safe-timeout");
        });
        assertCategory(OkxPrivateReadError.TIMEOUT, timeout);
    }

    @Test
    void classifiesHttpAuthRateLimitAndServerErrorsWithoutRetry() {
        Map<Integer, OkxPrivateReadError> cases = Map.of(
                401, OkxPrivateReadError.AUTHENTICATION_FAILURE,
                403, OkxPrivateReadError.AUTHENTICATION_FAILURE,
                429, OkxPrivateReadError.RATE_LIMITED,
                500, OkxPrivateReadError.HTTP_ERROR,
                503, OkxPrivateReadError.HTTP_ERROR
        );
        for (Map.Entry<Integer, OkxPrivateReadError> entry : cases.entrySet()) {
            AtomicInteger calls = new AtomicInteger();
            JdkOkxPrivateReadTransport transport = transport((uri, headers, timeout) -> {
                calls.incrementAndGet();
                return response(entry.getKey(), "provider-body-must-not-escape");
            });
            assertCategory(entry.getValue(), transport);
            assertEquals(1, calls.get());
        }
    }

    @Test
    void failsClosedForMultipleConfigurationRowsIncludingDangerousOrUnknownPermissions() {
        for (String secondPermission : List.of("read_only", "trade", "withdraw", "unknown_scope", "")) {
            JdkOkxPrivateReadTransport transport = transport((uri, headers, timeout) -> response(
                    200,
                    "{\"code\":\"0\",\"data\":[{\"perm\":\"read_only\"},{\"perm\":\""
                            + secondPermission + "\"}]}"
            ));

            OkxPrivateReadResult result = execute(
                    transport,
                    OkxPrivateReadRequest.accountConfiguration(),
                    OkxPrivateEnvironment.PRODUCTION
            );
            assertFalse(result.complete());
            assertTrue(result.normalizedPermissions().isEmpty());
        }
    }

    @Test
    void failsClosedForMissingNullOrEmptyConfigurationData() {
        for (String responseBody : List.of(
                "{\"code\":\"0\"}",
                "{\"code\":\"0\",\"data\":null}",
                "{\"code\":\"0\",\"data\":[]}"
        )) {
            JdkOkxPrivateReadTransport transport = transport((uri, headers, timeout) -> response(200, responseBody));

            OkxPrivateReadResult result = execute(
                    transport,
                    OkxPrivateReadRequest.accountConfiguration(),
                    OkxPrivateEnvironment.PRODUCTION
            );
            assertFalse(result.complete());
            assertTrue(result.normalizedPermissions().isEmpty());
        }
    }

    @Test
    void subscriberCancelsDuringReceiveWhenLimitIsExceeded() {
        JdkOkxPrivateReadTransport.LimitedBodySubscriber subscriber =
                new JdkOkxPrivateReadTransport.LimitedBodySubscriber(4);
        AtomicBoolean cancelled = new AtomicBoolean();
        subscriber.onSubscribe(new Flow.Subscription() {
            @Override
            public void request(long n) {
            }

            @Override
            public void cancel() {
                cancelled.set(true);
            }
        });

        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[]{1, 2, 3, 4, 5})));

        CompletionException ex = assertThrows(
                CompletionException.class,
                () -> subscriber.getBody().toCompletableFuture().join()
        );
        assertTrue(cancelled.get());
        assertTrue(ex.getCause() instanceof IOException);
        assertEquals("response exceeded configured byte limit", ex.getCause().getMessage());
    }

    @Test
    void enforcesSingleInFlightRequest() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        JdkOkxPrivateReadTransport transport = transport((uri, headers, timeout) -> {
            entered.countDown();
            if (!release.await(2, TimeUnit.SECONDS)) {
                throw new IOException("test synchronization timeout");
            }
            return response(200, "{\"code\":\"0\",\"data\":[{\"perm\":\"read_only\"}]}");
        });
        Thread first = Thread.ofVirtual().start(() -> {
            try {
                execute(transport, OkxPrivateReadRequest.accountConfiguration(), OkxPrivateEnvironment.PRODUCTION);
            } catch (Throwable throwable) {
                firstFailure.set(throwable);
            }
        });

        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertCategory(OkxPrivateReadError.RATE_LIMITED, transport);
        } finally {
            release.countDown();
            first.join();
        }
        assertEquals(null, firstFailure.get());
    }

    @Test
    void classifiesConfirmedProviderCategoriesWithoutRawMessage() {
        for (Map.Entry<String, OkxPrivateReadError> entry : Map.of(
                "50011", OkxPrivateReadError.RATE_LIMITED,
                "50101", OkxPrivateReadError.ENVIRONMENT_MISMATCH,
                "50102", OkxPrivateReadError.CLOCK_SKEW,
                "50105", OkxPrivateReadError.AUTHENTICATION_FAILURE,
                "50110", OkxPrivateReadError.IP_ALLOWLIST_FAILED,
                "50111", OkxPrivateReadError.INVALID_API_KEY,
                "50113", OkxPrivateReadError.SIGNATURE_FAILURE,
                "50120", OkxPrivateReadError.PERMISSION_BLOCKED,
                "50035", OkxPrivateReadError.IP_ALLOWLIST_FAILED,
                "59999", OkxPrivateReadError.OKX_PROVIDER_ERROR
        ).entrySet()) {
            JdkOkxPrivateReadTransport transport = transport((uri, headers, timeout) -> response(
                    200,
                    "{\"code\":\"" + entry.getKey() + "\",\"msg\":\"provider-sensitive-message\",\"data\":[]}"
            ));
            OkxPrivateReadException ex = assertThrows(OkxPrivateReadException.class,
                    () -> execute(transport, OkxPrivateReadRequest.accountConfiguration(), OkxPrivateEnvironment.PRODUCTION));
            assertEquals(entry.getValue(), ex.category());
            assertFalse(ex.getMessage().contains("provider-sensitive-message"));
        }
    }

    @Test
    void enforcesTimeoutUpperBounds() {
        assertThrows(IllegalArgumentException.class, () -> new JdkOkxPrivateReadTransport(
                new ObjectMapper(), CLOCK, Duration.ofSeconds(6), Duration.ofSeconds(5)));
        assertThrows(IllegalArgumentException.class, () -> new JdkOkxPrivateReadTransport(
                new ObjectMapper(), CLOCK, Duration.ofSeconds(2), Duration.ofSeconds(11)));
    }

    private static JdkOkxPrivateReadTransport transport(OkxPrivateHttpExchange exchange) {
        return new JdkOkxPrivateReadTransport(new ObjectMapper(), CLOCK, Duration.ofSeconds(5), exchange);
    }

    private static OkxPrivateReadResult execute(
            JdkOkxPrivateReadTransport transport,
            OkxPrivateReadRequest request,
            OkxPrivateEnvironment environment
    ) {
        try (OkxPrivateCredentialContext credential = new OkxPrivateCredentialContext(
                "key".toCharArray(), "secret".toCharArray(), "pass".toCharArray())) {
            return transport.execute(request, credential, environment);
        }
    }

    private static void assertCategory(OkxPrivateReadError category, JdkOkxPrivateReadTransport transport) {
        OkxPrivateReadException ex = assertThrows(OkxPrivateReadException.class,
                () -> execute(transport, OkxPrivateReadRequest.accountConfiguration(), OkxPrivateEnvironment.PRODUCTION));
        assertEquals(category, ex.category());
    }

    private static OkxPrivateHttpExchange.Response response(int status, String body) {
        return new OkxPrivateHttpExchange.Response(
                status,
                body.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static boolean allZero(byte[] value) {
        return Arrays.equals(value, new byte[value.length]);
    }
}
