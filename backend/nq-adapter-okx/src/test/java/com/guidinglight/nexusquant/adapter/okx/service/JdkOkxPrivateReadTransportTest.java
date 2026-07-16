package com.guidinglight.nexusquant.adapter.okx.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
        assertEquals(OkxPrivateReadError.RESPONSE_PARSE_FAILED, malformedException.category());
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
        assertCategory(OkxPrivateReadError.HTTP_UNEXPECTED_STATUS, noRetry);
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

        assertCategory(OkxPrivateReadError.HTTP_SERVER_ERROR, httpError);
        assertEquals(1, calls.get());
        assertTrue(allZero(body));

        JdkOkxPrivateReadTransport timeout = transport((uri, headers, requestTimeout) -> {
            throw new HttpTimeoutException("safe-timeout");
        });
        assertCategory(OkxPrivateReadError.NETWORK_TIMEOUT, timeout);
    }

    @Test
    void classifiesHttpAuthRateLimitServerAndUnexpectedErrorsThroughFakeServer() throws Exception {
        Map<Integer, OkxPrivateReadError> cases = Map.of(
                401, OkxPrivateReadError.HTTP_UNAUTHORIZED,
                403, OkxPrivateReadError.HTTP_FORBIDDEN,
                418, OkxPrivateReadError.HTTP_UNEXPECTED_STATUS,
                429, OkxPrivateReadError.HTTP_RATE_LIMITED,
                500, OkxPrivateReadError.HTTP_SERVER_ERROR,
                503, OkxPrivateReadError.HTTP_SERVER_ERROR
        );
        for (Map.Entry<Integer, OkxPrivateReadError> entry : cases.entrySet()) {
            try (FakeOkxHttpServer server = new FakeOkxHttpServer(
                    entry.getKey(),
                    "provider-body-must-not-escape"
            )) {
                assertCategory(entry.getValue(), transport(server::exchange));
                assertEquals(1, server.calls());
            }
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
    void classifiesAllowlistedProviderCodesWithoutRawMessageThroughFakeServer() throws Exception {
        for (Map.Entry<String, OkxPrivateReadError> entry : Map.of(
                "50011", OkxPrivateReadError.HTTP_RATE_LIMITED,
                "50101", OkxPrivateReadError.OKX_BUSINESS_REJECTED,
                "50102", OkxPrivateReadError.OKX_TIMESTAMP_INVALID,
                "50105", OkxPrivateReadError.OKX_AUTHENTICATION_FAILED,
                "50110", OkxPrivateReadError.OKX_IP_NOT_ALLOWED,
                "50111", OkxPrivateReadError.OKX_AUTHENTICATION_FAILED,
                "50113", OkxPrivateReadError.OKX_SIGNATURE_INVALID,
                "50120", OkxPrivateReadError.OKX_PERMISSION_DENIED,
                "50035", OkxPrivateReadError.OKX_IP_NOT_ALLOWED,
                "59999", OkxPrivateReadError.OKX_BUSINESS_REJECTED
        ).entrySet()) {
            try (FakeOkxHttpServer server = new FakeOkxHttpServer(
                    200,
                    "{\"code\":\"" + entry.getKey()
                            + "\",\"msg\":\"provider-sensitive-message\",\"data\":[]}"
            )) {
                JdkOkxPrivateReadTransport transport = transport(server::exchange);
                OkxPrivateReadException ex = assertThrows(OkxPrivateReadException.class,
                        () -> execute(transport, OkxPrivateReadRequest.accountConfiguration(),
                                OkxPrivateEnvironment.PRODUCTION));
                assertEquals(entry.getValue(), ex.category());
                assertFalse(ex.getMessage().contains("provider-sensitive-message"));
                assertEquals(1, server.calls());
            }
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

    /**
     * Loopback-only fake server；不会解析或记录 authenticated headers。
     */
    private static final class FakeOkxHttpServer implements AutoCloseable {
        private final HttpServer server;
        private final HttpClient client = HttpClient.newHttpClient();
        private final AtomicInteger calls = new AtomicInteger();

        private FakeOkxHttpServer(int status, String body) throws IOException {
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                calls.incrementAndGet();
                exchange.sendResponseHeaders(status, payload.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(payload);
                }
            });
            server.start();
        }

        private OkxPrivateHttpExchange.Response exchange(
                URI ignoredProductionUri,
                Map<String, String> ignoredAuthenticatedHeaders,
                Duration timeout
        ) throws IOException, InterruptedException {
            URI loopback = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/fake-okx");
            HttpResponse<byte[]> response = client.send(
                    HttpRequest.newBuilder(loopback).timeout(timeout).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray()
            );
            return new OkxPrivateHttpExchange.Response(response.statusCode(), response.body());
        }

        private int calls() {
            return calls.get();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
