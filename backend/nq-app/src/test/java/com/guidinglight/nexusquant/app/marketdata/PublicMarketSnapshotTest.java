package com.guidinglight.nexusquant.app.marketdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.config.PublicMarketDataOutboundSettings;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.policy.PublicMarketDataOutboundPolicy;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.model.PublicMarketDataEndpointCategory;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.model.PublicMarketDataOutboundRequest;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.model.PublicMarketDataOutboundErrorCategory;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.port.PublicMarketDataOutboundClient;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.transport.DisabledPublicMarketDataOutboundClient;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.transport.JdkPublicMarketDataOutboundClient;
import com.guidinglight.nexusquant.adapter.okx.marketdata.OkxHistoricalKlineAdapter;
import com.guidinglight.nexusquant.api.web.ApiExceptionHandler;
import com.guidinglight.nexusquant.app.config.PublicMarketDataOutboundConfiguration;
import com.guidinglight.nexusquant.marketdata.api.web.PublicMarketSnapshotController;
import com.guidinglight.nexusquant.marketdata.domain.PublicMarketSnapshot;
import com.guidinglight.nexusquant.marketdata.domain.instrument.InstrumentCatalogItem;
import com.guidinglight.nexusquant.marketdata.domain.instrument.port.InstrumentCatalogReadPort;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import static com.guidinglight.nexusquant.marketdata.domain.PublicMarketSnapshot.Freshness.*;
import static com.guidinglight.nexusquant.marketdata.domain.PublicMarketSnapshot.SourceStatus.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Timeout(12)
class PublicMarketSnapshotTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final Instant now = Instant.now();
    private final Instant open = now.truncatedTo(ChronoUnit.MINUTES);
    private final AtomicInteger tickerCalls = new AtomicInteger();
    private final AtomicInteger candleCalls = new AtomicInteger();
    private final AtomicInteger catalogReads = new AtomicInteger();
    private final ConcurrentLinkedQueue<String> unexpectedRequests = new ConcurrentLinkedQueue<>();
    private HttpServer upstream;
    private ExecutorService upstreamExecutor;
    private volatile Response ticker;
    private volatile Response candles;
    private PublicMarketSnapshotService snapshots;
    private volatile CountDownLatch firstRequests;

    @BeforeEach
    void start() throws Exception {
        ticker = new Response(200, ticker(now, "101.123456789012345678"));
        candles = new Response(200, candles(open, "0"));
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstreamExecutor = Executors.newVirtualThreadPerTaskExecutor();
        upstream.setExecutor(upstreamExecutor);
        upstream.createContext("/", this::reply);
        upstream.start();
    }

    @AfterEach
    void stop() {
        if (snapshots != null) snapshots.stop();
        upstream.stop(0);
        upstreamExecutor.shutdownNow();
        assertTrue(unexpectedRequests.isEmpty(), unexpectedRequests.toString());
    }

    @Test
    void exactStringTickerAndClosedOrFormingCandlesComeFromDifferentPublicFacts() throws Exception {
        setup(true, Duration.ofSeconds(2));
        mvc().perform(get("/api/marketdata/public-snapshot")
                        .param("exchangeCode", "OKX").param("marketType", "SPOT")
                        .param("symbol", "BTC-USDT").param("interval", "1m"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.lastPrice").value("101.123456789012345678"))
                .andExpect(jsonPath("$.lastPriceSource").value("OKX_PUBLIC_TICKER"))
                .andExpect(jsonPath("$.lastPriceObservedAt").exists())
                .andExpect(jsonPath("$.lastPriceReceivedAt").exists())
                .andExpect(jsonPath("$.barsObservedAt").exists())
                .andExpect(jsonPath("$.bars[0].status").value("CLOSED"))
                .andExpect(jsonPath("$.bars[1].status").value("FORMING"))
                .andExpect(jsonPath("$.bars[1].closePrice").value("100.000000000000000001"))
                .andExpect(jsonPath("$.bars[1].volume").value("2.500000000000000001"))
                .andExpect(jsonPath("$.source").value("OKX_PUBLIC_CANDLES"));
        assertEquals(1, tickerCalls.get());
        assertEquals(1, candleCalls.get());
        assertEquals(1, catalogReads.get());
    }

    @Test
    void formingBucketOnlyClosesWithExplicitVenueConfirmation() {
        setup(true, Duration.ofSeconds(2));
        Instant earlier = open.minusSeconds(60);
        candles = new Response(200, candles(earlier, "0"));
        assertEquals(PublicMarketSnapshot.BarStatus.FORMING, read().bars().getLast().status());
        assertEquals(STALE, read().barsFreshness());
        candles = new Response(200, candles(earlier, "1"));
        assertEquals(PublicMarketSnapshot.BarStatus.CLOSED, read().bars().getLast().status());
    }

    @Test
    void malformedCandlesAndIncompleteConfirmationNeverRejectAValidTicker() {
        setup(true, Duration.ofSeconds(2));
        candles = new Response(200, candles(open, "0") + "{}");
        var malformed = read();
        assertEquals(PARTIAL, malformed.freshness());
        assertEquals(INVALID_RESPONSE, malformed.barsStatus());
        assertTrue(malformed.bars().isEmpty());
        assertEquals("101.123456789012345678", malformed.lastPrice());

        candles = new Response(200, candles(open, "0").replace(",\"0\"]", "]"));
        assertEquals(INCOMPLETE_RESPONSE, read().barsStatus());
        candles = new Response(200, candles(open, "unknown"));
        assertEquals(INVALID_RESPONSE, read().barsStatus());
    }

    @Test
    void wrongOrMissingTickerDoesNotBorrowCandleClose() {
        setup(true, Duration.ofSeconds(2));
        ticker = new Response(200, ticker(now, "100").replace("BTC-USDT", "ETH-USDT"));
        var wrong = read();
        assertEquals(INVALID_RESPONSE, wrong.tickerStatus());
        assertNull(wrong.lastPrice());
        assertNull(wrong.lastPriceObservedAt());
        assertEquals(AVAILABLE, wrong.barsStatus());
        ticker = new Response(200, "{\"code\":\"0\",\"data\":[]}");
        assertEquals(INCOMPLETE_RESPONSE, read().tickerStatus());
        ticker = new Response(200, ticker(now.minusSeconds(91), "100"));
        assertEquals(STALE, read().tickerFreshness());
    }

    @Test
    void defaultNoOutboundDoesNotTouchFakeServerAndUnsupportedSelectionIsRejectedBeforeNetwork() throws Exception {
        setup(false, Duration.ofSeconds(2));
        assertEquals(DISABLED, read().tickerStatus());
        assertEquals(DISABLED, read().barsStatus());
        assertEquals(UNAVAILABLE, read().freshness());
        mvc().perform(get("/api/marketdata/public-snapshot")
                        .param("exchangeCode", "OKX").param("marketType", "SPOT")
                        .param("symbol", "ETH-USDT").param("interval", "1m"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("PUBLIC_MARKET_SNAPSHOT_UNSUPPORTED"));
        assertEquals(0, tickerCalls.get());
        assertEquals(0, candleCalls.get());
    }

    enum PermissionCase {
        PROCESS_CI(Map.of("CI", "true")),
        PROCESS_NO_OUTBOUND(Map.of("NQ_NO_OUTBOUND", "true")),
        PROCESS_BOTH(Map.of("CI", "true", "NQ_NO_OUTBOUND", "true")),
        SPRING_CI(Map.of()), SPRING_NO_OUTBOUND(Map.of()), AUTHORIZED(Map.of());

        final Map<String, String> vetoes;
        PermissionCase(Map<String, String> vetoes) { this.vetoes = vetoes; }
    }

    @ParameterizedTest
    @EnumSource(PermissionCase.class)
    @Timeout(40)
    void hiddenProcessEnvironmentCannotGrantOutbound(PermissionCase scenario, @TempDir Path directory)
            throws Exception {
        PublicMarketReadProcess.run(getClass(), directory.resolve("permission.log"), scenario.vetoes,
                scenario.name(), Integer.toString(upstream.getAddress().getPort()));
        int expected = scenario == PermissionCase.AUTHORIZED ? 1 : 0;
        assertEquals(expected, tickerCalls.get());
        assertEquals(expected, candleCalls.get());
        System.out.printf("%s ticker=%d candles=%d unexpected=%d%n", scenario,
                tickerCalls.get(), candleCalls.get(), unexpectedRequests.size());
    }

    public static void main(String[] args) {
        PermissionCase scenario = PermissionCase.valueOf(args[0]);
        URI origin = URI.create("http://127.0.0.1:" + Integer.parseInt(args[1]));
        boolean authorized = scenario == PermissionCase.AUTHORIZED;
        try (var context = new AnnotationConfigApplicationContext()) {
            var environment = context.getEnvironment();
            for (String variable : List.of("CI", "NQ_NO_OUTBOUND")) {
                assertEquals(scenario.vetoes.get(variable), System.getenv(variable));
                assertNull(environment.getProperty(variable));
                assertFalse(environment.getSystemEnvironment().containsKey(variable));
            }
            environment.getPropertySources().addFirst(new MapPropertySource("snapshot-test", Map.of(
                    "nq.public-marketdata.outbound.enabled", "true",
                    "nq.public-marketdata.outbound.base-url", origin.toString(),
                    "nq.env-safety.ci", scenario == PermissionCase.SPRING_CI,
                    "nq.env-safety.no-outbound", scenario == PermissionCase.SPRING_NO_OUTBOUND)));
            environment.setActiveProfiles("test", "public-marketdata-manual");
            assertFalse(ManualPublicMarketRead.allowed(environment));
            environment.setActiveProfiles("local", "public-marketdata-manual");
            assertEquals(authorized, ManualPublicMarketRead.allowed(environment));
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules());
            context.registerBean(InstrumentCatalogReadPort.class, () -> (exchange, symbols) -> List.of(
                    new InstrumentCatalogItem("OKX", "SPOT", "BTC-USDT", "BTC-USDT", "BTC", "USDT",
                            "LIVE", null, null, null, "FIXTURE")));
            context.register(PublicMarketDataOutboundConfiguration.class, PublicMarketSnapshotService.class);
            context.refresh();
            var client = context.getBean(PublicMarketDataOutboundClient.class);
            var service = context.getBean(PublicMarketSnapshotService.class);
            var result = new PublicMarketSnapshotController(service).read("OKX", "SPOT", "BTC-USDT", "1m").getBody();
            assertNotNull(result);
            assertEquals(authorized ? AVAILABLE : DISABLED, result.tickerStatus());
            assertEquals(authorized ? AVAILABLE : DISABLED, result.barsStatus());
            if (authorized) {
                assertInstanceOf(JdkPublicMarketDataOutboundClient.class, client);
            } else {
                assertInstanceOf(DisabledPublicMarketDataOutboundClient.class, client);
                // 即使直接进入 JDK transport，每次 HTTP 尝试也必须复核相同的许可。
                var transport = new JdkPublicMarketDataOutboundClient(origin, new PublicMarketDataOutboundPolicy(),
                        PublicMarketDataOutboundSettings.defaults(), () -> ManualPublicMarketRead.allowed(environment));
                var request = PublicMarketDataOutboundRequest.publicGet("OKX", PublicMarketDataEndpointCategory.TICKER,
                        "/api/v5/market/ticker?instId=BTC-USDT");
                assertEquals(PublicMarketDataOutboundErrorCategory.DISABLED, transport.fetchSnapshot(request).error());
            }
        }
    }

    @Test
    void slowHttp429BodyDoesNotLoseRetryAfterOrReleaseTheOtherRetry() {
        setup(() -> true, () -> true, Duration.ofSeconds(2), origin(), Duration.ofMillis(1000));
        firstRequests = new CountDownLatch(2);
        ticker = new Response(429, "rate limited", 4500, "75", 100);
        candles = new Response(503, "backoff");
        var result = read();
        assertEquals(RATE_LIMITED, result.tickerStatus());
        assertEquals(RATE_LIMITED, result.barsStatus());
        assertTrue(result.retryAt().isAfter(now.plusSeconds(70)));
        assertEquals(1, tickerCalls.get());
        assertEquals(1, candleCalls.get());
        assertEquals(result.retryAt(), read().retryAt());
        assertEquals(2, tickerCalls.get() + candleCalls.get());
    }

    @Test
    void businessRateLimitFromTickerStopsScheduledCandleRetry() {
        assertBusinessRateLimit(true);
    }

    @Test
    void businessRateLimitFromCandlesStopsScheduledTickerRetry() {
        assertBusinessRateLimit(false);
    }

    private void assertBusinessRateLimit(boolean tickerLimits) {
        setup(() -> true, () -> true, Duration.ofSeconds(2), origin(), Duration.ofMillis(1000));
        firstRequests = new CountDownLatch(2);
        var rejected = new Response(200, "{\"code\":\"" + (tickerLimits ? "50011" : "50040") + "\"}",
                0, null, 100);
        var retryable = new Response(503, "backoff");
        ticker = tickerLimits ? rejected : retryable;
        candles = tickerLimits ? retryable : rejected;
        var result = read();
        assertEquals(RATE_LIMITED, result.tickerStatus());
        assertEquals(RATE_LIMITED, result.barsStatus());
        assertTrue(result.retryAt().isAfter(now.plusSeconds(25)));
        assertEquals(1, tickerCalls.get());
        assertEquals(1, candleCalls.get());
        assertEquals(result.retryAt(), read().retryAt());
        assertEquals(2, tickerCalls.get() + candleCalls.get());
    }

    @Test
    void concurrentLimitsKeepLongestDeadlineWithInvalidRetryAfterFallback() {
        setup(true, Duration.ofSeconds(2));
        firstRequests = new CountDownLatch(2);
        ticker = new Response(429, "ignored", 0, "75");
        candles = new Response(429, "ignored", 0, "invalid", 100);
        var result = read();
        assertEquals(RATE_LIMITED, result.tickerStatus());
        assertEquals(RATE_LIMITED, result.barsStatus());
        assertTrue(result.retryAt().isAfter(now.plusSeconds(70)));
        assertEquals(result.retryAt(), read().retryAt());
        assertEquals(2, tickerCalls.get() + candleCalls.get());
    }

    @Test
    void invalidRetryAfterStartsDefaultCooldown() {
        setup(true, Duration.ofSeconds(2));
        ticker = new Response(429, "ignored", 0, "invalid");
        var result = read();
        assertEquals(RATE_LIMITED, result.tickerStatus());
        assertTrue(result.retryAt().isAfter(now.plusSeconds(25)));
        assertTrue(result.retryAt().isBefore(Instant.now().plusSeconds(31)));
        int requests = tickerCalls.get() + candleCalls.get();
        assertEquals(result.retryAt(), read().retryAt());
        assertEquals(requests, tickerCalls.get() + candleCalls.get());
    }

    @Test
    void throttlingHonorsRetryAfterAcrossPartsAndRequests() {
        setup(true, Duration.ofSeconds(2));
        ticker = new Response(429, "not JSON", 0, "75");
        var initial = read();
        assertEquals(RATE_LIMITED, initial.tickerStatus());
        assertTrue(initial.retryAt().isAfter(now.plusSeconds(70)));
        int requests = tickerCalls.get() + candleCalls.get();
        var cooled = read();
        assertEquals(RATE_LIMITED, cooled.tickerStatus());
        assertEquals(RATE_LIMITED, cooled.barsStatus());
        assertEquals(initial.retryAt(), cooled.retryAt());
        assertNull(cooled.observedAt());
        assertEquals(requests, tickerCalls.get() + candleCalls.get());
    }

    @Test
    void upstreamServerErrorRetriesAtMostTwiceAndReturnsNoPrice() {
        setup(true, Duration.ofSeconds(2));
        ticker = new Response(503, "unavailable");
        var result = read();
        assertEquals(SOURCE_UNAVAILABLE, result.tickerStatus());
        assertNull(result.lastPrice());
        assertEquals(3, tickerCalls.get());
        assertEquals(1, candleCalls.get());
    }

    @Test
    void timeoutAndOversizeResponsesAreBounded() {
        setup(true, Duration.ofMillis(240));
        ticker = new Response(200, ticker.body(), 1000, null);
        candles = new Response(200, "x".repeat(256_001));
        var result = read();
        assertEquals(TIMEOUT, result.tickerStatus());
        assertEquals(INVALID_RESPONSE, result.barsStatus());
        assertEquals(UNAVAILABLE, result.freshness());
        assertEquals(1, tickerCalls.get());
        assertEquals(1, candleCalls.get());
    }

    @Test
    void rejectsOutOfScopeVenueDestinationWithoutSendingRequests() {
        setup(true, Duration.ofSeconds(2), URI.create("http://127.0.0.2:" + upstream.getAddress().getPort()));
        assertEquals(DENIED, read().tickerStatus());
        assertEquals(DENIED, read().barsStatus());
        assertEquals(0, tickerCalls.get() + candleCalls.get());
    }

    @Test
    void dailyCandlesUseOkxUtcPlusEightBucket() {
        var parser = new OkxPublicSnapshotParser(mapper);
        var opened = Instant.parse("2026-11-24T16:00:00Z");
        var received = opened.plusSeconds(120);
        var parsed = parser.candles(("{\"code\":\"0\",\"data\":[" + row(opened, "0") + "]}")
                        .getBytes(StandardCharsets.UTF_8),
                com.guidinglight.nexusquant.marketdata.domain.BarInterval.ONE_DAY, received);
        assertEquals("1D", OkxHistoricalKlineAdapter.venueInterval("1d"));
        assertEquals(opened.plusSeconds(86400).minusMillis(1), parsed.getFirst().closeTime());
    }

    private URI origin() {
        return URI.create("http://127.0.0.1:" + upstream.getAddress().getPort());
    }

    private void setup(boolean enabled, Duration timeout) {
        setup(enabled, timeout, origin());
    }

    private void setup(boolean enabled, Duration timeout, URI origin) {
        setup(() -> enabled, () -> true, timeout, origin, Duration.ofMillis(1));
    }

    private JdkPublicMarketDataOutboundClient setup(BooleanSupplier enabled, BooleanSupplier transportAllowed,
                                                    Duration timeout, URI origin, Duration backoff) {
        var settings = new PublicMarketDataOutboundSettings(Duration.ofMillis(150), timeout, timeout, 2,
                backoff, backoff);
        var outbound = new JdkPublicMarketDataOutboundClient(origin, new PublicMarketDataOutboundPolicy(),
                settings, transportAllowed);
        snapshots = new PublicMarketSnapshotService((exchange, symbols) -> {
            catalogReads.incrementAndGet();
            return "OKX".equals(exchange) && List.of("BTC-USDT").equals(symbols)
                    ? List.of(new InstrumentCatalogItem("OKX", "SPOT", "BTC-USDT", "BTC-USDT", "BTC", "USDT",
                            "LIVE", null, null, null, "FIXTURE")) : List.of();
        }, () -> outbound, mapper, enabled, Clock.systemUTC());
        return outbound;
    }

    private PublicMarketSnapshot read() {
        return snapshots.read("OKX", "SPOT", "BTC-USDT", "1m");
    }

    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new PublicMarketSnapshotController(snapshots))
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).build();
    }

    private void reply(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().toString();
            if (!"GET".equals(exchange.getRequestMethod()) || exchange.getRequestHeaders().containsKey("Authorization")
                    || exchange.getRequestHeaders().containsKey("OK-ACCESS-KEY")) {
                unexpectedRequests.add(path);
                exchange.sendResponseHeaders(403, -1);
                return;
            }
            Response response;
            if (path.equals("/api/v5/market/ticker?instId=BTC-USDT")) {
                tickerCalls.incrementAndGet();
                response = ticker;
            } else if (path.equals("/api/v5/market/candles?instId=BTC-USDT&bar=1m&limit=100")) {
                candleCalls.incrementAndGet();
                response = candles;
            } else {
                unexpectedRequests.add(path);
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            if (firstRequests != null) {
                firstRequests.countDown();
                try {
                    if (!firstRequests.await(1, TimeUnit.SECONDS)) throw new IOException("夹具请求未并发到达");
                    Thread.sleep(response.headerDelayMs());
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            if (response.retryAfter() != null) exchange.getResponseHeaders().set("Retry-After", response.retryAfter());
            byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(response.status(), body.length);
            if (response.delayMs() > 0) {
                try {
                    Thread.sleep(response.delayMs());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            exchange.getResponseBody().write(body);
        }
    }

    private static String ticker(Instant timestamp, String last) {
        return """
                {"code":"0","data":[{"instId":"BTC-USDT","instType":"SPOT","last":"%s","ts":"%s"}]}
                """.formatted(last, timestamp.toEpochMilli());
    }

    private static String candles(Instant open, String confirm) {
        return "{\"code\":\"0\",\"data\":[" + row(open, confirm) + "," + row(open.minusSeconds(60), "1") + "]}";
    }

    private static String row(Instant open, String confirm) {
        return """
                ["%s","100.01","101.02","99.98","100.000000000000000001","2.500000000000000001","2.5","250.0","%s"]
                """.formatted(open.toEpochMilli(), confirm);
    }

    private record Response(int status, String body, int delayMs, String retryAfter, int headerDelayMs) {
        private Response(int status, String body) { this(status, body, 0, null); }
        private Response(int status, String body, int delayMs, String retryAfter) {
            this(status, body, delayMs, retryAfter, 0);
        }
    }
}
