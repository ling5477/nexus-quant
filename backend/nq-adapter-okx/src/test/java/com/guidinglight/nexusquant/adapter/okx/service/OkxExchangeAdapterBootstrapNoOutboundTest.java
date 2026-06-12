package com.guidinglight.nexusquant.adapter.okx.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.adapter.okx.model.OkxApiCredentials;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * OkxExchangeAdapterBootstrapNoOutboundTest 固定 OKX adapter 默认依赖创建的 no-outbound 约束。
 * <p>
 * Why:
 * Spring context 会创建默认 OKX adapter；如果默认依赖创建阶段刷新 instruments，local/full Maven test
 * 会在业务测试开始前访问 OKX public endpoint。这里用本地 fake endpoint 计数，证明构造期不发生 GET，
 * 且 production runtime 首次显式读取 instruments 时仍会刷新。
 */
class OkxExchangeAdapterBootstrapNoOutboundTest {

    @Test
    void shouldCreateDefaultDependenciesWithoutFetchingPublicInstruments() throws Exception {
        try (CountingInstrumentsServer server = new CountingInstrumentsServer()) {
            OkxRuntimeConfig runtimeConfig = new OkxRuntimeConfig(
                    "dome",
                    server.baseUrl(),
                    "ws://127.0.0.1/private",
                    Duration.ofSeconds(1),
                    Duration.ofMinutes(5),
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(5),
                    Duration.ofSeconds(20),
                    new OkxApiCredentials("", "", ""),
                    true
            );

            OkxExchangeAdapter.Dependencies dependencies = OkxExchangeAdapter.createDefaultDependencies(runtimeConfig);
            OkxExchangeAdapter adapter = new OkxExchangeAdapter(dependencies);

            assertEquals(0, server.hitCount());

            Map<String, com.guidinglight.nexusquant.adapter.okx.model.OkxInstrument> snapshot =
                    adapter.instrumentsCache().snapshot("trc-default-dependencies-first-read");

            assertEquals(1, server.hitCount());
            assertTrue(snapshot.containsKey("BTC-USDT"));
        }
    }

    private static final class CountingInstrumentsServer implements AutoCloseable {

        private final HttpServer server;
        private final AtomicInteger hitCount = new AtomicInteger();

        private CountingInstrumentsServer() throws IOException {
            this.server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/api/v5/public/instruments", exchange -> {
                hitCount.incrementAndGet();
                byte[] response = """
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

        private int hitCount() {
            return hitCount.get();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
