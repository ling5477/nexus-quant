package com.guidinglight.nexusquant.adapter.okx.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderRequest;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 直接 adapter 调用也不能在持久化边界之后静默改值；只向本机 HTTP fixture 发送。 */
class OkxEffectiveExecutionContractTest {
    @Test void unnormalizedRequestMustNotSendAndExactValuesReachWire() throws Exception {
        var mapper = new ObjectMapper(); var posts = new AtomicInteger(); var wire = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 4);
        server.createContext("/", exchange -> {
            String response;
            if (exchange.getRequestMethod().equals("POST")) {
                posts.incrementAndGet(); wire.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                response = "{\"code\":\"0\",\"data\":[{\"sCode\":\"0\",\"ordId\":\"fixture-order\",\"clOrdId\":\"eq-client\"}]}";
            } else response = "{\"code\":\"0\",\"data\":[{\"instId\":\"BTC-USDT\",\"state\":\"live\",\"lotSz\":\"0.001\",\"tickSz\":\"0.01\",\"minSz\":\"0.001\"}]}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            var transport = new OkxHttpClient(client, mapper, "http://127.0.0.1:" + server.getAddress().getPort(), Duration.ofSeconds(5));
            var adapter = new OkxExchangeAdapter(new OkxExchangeAdapter.Dependencies(mapper, transport,
                    new OkxInstrumentsCache(transport, Clock.systemUTC(), Duration.ofHours(1)), Clock.systemUTC(), "SIM"));
            var original = request("10.0005", "100.005");
            var normalized = adapter.normalizeOrder(original);
            assertEquals(0, new BigDecimal("10").compareTo(normalized.quantity()));
            assertEquals(0, new BigDecimal("100").compareTo(normalized.price()));
            assertEquals(0, posts.get());
            var rejected = adapter.placeOrder(original);
            assertFalse(rejected.accepted()); assertEquals("OKX_EFFECTIVE_PARAMETERS_CHANGED", rejected.error().code());
            assertEquals(0, posts.get());
            assertTrue(adapter.placeOrder(request("10.00000000", "100.00000000")).accepted());
            assertEquals(1, posts.get());
            assertEquals("10.00000000", mapper.readTree(wire.get()).path("sz").asText());
            assertEquals("100.00000000", mapper.readTree(wire.get()).path("px").asText());
        } finally { server.stop(0); }
    }
    private static AdapterOrderRequest request(String quantity, String price) {
        return new AdapterOrderRequest("eq-request", "eq-order", 1L, "OKX", "BTC-USDT", "eq-client", "1:eq-client", "BUY", "LIMIT",
                new BigDecimal(price), new BigDecimal(quantity), null, "GTC", "strategy", "eq-run", "eq-trace");
    }
}
