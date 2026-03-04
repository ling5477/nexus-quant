package com.guidinglight.nexusquant.adapter.okx.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.okx.model.OkxApiCredentials;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/**
 * OkxHttpClientTest 验证 OKX 鉴权头是否覆盖 GET query 与 POST body。
 */
class OkxHttpClientTest {

    /**
     * 验证 GET 带 query 时，签名头会基于完整 path+query 生成。
     */
    @Test
    void shouldAttachSignedHeadersForGetRequestWithQuery() throws Exception {
        AtomicReference<HttpExchange> exchangeRef = new AtomicReference<>();
        try (TestServer server = new TestServer(exchangeRef)) {
            OkxRequestSigner signer = new OkxRequestSigner();
            OkxApiCredentials credentials = new OkxApiCredentials("api-key", "secret-key", "passphrase");
            String timestamp = "2026-03-04T00:00:00Z";
            OkxHttpClient client = new OkxHttpClient(
                    HttpClient.newHttpClient(),
                    new ObjectMapper(),
                    server.baseUrl(),
                    Duration.ofSeconds(2),
                    signer,
                    () -> timestamp,
                    credentials,
                    true
            );
            String endpoint = "/api/v5/account/balance?ccy=USDT";

            client.get(endpoint, "trc-okx-get");

            HttpExchange exchange = exchangeRef.get();
            Map<String, String> expectedHeaders = signer.signHeaders(credentials, "GET", endpoint, "", timestamp);
            assertEquals(endpoint, exchange.getRequestURI().toString());
            assertEquals(expectedHeaders.get("OK-ACCESS-KEY"), exchange.getRequestHeaders().getFirst("OK-ACCESS-KEY"));
            assertEquals(expectedHeaders.get("OK-ACCESS-SIGN"), exchange.getRequestHeaders().getFirst("OK-ACCESS-SIGN"));
            assertEquals(expectedHeaders.get("OK-ACCESS-TIMESTAMP"), exchange.getRequestHeaders().getFirst("OK-ACCESS-TIMESTAMP"));
            assertEquals(expectedHeaders.get("OK-ACCESS-PASSPHRASE"), exchange.getRequestHeaders().getFirst("OK-ACCESS-PASSPHRASE"));
        }
    }

    /**
     * 验证 POST 带 body 时，签名头会把请求体纳入签名。
     */
    @Test
    void shouldAttachSignedHeadersForPostRequestWithBody() throws Exception {
        AtomicReference<HttpExchange> exchangeRef = new AtomicReference<>();
        try (TestServer server = new TestServer(exchangeRef)) {
            OkxRequestSigner signer = new OkxRequestSigner();
            OkxApiCredentials credentials = new OkxApiCredentials("api-key", "secret-key", "passphrase");
            String timestamp = "2026-03-04T00:00:00Z";
            OkxHttpClient client = new OkxHttpClient(
                    HttpClient.newHttpClient(),
                    new ObjectMapper(),
                    server.baseUrl(),
                    Duration.ofSeconds(2),
                    signer,
                    () -> timestamp,
                    credentials,
                    true
            );
            String endpoint = "/api/v5/trade/order";
            String body = "{\"instId\":\"BTC-USDT\",\"sz\":\"0.01\"}";

            client.post(endpoint, body, "trc-okx-post");

            HttpExchange exchange = exchangeRef.get();
            Map<String, String> expectedHeaders = signer.signHeaders(credentials, "POST", endpoint, body, timestamp);
            assertEquals(endpoint, exchange.getRequestURI().toString());
            assertEquals(expectedHeaders.get("OK-ACCESS-SIGN"), exchange.getRequestHeaders().getFirst("OK-ACCESS-SIGN"));
            assertEquals(expectedHeaders.get("OK-ACCESS-TIMESTAMP"), exchange.getRequestHeaders().getFirst("OK-ACCESS-TIMESTAMP"));
        }
    }

    private static final class TestServer implements AutoCloseable {

        private final HttpServer server;

        private TestServer(AtomicReference<HttpExchange> exchangeRef) throws IOException {
            this.server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/", exchange -> {
                exchangeRef.set(exchange);
                byte[] response = "{\"code\":\"0\",\"data\":[]}".getBytes();
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
