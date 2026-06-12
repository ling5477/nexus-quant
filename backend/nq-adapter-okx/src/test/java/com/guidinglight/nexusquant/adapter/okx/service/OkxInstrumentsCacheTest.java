package com.guidinglight.nexusquant.adapter.okx.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.okx.model.OkxApiCredentials;
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
import java.util.concurrent.atomic.AtomicInteger;

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

    /**
     * 构造 cache 只允许建立本地状态；首次显式读取 instruments 时才允许访问 public endpoint。
     */
    @Test
    void shouldNotFetchDuringConstructionAndRefreshOnFirstSnapshot() {
        CountingOkxHttpClient publicClient = new CountingOkxHttpClient();
        OkxInstrumentsCache cache = new OkxInstrumentsCache(
                publicClient,
                Clock.fixed(Instant.parse("2026-03-04T00:00:00Z"), ZoneOffset.UTC),
                Duration.ofMinutes(5)
        );

        assertEquals(0, publicClient.getCount());

        Map<String, com.guidinglight.nexusquant.adapter.okx.model.OkxInstrument> snapshot = cache.snapshot("trc-first-read");

        assertEquals(1, publicClient.getCount());
        assertTrue(snapshot.containsKey("BTC-USDT"));
    }

    /**
     * 验证当返回 preopen 或缺失精度字段时，缓存会跳过异常条目而不是阻断启动。
     */
    @Test
    void shouldSkipPreopenInstrumentWithoutPrecisionFields() throws Exception {
        try (TestServer server = new TestServer("""
                {
                  "code": "0",
                  "data": [
                    {
                      "instId": "ROBO-USDT",
                      "tickSz": "",
                      "lotSz": "",
                      "minSz": "",
                      "state": "preopen"
                    },
                    {
                      "instId": "BTC-USDT",
                      "tickSz": "0.1",
                      "lotSz": "0.0001",
                      "minSz": "0.001",
                      "state": "live"
                    }
                  ]
                }
                """)) {
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
            assertFalse(snapshot.containsKey("ROBO-USDT"));
        }
    }

    private static final class TestServer implements AutoCloseable {

        private final HttpServer server;
        private final String responseBody;

        private TestServer() throws IOException {
            this("""
                    {
                      "code": "0",
                      "data": [
                        {
                          "instId": "BTC-USDT",
                          "tickSz": "0.1",
                          "lotSz": "0.0001",
                          "minSz": "0.001",
                          "state": "live"
                        }
                      ]
                    }
                    """);
        }

        private TestServer(String responseBody) throws IOException {
            this.server = HttpServer.create(new InetSocketAddress(0), 0);
            this.responseBody = responseBody;
            server.createContext("/api/v5/public/instruments", exchange -> {
                byte[] response = this.responseBody.getBytes();
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

    private static final class CountingOkxHttpClient extends OkxHttpClient {

        private final ObjectMapper objectMapper = new ObjectMapper();
        private final AtomicInteger getCount = new AtomicInteger();

        private CountingOkxHttpClient() {
            super(
                    HttpClient.newHttpClient(),
                    new ObjectMapper(),
                    "http://127.0.0.1",
                    Duration.ofSeconds(1),
                    new OkxRequestSigner(),
                    () -> "2026-03-04T00:00:00Z",
                    new OkxApiCredentials("", "", ""),
                    false
            );
        }

        @Override
        public JsonNode get(String requestPathWithQuery, String traceId) {
            getCount.incrementAndGet();
            var root = objectMapper.createObjectNode();
            root.put("code", "0");
            var data = root.putArray("data");
            var btcUsdt = data.addObject();
            btcUsdt.put("instId", "BTC-USDT");
            btcUsdt.put("tickSz", "0.1");
            btcUsdt.put("lotSz", "0.0001");
            btcUsdt.put("minSz", "0.001");
            btcUsdt.put("state", "live");
            return root;
        }

        private int getCount() {
            return getCount.get();
        }
    }
}
