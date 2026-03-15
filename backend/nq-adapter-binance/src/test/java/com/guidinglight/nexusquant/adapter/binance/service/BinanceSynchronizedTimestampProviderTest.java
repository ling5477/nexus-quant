package com.guidinglight.nexusquant.adapter.binance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * BinanceSynchronizedTimestampProviderTest 验证签名时间会先按需校准 serverTime，再叠加人工偏移。
 */
class BinanceSynchronizedTimestampProviderTest {

    @Test
    void shouldSyncServerTimeAndCacheResolvedOffset() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        try (TestServer server = new TestServer(requestCount, 200, "{\"serverTime\":1700000002000}")) {
            BinanceSynchronizedTimestampProvider provider = new BinanceSynchronizedTimestampProvider(
                    HttpClient.newHttpClient(),
                    new ObjectMapper(),
                    server.baseUrl(),
                    java.time.Duration.ofSeconds(2),
                    Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC),
                    -500L,
                    30_000L
            );

            assertEquals(1_700_000_001_500L, provider.nowMillis());
            assertEquals(1_500L, provider.effectiveOffsetMillis());
            assertEquals(1, requestCount.get());

            assertEquals(1_700_000_001_500L, provider.nowMillis());
            assertEquals(1, requestCount.get());
        }
    }

    @Test
    void shouldFallBackToManualOffsetWhenServerTimeSyncFails() throws Exception {
        AtomicInteger requestCount = new AtomicInteger();
        try (TestServer server = new TestServer(requestCount, 500, "{}")) {
            BinanceSynchronizedTimestampProvider provider = new BinanceSynchronizedTimestampProvider(
                    HttpClient.newHttpClient(),
                    new ObjectMapper(),
                    server.baseUrl(),
                    java.time.Duration.ofSeconds(2),
                    Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC),
                    -800L,
                    30_000L
            );

            assertEquals(1_699_999_999_200L, provider.nowMillis());
            assertEquals(-800L, provider.effectiveOffsetMillis());
            assertTrue(requestCount.get() >= 1);
        }
    }

    private static final class TestServer implements AutoCloseable {

        private final HttpServer server;

        private TestServer(AtomicInteger requestCount, int status, String responseBody) throws IOException {
            this.server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/api/v3/time", exchange -> writeResponse(exchange, requestCount, status, responseBody));
            server.start();
        }

        private static void writeResponse(HttpExchange exchange, AtomicInteger requestCount, int status, String responseBody) throws IOException {
            requestCount.incrementAndGet();
            byte[] response = responseBody.getBytes();
            exchange.sendResponseHeaders(status, response.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(response);
            }
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
