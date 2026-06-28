package com.guidinglight.nexusquant.adapter.okx.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOpenOrdersQuery;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderQuery;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderRequest;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderSnapshot;
import com.guidinglight.nexusquant.adapter.api.model.AdapterResultCategory;
import com.guidinglight.nexusquant.adapter.okx.model.OkxApiCredentials;
import com.guidinglight.nexusquant.adapter.okx.model.OkxInstrument;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * OkxExchangeAdapterRawBodySuppressionTest 固定 GateL-1B-C 的 order ack/snapshot 原始响应抑制边界。
 * <p>
 * Why:
 * P1-C 要保留 adapter-api 的 rawPayload 字段但停止 producer 传播 provider full body、签名、认证头或
 * credential-like 诊断文本；这里用本地 mock server 注入敏感形态字段，证明它们不会跨层进入 core-facing model。
 */
class OkxExchangeAdapterRawBodySuppressionTest {

    private static final Clock TEST_CLOCK = Clock.fixed(Instant.parse("2026-06-22T10:00:00Z"), ZoneOffset.UTC);
    private static final String TIMESTAMP = "2026-06-22T10:00:00Z";

    /**
     * 验证 OKX 下单 ack、单笔查询 snapshot、挂单列表 snapshot 均抑制 rawPayload。
     */
    @Test
    void shouldSuppressProviderRawBodyForAckAndSnapshots() throws Exception {
        try (TestServer server = new TestServer()) {
            OkxExchangeAdapter adapter = createAdapter(server.baseUrl());

            var ack = adapter.placeOrder(new AdapterOrderRequest(
                    "req-okx-raw-1",
                    "ord-okx-raw-1",
                    1001L,
                    "OKX",
                    "BTC-USDT",
                    "cid-okx-raw-1",
                    "1001:cid-okx-raw-1",
                    "BUY",
                    "LIMIT",
                    new BigDecimal("30000.12"),
                    new BigDecimal("0.123"),
                    null,
                    "GTC",
                    "strategy",
                    "run-1",
                    "trc-okx-raw-place"
            ));

            assertTrue(ack.accepted());
            assertEquals("889900", ack.externalOrderId());
            assertEquals(AdapterResultCategory.ACCEPTED, ack.resultCategory());
            assertNull(ack.rawPayload());

            AdapterOrderSnapshot snapshot = adapter.getOrder(new AdapterOrderQuery(
                    1001L,
                    "OKX",
                    "BTC-USDT",
                    "cid-okx-raw-1",
                    "889900",
                    "trc-okx-raw-get"
            ));

            assertEquals("ACCEPTED", snapshot.externalStatus());
            assertEquals(AdapterResultCategory.SUCCESS, snapshot.resultCategory());
            assertNull(snapshot.rawPayload());

            List<AdapterOrderSnapshot> openOrders = adapter.listOpenOrders(new AdapterOpenOrdersQuery(
                    1001L,
                    "OKX",
                    "BTC-USDT",
                    "trc-okx-raw-open"
            ));

            assertEquals(1, openOrders.size());
            assertEquals("cid-okx-raw-1", openOrders.getFirst().clientOrderId());
            assertNull(openOrders.getFirst().rawPayload());
        }
    }

    /**
     * 验证 OKX error snapshot 也不把异常诊断文本写入 rawPayload。
     */
    @Test
    void shouldSuppressProviderRawBodyForErrorSnapshot() throws Exception {
        try (TestServer server = new TestServer()) {
            OkxExchangeAdapter adapter = createAdapter(server.baseUrl());

            AdapterOrderSnapshot snapshot = adapter.getOrder(new AdapterOrderQuery(
                    1001L,
                    "OKX",
                    "BTC-USDT",
                    "force-error",
                    null,
                    "trc-okx-raw-error"
            ));

            assertEquals(AdapterResultCategory.THROTTLED, snapshot.resultCategory());
            assertNull(snapshot.rawPayload());
        }
    }

    private OkxExchangeAdapter createAdapter(String baseUrl) {
        ObjectMapper objectMapper = new ObjectMapper();
        OkxHttpClient authenticatedHttpClient = new OkxHttpClient(
                HttpClient.newHttpClient(),
                objectMapper,
                baseUrl,
                Duration.ofSeconds(2),
                new OkxRequestSigner(),
                () -> TIMESTAMP,
                new OkxApiCredentials("okx-test-key", "okx-test-secret", "okx-test-passphrase"),
                true
        );
        return new OkxExchangeAdapter(new OkxExchangeAdapter.Dependencies(
                objectMapper,
                authenticatedHttpClient,
                new StubOkxInstrumentsCache(),
                TEST_CLOCK,
                "SIM"
        ));
    }

    private static final class StubOkxInstrumentsCache extends OkxInstrumentsCache {

        private StubOkxInstrumentsCache() {
            super(dummyPublicClient(), TEST_CLOCK, Duration.ofMinutes(5));
        }

        @Override
        public OkxInstrument getRequired(String instId, String traceId) {
            return new OkxInstrument(
                    instId,
                    new BigDecimal("0.01"),
                    new BigDecimal("0.001"),
                    new BigDecimal("0.001"),
                    "live"
            );
        }

        private static OkxHttpClient dummyPublicClient() {
            return new OkxHttpClient(
                    HttpClient.newHttpClient(),
                    new ObjectMapper(),
                    "http://127.0.0.1:1",
                    Duration.ofSeconds(1),
                    new OkxRequestSigner(),
                    () -> TIMESTAMP,
                    OkxApiCredentials.unconfigured(),
                    false
            );
        }
    }

    private static final class TestServer implements AutoCloseable {

        private final HttpServer server;

        private TestServer() throws IOException {
            this.server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/", this::handle);
            server.start();
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        @Override
        public void close() {
            server.stop(0);
        }

        private void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String responseBody;
            int status = 200;
            if ("/api/v5/trade/order".equals(path) && "POST".equals(exchange.getRequestMethod())) {
                responseBody = "{\"code\":\"0\",\"data\":[{\"instId\":\"BTC-USDT\",\"clOrdId\":\"cid-okx-raw-1\",\"ordId\":\"889900\",\"sCode\":\"0\",\"state\":\"live\",\"apiKey\":\"PROVIDER_API_KEY_MARKER\",\"signature\":\"PROVIDER_SIGNATURE_MARKER\",\"responseBody\":\"PROVIDER_FULL_BODY_MARKER\"}]}";
            } else if ("/api/v5/trade/order".equals(path) && exchange.getRequestURI().getQuery().contains("force-error")) {
                status = 400;
                responseBody = "{\"code\":\"50011\",\"msg\":\"PROVIDER_SECRET_MARKER should stay inside provider body\",\"data\":[]}";
            } else if ("/api/v5/trade/order".equals(path)) {
                responseBody = orderSnapshotBody();
            } else if ("/api/v5/trade/orders-pending".equals(path)) {
                responseBody = orderSnapshotBody();
            } else {
                status = 404;
                responseBody = "{\"code\":\"404\",\"msg\":\"unexpected path\"}";
            }
            respond(exchange, status, responseBody);
        }

        private String orderSnapshotBody() {
            return "{\"code\":\"0\",\"data\":[{\"instId\":\"BTC-USDT\",\"clOrdId\":\"cid-okx-raw-1\",\"ordId\":\"889900\",\"state\":\"live\",\"px\":\"30000.12\",\"sz\":\"0.123\",\"accFillSz\":\"0\",\"avgPx\":\"0\",\"authHeader\":\"PROVIDER_AUTH_HEADER_MARKER\",\"setCookie\":\"PROVIDER_SET_COOKIE_MARKER\",\"responseBody\":\"PROVIDER_FULL_BODY_MARKER\"}]}";
        }

        private void respond(HttpExchange exchange, int status, String responseBody) throws IOException {
            byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, response.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(response);
            }
        }
    }
}
