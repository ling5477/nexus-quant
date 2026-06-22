package com.guidinglight.nexusquant.adapter.binance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.api.model.AdapterCancelRequest;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOpenOrdersQuery;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderQuery;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderRequest;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderSnapshot;
import com.guidinglight.nexusquant.adapter.binance.model.BinanceApiCredentials;
import com.guidinglight.nexusquant.adapter.binance.model.BinanceSymbolFilters;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/**
 * BinanceExchangeAdapterTest 覆盖 GateC-2 PR-C12 的无 key 阶段请求组装与拒单路径。
 * <p>
 * Why:
 * TradingAdapter 是 Binance 闭环的第一层交易所方言封装点，
 * 如果这里的 symbol/clientOrderId/orderId/query-confirm 参数组装错了，后续 reconcile 与 event_store 都没有意义。
 */
class BinanceExchangeAdapterTest {

    private static final BinanceApiCredentials TEST_CREDENTIALS = new BinanceApiCredentials("binance-test-key", "binance-test-secret");
    private static final BinanceRequestSigner TEST_SIGNER = new BinanceRequestSigner();
    private static final Clock TEST_CLOCK = Clock.fixed(Instant.parse("2026-03-06T10:00:00Z"), ZoneOffset.UTC);
    private static final long FIXED_TIMESTAMP = 1_700_000_000_123L;

    /**
     * 验证 placeOrder 会使用 filters trim 后的价格/数量，并把 Binance 签名参数按 query 形式发送。
     */
    @Test
    void shouldPlaceOrderWithTrimmedSignedParams() throws Exception {
        AtomicReference<RecordedExchange> exchangeRef = new AtomicReference<>();
        try (TestServer server = new TestServer(exchangeRef, 200, "{\"symbol\":\"BTCUSDT\",\"orderId\":\"889900\",\"clientOrderId\":\"cid-binance-1\",\"status\":\"NEW\",\"apiKey\":\"PROVIDER_API_KEY_MARKER\",\"signature\":\"PROVIDER_SIGNATURE_MARKER\",\"responseBody\":\"PROVIDER_FULL_BODY_MARKER\"}")) {
            BinanceExchangeAdapter adapter = createAdapter(server.baseUrl());

            AdapterOrderRequest request = new AdapterOrderRequest(
                    "req-binance-1",
                    "ord-binance-1",
                    2001L,
                    "BINANCE",
                    "BTC-USDT",
                    "cid-binance-1",
                    "2001:cid-binance-1",
                    "BUY",
                    "LIMIT",
                    new BigDecimal("30000.129"),
                    new BigDecimal("0.123456"),
                    null,
                    "GTC",
                    "strategy",
                    "run-1",
                    "trc-binance-place-1"
            );

            var ack = adapter.placeOrder(request);

            assertTrue(ack.accepted());
            assertEquals("BINANCE", ack.exchangeCode());
            assertEquals("889900", ack.externalOrderId());
            assertEquals(com.guidinglight.nexusquant.adapter.api.model.AdapterResultCategory.ACCEPTED, ack.resultCategory());
            assertNull(ack.rawPayload());
            RecordedExchange exchange = exchangeRef.get();
            assertEquals("POST", exchange.method());
            assertEquals("/api/v3/order", exchange.path());
            String expectedUnsigned = "symbol=BTCUSDT&side=BUY&type=LIMIT&newClientOrderId=cid-binance-1&quantity=0.123&timeInForce=GTC&price=30000.12&timestamp=1700000000123&recvWindow=5000";
            String expectedSignature = TEST_SIGNER.sign(expectedUnsigned, TEST_CREDENTIALS);
            assertEquals(
                    "/api/v3/order?" + expectedUnsigned + "&signature=" + expectedSignature,
                    exchange.uri()
            );
            assertEquals("binance-test-key", exchange.apiKey());
        }
    }

    /**
     * 验证 cancelOrder/getOrder/listOpenOrders 会优先复用 Binance 的 orderId/clientOrderId 语义，而不是让 core 感知路径差异。
     */
    @Test
    void shouldCancelAndQueryOrdersUsingBinanceIdentifiers() throws Exception {
        AtomicReference<RecordedExchange> cancelExchange = new AtomicReference<>();
        try (TestServer server = new TestServer(cancelExchange, 200, "{\"symbol\":\"BTCUSDT\",\"orderId\":\"889900\",\"status\":\"CANCELED\"}")) {
            BinanceExchangeAdapter adapter = createAdapter(server.baseUrl());
            adapter.cancelOrder(new AdapterCancelRequest(
                    "req-binance-cancel-1",
                    "ord-binance-2",
                    2001L,
                    "BINANCE",
                    "BTC-USDT",
                    "cid-binance-2",
                    "889900",
                    "cancel_request",
                    "trc-binance-cancel-1"
            ));
            assertEquals("DELETE", cancelExchange.get().method());
            assertTrue(cancelExchange.get().uri().contains("orderId=889900"));
            assertFalse(cancelExchange.get().uri().contains("origClientOrderId="));
        }

        AtomicReference<RecordedExchange> getExchange = new AtomicReference<>();
        try (TestServer server = new TestServer(getExchange, 200, "{\"symbol\":\"BTCUSDT\",\"orderId\":\"889900\",\"clientOrderId\":\"cid-binance-2\",\"status\":\"NEW\",\"secret\":\"PROVIDER_SECRET_MARKER\",\"authHeader\":\"PROVIDER_AUTH_HEADER_MARKER\"}")) {
            BinanceExchangeAdapter adapter = createAdapter(server.baseUrl());
            AdapterOrderSnapshot snapshot = adapter.getOrder(new AdapterOrderQuery(
                    2001L,
                    "BINANCE",
                    "BTC-USDT",
                    "cid-binance-2",
                    "889900",
                    "trc-binance-get-1"
            ));
            assertEquals("ACCEPTED", snapshot.externalStatus());
            assertEquals(com.guidinglight.nexusquant.adapter.api.model.AdapterResultCategory.SUCCESS, snapshot.resultCategory());
            assertEquals("BTC-USDT", snapshot.symbol());
            assertNull(snapshot.rawPayload());
            assertTrue(getExchange.get().uri().contains("orderId=889900"));
        }

        AtomicReference<RecordedExchange> listExchange = new AtomicReference<>();
        try (TestServer server = new TestServer(listExchange, 200, "[{\"symbol\":\"BTCUSDT\",\"orderId\":\"889900\",\"clientOrderId\":\"cid-binance-2\",\"status\":\"NEW\",\"setCookie\":\"PROVIDER_SET_COOKIE_MARKER\",\"responseBody\":\"PROVIDER_FULL_BODY_MARKER\"}]")) {
            BinanceExchangeAdapter adapter = createAdapter(server.baseUrl());
            List<AdapterOrderSnapshot> snapshots = adapter.listOpenOrders(new AdapterOpenOrdersQuery(
                    2001L,
                    "BINANCE",
                    null,
                    "trc-binance-open-1"
            ));
            assertEquals(1, snapshots.size());
            assertEquals("BTC-USDT", snapshots.getFirst().symbol());
            assertNull(snapshots.getFirst().rawPayload());
            assertEquals("/api/v3/openOrders?timestamp=1700000000123&recvWindow=5000&signature="
                    + TEST_SIGNER.sign("timestamp=1700000000123&recvWindow=5000", TEST_CREDENTIALS), listExchange.get().uri());
        }
    }

    /**
     * 验证 Binance 结构化错误会被转换成统一的 reject_code/reject_reason，而不是把异常直接抛回 core。
     */
    @Test
    void shouldReturnStructuredRejectWhenExchangeRejectsPlaceOrder() throws Exception {
        AtomicReference<RecordedExchange> exchangeRef = new AtomicReference<>();
        try (TestServer server = new TestServer(
                exchangeRef,
                400,
                "{\"code\":-2010,\"msg\":\"Account has insufficient balance for requested action.\"}"
        )) {
            BinanceExchangeAdapter adapter = createAdapter(server.baseUrl());
            AdapterOrderRequest request = new AdapterOrderRequest(
                    "req-binance-3",
                    "ord-binance-3",
                    2001L,
                    "BINANCE",
                    "BTC-USDT",
                    "cid-binance-3",
                    "2001:cid-binance-3",
                    "BUY",
                    "LIMIT",
                    new BigDecimal("30000.12"),
                    new BigDecimal("0.123"),
                    null,
                    "GTC",
                    "strategy",
                    "run-1",
                    "trc-binance-place-2"
            );

            var ack = adapter.placeOrder(request);

            assertFalse(ack.accepted());
            assertNotNull(ack.error());
            assertEquals("-2010", ack.error().code());
            assertEquals(com.guidinglight.nexusquant.adapter.api.model.AdapterResultCategory.FATAL_FAILURE, ack.resultCategory());
            assertTrue(ack.error().message().contains("insufficient balance"));
            assertEquals("POST", exchangeRef.get().method());
        }
    }

    /**
     * 验证 trim 失败时 adapter 直接返回结构化拒单，不会错误访问外部网络。
     */
    @Test
    void shouldRejectBeforeNetworkWhenTrimFails() {
        BinanceExchangeAdapter adapter = createAdapter("http://127.0.0.1:65535");
        AdapterOrderRequest request = new AdapterOrderRequest(
                "req-binance-4",
                "ord-binance-4",
                2001L,
                "BINANCE",
                "BTC-USDT",
                "cid-binance-4",
                "2001:cid-binance-4",
                "BUY",
                "LIMIT",
                new BigDecimal("100.00"),
                new BigDecimal("0.000001"),
                null,
                "GTC",
                "strategy",
                "run-1",
                "trc-binance-place-3"
        );

        var ack = adapter.placeOrder(request);

        assertFalse(ack.accepted());
        assertNotNull(ack.error());
        assertEquals("BINANCE_QTY_BELOW_MIN_QTY", ack.error().code());
    }

    private BinanceExchangeAdapter createAdapter(String baseUrl) {
        BinanceHttpClient httpClient = new BinanceHttpClient(
                HttpClient.newHttpClient(),
                new ObjectMapper(),
                baseUrl,
                Duration.ofSeconds(2),
                TEST_SIGNER,
                () -> FIXED_TIMESTAMP,
                TEST_CREDENTIALS
        );
        BinanceFiltersCache filtersCache = new BinanceFiltersCache(stubExchangeInfoClient(baseUrl), TEST_CLOCK, Duration.ofMinutes(5));
        return new BinanceExchangeAdapter(new BinanceExchangeAdapter.Dependencies(
                httpClient,
                filtersCache,
                new BinanceOrderTrimmer(),
                TEST_CLOCK
        ));
    }

    private BinanceExchangeInfoClient stubExchangeInfoClient(String baseUrl) {
        BinanceHttpClient dummyHttpClient = new BinanceHttpClient(
                HttpClient.newHttpClient(),
                new ObjectMapper(),
                baseUrl,
                Duration.ofSeconds(2),
                TEST_SIGNER,
                () -> FIXED_TIMESTAMP,
                TEST_CREDENTIALS
        );
        BinanceSymbolFilters filters = new BinanceSymbolFilters(
                "BTCUSDT",
                "BTC-USDT",
                "TRADING",
                new BigDecimal("0.01"),
                new BigDecimal("0.01"),
                new BigDecimal("1000000"),
                new BigDecimal("0.001"),
                new BigDecimal("0.001"),
                new BigDecimal("1000"),
                new BigDecimal("0.001"),
                new BigDecimal("0.001"),
                new BigDecimal("1000"),
                new BigDecimal("10"),
                null,
                true,
                false
        );
        return new BinanceExchangeInfoClient(dummyHttpClient) {
            @Override
            public Map<String, BinanceSymbolFilters> fetchSpotExchangeInfo(String traceId) {
                return Map.of("BTCUSDT", filters);
            }
        };
    }

    private record RecordedExchange(String method, String path, String uri, String apiKey) {
    }

    private static final class TestServer implements AutoCloseable {

        private final HttpServer server;

        private TestServer(AtomicReference<RecordedExchange> exchangeRef, int status, String responseBody) throws IOException {
            this.server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/", exchange -> {
                exchangeRef.set(new RecordedExchange(
                        exchange.getRequestMethod(),
                        exchange.getRequestURI().getPath(),
                        exchange.getRequestURI().toString(),
                        exchange.getRequestHeaders().getFirst("X-MBX-APIKEY")
                ));
                respond(exchange, status, responseBody);
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

        private void respond(HttpExchange exchange, int status, String responseBody) throws IOException {
            byte[] response = responseBody.getBytes();
            exchange.sendResponseHeaders(status, response.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(response);
            }
        }
    }
}
