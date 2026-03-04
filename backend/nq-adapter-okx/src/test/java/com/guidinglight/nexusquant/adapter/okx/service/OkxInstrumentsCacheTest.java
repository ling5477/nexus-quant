package com.guidinglight.nexusquant.adapter.okx.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * OkxInstrumentsCacheTest 验证 instruments 拉取与字段解析。
 */
class OkxInstrumentsCacheTest {

    /**
     * 验证可以解析 tickSz/lotSz/minSz/state 并缓存到 instId 索引。
     */
    @Test
    void shouldParseSpotInstrumentsIntoCache() throws Exception {
        try (TestServer server = new TestServer()) {
            OkxHttpClient publicClient = new OkxHttpClient(
                    HttpClient.newHttpClient(),
                    new ObjectMapper(),
                    server.baseUrl(),
                    Duration.ofSeconds(2),
                    new OkxRequestSigner(),
                    () -> "2026-03-04T00:00:00Z",
                    null,
                    false
            );
            OkxInstrumentsCache cache = new OkxInstrumentsCache(
                    publicClient,
                    Clock.fixed(Instant.parse("2026-03-04T00:00:00Z"), ZoneOffset.UTC),
                    Duration.ofMinutes(5)
            );

            Map<String, com.guidinglight.nexusquant.adapter.okx.model.OkxInstrument> snapshot = cache.snapshot("trc-cache");

            assertTrue(snapshot.containsKey("BTC-USDT"));
            assertEquals("0.10000000", snapshot.get("BTC-USDT").tickSize().setScale(8).toPlainString());
            assertEquals("0.00010000", snapshot.get("BTC-USDT").lotSize().setScale(8).toPlainString());
            assertEquals("0.00100000", snapshot.get("BTC-USDT").minSize().setScale(8).toPlainString());
            assertEquals("live", snapshot.get("BTC-USDT").state());
        }
    }

    private static final class TestServer implements AutoCloseable {

        private final HttpServer server;

        private TestServer() throws IOException {
            this.server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/api/v5/public/instruments", exchange -> {
                byte[] response = """
                        {
                          \"code\": \"0\",
                          \"data\": [
                            {
                              \"instId\": \"BTC-USDT\",
                              \"tickSz\": \"0.1\",
                              \"lotSz\": \"0.0001\",
                              \"minSz\": \"0.001\",
                              \"state\": \"live\"
                            }
                          ]
                        }
                        """.getBytes();
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream outputStream = exchange.getResponseBody()) {
                    outputStream.write(response);
                }
            });
            server.start();
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
