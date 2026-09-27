package com.guidinglight.nexusquant.account.infra.okx.readonly;

import com.guidinglight.nexusquant.marketdata.domain.instrument.InstrumentCatalogItem;
import com.guidinglight.nexusquant.marketdata.domain.instrument.OkxVenueRuleContract;
import com.guidinglight.nexusquant.marketdata.domain.instrument.VenueRuleChecksumCalculator;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static com.guidinglight.nexusquant.account.infra.okx.readonly.AccountFactsSnapshot.Status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OkxCurrentPublicRuleReaderTest {
    private static final Instant NOW = Instant.parse("2026-09-27T01:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String RULE = """
            {"instId":"BTC-USDT","instType":"SPOT","state":"live",
             "baseCcy":"BTC","quoteCcy":"USDT","tickSz":"0.1","lotSz":"0.00000001",
             "minSz":"0.00001","maxLmtSz":"1000000","maxMktSz":"1000000",
             "maxLmtAmt":"20000000","maxMktAmt":"1000000","upcChg":[]}
            """;

    @Test
    void constructionHasNoEgressAndObservationUsesOnlyExactUnsignedGet() {
        Fixture fixture = fixture(response(RULE), 200, CLOCK);
        verifyNoInteractions(fixture.client());

        var fact = fixture.reader().observe();

        assertEquals(Status.OBSERVED, fact.status());
        HttpRequest request = fixture.request().get();
        assertEquals("GET", request.method());
        assertEquals("https://openapi.okx.com/api/v5/public/instruments?instType=SPOT&instId=BTC-USDT",
                request.uri().toString());
        assertEquals(Duration.ofSeconds(5), request.timeout().orElseThrow());
        assertTrue(request.bodyPublisher().isEmpty());
        assertEquals(List.of("Accept"), request.headers().map().keySet().stream().toList());
        assertEquals(List.of("application/json"), request.headers().allValues("Accept"));
        verify(fixture.client(), times(1)).sendAsync(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
    }

    @Test
    void currentIdentityMatchesCanonicalRuleChecksumAndExpiresInTwentyFourHours() {
        var fact = fixture(response(RULE), 200, CLOCK).reader().observe();
        var expectedItem = new InstrumentCatalogItem(null, "OKX", "SPOT", "BTC-USDT", "BTC-USDT",
                "BTC", "USDT", "LIVE", new BigDecimal("0.1"), new BigDecimal("0.00000001"),
                new BigDecimal("0.00001"), new BigDecimal("1000000"), new BigDecimal("1000000"),
                "USDT", new BigDecimal("20000000"), new BigDecimal("1000000"),
                OkxVenueRuleContract.SOURCE, OkxVenueRuleContract.SOURCE_SCHEMA_VERSION,
                NOW, null, null, null, null, null);

        assertEquals("OKX:BTC-USDT:" + new VenueRuleChecksumCalculator().calculate(expectedItem), fact.value());
        assertEquals(OkxVenueRuleContract.SOURCE, fact.source());
        assertEquals("CURRENTLY_OBSERVED_PUBLIC_RULE", fact.reason());
        assertEquals(NOW, fact.observedAt());
        assertEquals(NOW.plus(Duration.ofHours(24)), fact.expiresAt());
        assertEquals(Status.STALE, fact.statusAt(NOW.plus(Duration.ofHours(24)).plusNanos(1)));
    }

    @Test
    void changedCurrentRuleProducesItsOwnChecksumWithoutCatalogFallback() {
        String first = fixture(response(RULE), 200, CLOCK).reader().observe().value();
        String changed = fixture(response(RULE.replace("\"tickSz\":\"0.1\"", "\"tickSz\":\"0.01\"")),
                200, CLOCK).reader().observe().value();
        assertFalse(first.equals(changed));
    }

    @Test
    void missingDuplicateMalformedOrOutOfScopeRuleNeverProducesIdentity() {
        for (String payload : List.of(
                "{}", "null", "not-json", response(""), response(RULE + "," + RULE),
                response(RULE.replace("BTC-USDT", "ETH-USDT")),
                response(RULE.replace("\"SPOT\"", "\"SWAP\"")),
                response(RULE.replace("\"live\"", "\"suspend\"")),
                response(RULE.replace("\"BTC\"", "\"XBT\"")),
                response(RULE.replace("\"USDT\"", "\"USD\"")),
                response(RULE.replace("\"tickSz\":\"0.1\"", "\"tickSz\":\"0\"")),
                response(RULE.replace("\"maxLmtAmt\":\"20000000\"", "\"maxLmtAmt\":\"\"")),
                response(RULE.replace("\"maxMktSz\":\"1000000\"", "\"maxMktSz\":null")),
                response(RULE.replace("\"tickSz\":\"0.1\"", "\"tickSz\":\"1e2147483647\"")),
                response(RULE.replace("\"tickSz\":\"0.1\"", "\"tickSz\":0.1")),
                response(RULE.replace("\"tickSz\":\"0.1\"", "\"tickSz\":\"0.1\",\"tickSz\":\"0.01\"")),
                response(RULE) + "{}", response(RULE).replace("\"code\":\"0\"", "\"code\":\"50000\""))) {
            var fact = fixture(payload, 200, CLOCK).reader().observe();
            assertEquals(Status.UNKNOWN, fact.status());
            assertEquals("PUBLIC_RULE_RESPONSE_INVALID", fact.reason());
            assertNull(fact.value());
        }
    }

    @Test
    void staleClockObservationCannotBecomeCurrentIdentity() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(NOW, NOW, NOW.plus(Duration.ofHours(24)).plusSeconds(1));

        var fact = fixture(response(RULE), 200, clock).reader().observe();

        assertEquals(Status.STALE, fact.status());
        assertEquals("PUBLIC_RULE_OBSERVATION_STALE", fact.reason());
        assertNull(fact.value());
    }

    @Test
    void redirectsAndHttpFailureAreNotRetried() {
        for (int status : List.of(301, 429, 500)) {
            Fixture fixture = fixture(response(RULE), status, CLOCK);
            var fact = fixture.reader().observe();
            assertEquals(Status.UNKNOWN, fact.status());
            assertEquals("PUBLIC_RULE_HTTP_FAILED", fact.reason());
            assertNull(fact.value());
            verify(fixture.client(), times(1)).sendAsync(any(HttpRequest.class),
                    org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
        }
    }

    @Test
    void responseByteCapCancelsSubscriptionBeforeParsing() {
        Fixture fixture = fixture(" ".repeat(64 * 1024 + 1), 200, CLOCK);

        var fact = fixture.reader().observe();

        assertEquals(Status.UNKNOWN, fact.status());
        assertNull(fact.value());
        verify(fixture.subscription()).cancel();
    }

    @Test
    void concurrentObservationIsRejectedWithoutStartingSecondRequest() throws Exception {
        HttpClient client = mock(HttpClient.class);
        var pending = new CompletableFuture<HttpResponse<byte[]>>();
        CountDownLatch started = new CountDownLatch(1);
        when(client.sendAsync(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any())).thenAnswer(call -> {
                    started.countDown();
                    return pending;
                });
        var reader = new OkxCurrentPublicRuleReader(client, CLOCK);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = workers.submit(reader::observe);
            assertTrue(started.await(2, TimeUnit.SECONDS));
            var second = reader.observe();
            assertEquals(Status.UNKNOWN, second.status());
            assertEquals("PUBLIC_RULE_REQUEST_IN_PROGRESS", second.reason());
            pending.complete(httpResponse(200, response(RULE).getBytes(StandardCharsets.UTF_8)));
            assertEquals(Status.OBSERVED, first.get(2, TimeUnit.SECONDS).status());
        }
        verify(client, times(1)).sendAsync(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
    }

    @Test
    void bodyCompletionTimeoutCancelsRequestAndReleasesConcurrencyPermit() {
        HttpClient client = mock(HttpClient.class);
        var pending = new CompletableFuture<HttpResponse<byte[]>>();
        when(client.sendAsync(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(pending)
                .thenReturn(CompletableFuture.completedFuture(
                        httpResponse(200, response(RULE).getBytes(StandardCharsets.UTF_8))));
        var reader = new OkxCurrentPublicRuleReader(client, CLOCK);

        var timeout = reader.observe();

        assertEquals(Status.UNKNOWN, timeout.status());
        assertEquals("PUBLIC_RULE_READ_TIMEOUT", timeout.reason());
        assertTrue(pending.isCancelled());
        assertEquals(Status.OBSERVED, reader.observe().status());
    }

    @Test
    void responseFailuresRemainUnknownAndReaderShutdownReleasesClient() {
        HttpClient client = mock(HttpClient.class);
        when(client.sendAsync(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("synthetic failure")));
        var reader = new OkxCurrentPublicRuleReader(client, CLOCK);

        assertEquals(Status.UNKNOWN, reader.observe().status());
        reader.close();

        verify(client).shutdownNow();
    }

    private static Fixture fixture(String payload, int status, Clock clock) {
        HttpClient client = mock(HttpClient.class);
        Flow.Subscription subscription = mock(Flow.Subscription.class);
        AtomicReference<HttpRequest> request = new AtomicReference<>();
        doAnswer(call -> {
            request.set(call.getArgument(0));
            HttpResponse.BodyHandler<byte[]> handler = call.getArgument(1);
            var subscriber = handler.apply(mock(HttpResponse.ResponseInfo.class));
            var responseFuture = subscriber.getBody().toCompletableFuture()
                    .thenApply(bytes -> httpResponse(status, bytes));
            subscriber.onSubscribe(subscription);
            byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
            int split = Math.min(bytes.length, 1024);
            subscriber.onNext(List.of(ByteBuffer.wrap(bytes, 0, split)));
            subscriber.onNext(List.of(ByteBuffer.wrap(bytes, split, bytes.length - split)));
            subscriber.onComplete();
            return responseFuture;
        }).when(client).sendAsync(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
        return new Fixture(new OkxCurrentPublicRuleReader(client, clock), client, request, subscription);
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<byte[]> httpResponse(int status, byte[] bytes) {
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(bytes);
        return response;
    }

    private static String response(String rules) {
        return "{\"code\":\"0\",\"data\":[" + rules + "]}";
    }

    private record Fixture(OkxCurrentPublicRuleReader reader, HttpClient client,
                           AtomicReference<HttpRequest> request, Flow.Subscription subscription) {
    }
}
