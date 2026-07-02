package com.guidinglight.nexusquant.adapter.api.publicmarketdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * JdkPublicMarketDataOutboundClientTest 用 localhost fake server 验证 O-1 出站闭环。
 *
 * <p>Why: 本测试不访问 OKX/Binance/Bybit/Gate/Coinbase/Kraken，不读取 credential，不执行 O-5 manual
 * real public smoke。fake server 覆盖 success、429、timeout、5xx、denylist、retry 上限和 redaction，
 * 证明真实交易所外联默认仍不进入测试链路。</p>
 */
class JdkPublicMarketDataOutboundClientTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-01T00:00:00Z"),
            ZoneOffset.UTC);

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void fakeServerSuccessShouldMapToHealthyWithoutTradingAuthorization() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        URI baseUri = startServer(exchange -> {
            calls.incrementAndGet();
            respond(exchange, 200, "{\"rows\":[{\"apiKey\":\"fake-secret-like\"}]}");
        });
        JdkPublicMarketDataOutboundClient client = client(baseUri, PublicMarketDataOutboundSettings.defaults());
        PublicMarketDataOutboundRequest request = new PublicMarketDataOutboundRequest(
                "FAKE",
                PublicMarketDataEndpointCategory.TICKER,
                "/ticker",
                false,
                false,
                "trc-ok",
                "req-ok",
                "1m");

        PublicMarketDataOutboundResult result = client.fetch(request);
        PublicMarketDataQualitySummary summary = PublicMarketDataSourceHealthMapper.map(result);
        PublicMarketDataLogSummary logSummary = PublicMarketDataLogSummary.from(request, result);

        assertEquals(1, calls.get());
        assertEquals(PublicMarketDataOutboundErrorCategory.NONE, result.errorCategory());
        assertEquals(200, result.statusCode());
        assertEquals(PublicMarketDataQualitySummary.SourceHealth.HEALTHY, summary.sourceHealth());
        assertEquals(PublicMarketDataQualitySummary.DataOrigin.FAKE_SERVER, summary.dataOrigin());
        assertFalse(summary.tradingAuthorization());
        assertFalse(logSummary.toString().contains("fake-secret-like"));
        assertFalse(result.message().contains("fake-secret-like"));
    }

    @Test
    void fakeServerRateLimitShouldMapToRateLimitedAndBoundedRetry() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        URI baseUri = startServer(exchange -> {
            calls.incrementAndGet();
            respond(exchange, 429, "{\"error\":\"rate limited\"}");
        });
        JdkPublicMarketDataOutboundClient client = client(baseUri, PublicMarketDataOutboundSettings.defaults());

        PublicMarketDataOutboundResult result = client.fetch(PublicMarketDataOutboundRequest.publicGet(
                "FAKE",
                PublicMarketDataEndpointCategory.SERVER_TIME,
                "/time"));
        PublicMarketDataQualitySummary summary = PublicMarketDataSourceHealthMapper.map(result);

        assertEquals(3, calls.get());
        assertEquals(3, result.attempts());
        assertEquals(PublicMarketDataOutboundErrorCategory.RATE_LIMITED, result.errorCategory());
        assertEquals(PublicMarketDataQualitySummary.SourceHealth.RATE_LIMITED, summary.sourceHealth());
    }

    @Test
    void fakeServerTimeoutShouldMapToTimeout() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        URI baseUri = startServer(exchange -> {
            calls.incrementAndGet();
            sleep(300);
            respond(exchange, 200, "{\"rows\":[{}]}");
        });
        PublicMarketDataOutboundSettings fastTimeout = new PublicMarketDataOutboundSettings(
                Duration.ofMillis(100),
                Duration.ofMillis(100),
                Duration.ofMillis(100),
                0,
                Duration.ZERO,
                Duration.ZERO);
        JdkPublicMarketDataOutboundClient client = client(baseUri, fastTimeout);

        PublicMarketDataOutboundResult result = client.fetch(PublicMarketDataOutboundRequest.publicGet(
                "FAKE",
                PublicMarketDataEndpointCategory.OHLCV,
                "/slow-klines"));
        PublicMarketDataQualitySummary summary = PublicMarketDataSourceHealthMapper.map(result);

        assertEquals(1, calls.get());
        assertEquals(PublicMarketDataOutboundErrorCategory.TIMEOUT, result.errorCategory());
        assertEquals(PublicMarketDataQualitySummary.SourceHealth.TIMEOUT, summary.sourceHealth());
    }

    @Test
    void fakeServer5xxShouldMapToErrorAndRetryAtMostTwice() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        URI baseUri = startServer(exchange -> {
            calls.incrementAndGet();
            respond(exchange, 503, "{\"error\":\"temporary\"}");
        });
        JdkPublicMarketDataOutboundClient client = client(baseUri, PublicMarketDataOutboundSettings.defaults());

        PublicMarketDataOutboundResult result = client.fetch(PublicMarketDataOutboundRequest.publicGet(
                "FAKE",
                PublicMarketDataEndpointCategory.INSTRUMENTS,
                "/instruments"));
        PublicMarketDataQualitySummary summary = PublicMarketDataSourceHealthMapper.map(result);

        assertEquals(3, calls.get());
        assertEquals(3, result.attempts());
        assertEquals(PublicMarketDataOutboundErrorCategory.TEMPORARY_FAILURE, result.errorCategory());
        assertEquals(PublicMarketDataQualitySummary.SourceHealth.ERROR, summary.sourceHealth());
    }

    @Test
    void fakeServerMalformedResponseShouldMapToInvalidResponse() throws Exception {
        URI baseUri = startServer(exchange -> respond(exchange, 200, "not-json"));
        JdkPublicMarketDataOutboundClient client = client(baseUri, PublicMarketDataOutboundSettings.defaults());

        PublicMarketDataOutboundResult result = client.fetch(PublicMarketDataOutboundRequest.publicGet(
                "FAKE",
                PublicMarketDataEndpointCategory.TICKER,
                "/ticker"));
        PublicMarketDataQualitySummary summary = PublicMarketDataSourceHealthMapper.map(result);

        assertEquals(PublicMarketDataOutboundErrorCategory.INVALID_RESPONSE, result.errorCategory());
        assertEquals(PublicMarketDataQualitySummary.SourceHealth.ERROR, summary.sourceHealth());
        assertFalse(summary.tradingAuthorization());
        assertFalse(result.message().contains("not-json"));
    }

    @Test
    void deniedPrivateCategoryMustNotReachFakeServerOrRetry() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        URI baseUri = startServer(exchange -> {
            calls.incrementAndGet();
            respond(exchange, 200, "{\"rows\":[{}]}");
        });
        JdkPublicMarketDataOutboundClient client = client(baseUri, PublicMarketDataOutboundSettings.defaults());

        PublicMarketDataOutboundResult result = client.fetch(new PublicMarketDataOutboundRequest(
                "FAKE",
                PublicMarketDataEndpointCategory.BALANCE,
                "/account/balance",
                true,
                true,
                null,
                null,
                null));

        assertEquals(0, calls.get());
        assertEquals(0, result.attempts());
        assertEquals(PublicMarketDataOutboundErrorCategory.DENIED, result.errorCategory());
    }

    @Test
    void hostEscapingEndpointMustNotReachFakeServerOrRetry() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        URI baseUri = startServer(exchange -> {
            calls.incrementAndGet();
            respond(exchange, 200, "{\"rows\":[{}]}");
        });
        JdkPublicMarketDataOutboundClient client = client(baseUri, PublicMarketDataOutboundSettings.defaults());

        for (String endpointPath : new String[]{
                "//example.invalid/ticker?token=bad",
                "http://example.invalid/ticker",
                "https://example.invalid/ticker",
                "//user@example.invalid/ticker",
                "/ticker#frag"
        }) {
            PublicMarketDataOutboundRequest request = new PublicMarketDataOutboundRequest(
                    "FAKE",
                    PublicMarketDataEndpointCategory.TICKER,
                    endpointPath,
                    false,
                    false,
                    "trc-denied",
                    "req-denied",
                    "1m");

            PublicMarketDataOutboundResult result = client.fetch(request);
            String summary = PublicMarketDataLogSummary.from(request, result).toString();

            assertEquals(PublicMarketDataOutboundErrorCategory.DENIED, result.errorCategory());
            assertEquals(0, result.attempts());
            assertFalse(summary.toLowerCase().contains("token"));
            assertFalse(result.message().toLowerCase().contains("token"));
        }
        assertEquals(0, calls.get());
    }

    @Test
    void relativeEndpointShouldResolveWithinBaseHost() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<URI> seenUri = new AtomicReference<>();
        URI baseUri = startServer(exchange -> {
            calls.incrementAndGet();
            seenUri.set(exchange.getRequestURI());
            respond(exchange, 200, "{\"rows\":[{}]}");
        });
        JdkPublicMarketDataOutboundClient client = client(baseUri, PublicMarketDataOutboundSettings.defaults());

        PublicMarketDataOutboundResult result = client.fetch(PublicMarketDataOutboundRequest.publicGet(
                "FAKE",
                PublicMarketDataEndpointCategory.TICKER,
                "ticker?symbol=BTC-USDT"));

        assertEquals(1, calls.get());
        assertEquals(PublicMarketDataOutboundErrorCategory.NONE, result.errorCategory());
        assertEquals("/ticker", seenUri.get().getPath());
        assertEquals("symbol=BTC-USDT", seenUri.get().getQuery());
    }

    @Test
    void logSummaryShouldNotContainFullQueryStringOrCredentialLikeMaterial() throws Exception {
        URI baseUri = startServer(exchange -> respond(exchange, 200, "{\"token\":\"fake-token\",\"rows\":[{}]}"));
        JdkPublicMarketDataOutboundClient client = client(baseUri, PublicMarketDataOutboundSettings.defaults());
        PublicMarketDataOutboundRequest request = new PublicMarketDataOutboundRequest(
                "FAKE",
                PublicMarketDataEndpointCategory.OHLCV,
                "/klines?symbol=BTC-USDT&limit=1",
                false,
                false,
                "trc-redaction",
                "req-redaction",
                "1m");

        PublicMarketDataOutboundResult result = client.fetch(request);
        String summary = PublicMarketDataLogSummary.from(request, result).toString();

        assertFalse(summary.contains("symbol=BTC-USDT"));
        assertFalse(summary.contains("limit=1"));
        assertFalse(summary.toLowerCase().contains("token"));
        assertFalse(result.message().toLowerCase().contains("token"));
    }

    private JdkPublicMarketDataOutboundClient client(
            URI baseUri, PublicMarketDataOutboundSettings settings) {
        return new JdkPublicMarketDataOutboundClient(
                baseUri,
                new PublicMarketDataOutboundPolicy(FIXED_CLOCK),
                settings,
                HttpClient.newBuilder().connectTimeout(settings.connectTimeout()).build(),
                Clock.systemUTC(),
                backoff -> {
                    // 测试中不真实等待 backoff，避免 retry 验证变慢；生产构造器仍按配置 sleep。
                });
    }

    private URI startServer(ThrowingHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", exchange -> {
            try {
                handler.handle(exchange);
            } finally {
                exchange.close();
            }
        });
        server.start();
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }

    private static void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface ThrowingHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
