package com.guidinglight.nexusquant.adapter.binance.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.binance.model.BinanceApiCredentials;
import com.guidinglight.nexusquant.adapter.binance.service.BinanceApiException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/**
 * BinanceListenKeyClientTest 覆盖 listenKey 创建、续期与错误路径。
 */
class BinanceListenKeyClientTest {

    @Test
    void shouldCreateAndRefreshListenKey() throws Exception {
        AtomicReference<HttpExchange> exchangeRef = new AtomicReference<>();
        try (TestServer server = new TestServer(exchangeRef)) {
            BinanceListenKeyClient client = new BinanceListenKeyClient(
                    HttpClient.newHttpClient(),
                    new ObjectMapper(),
                    server.baseUrl(),
                    Duration.ofSeconds(2),
                    new BinanceApiCredentials("test-api-key", "test-secret")
            );

            String listenKey = client.createListenKey("trc-create");

            assertEquals("listen-key-123", listenKey);
            assertEquals("POST", exchangeRef.get().getRequestMethod());
            assertEquals("/api/v3/userDataStream", exchangeRef.get().getRequestURI().toString());
            assertEquals("test-api-key", exchangeRef.get().getRequestHeaders().getFirst("X-MBX-APIKEY"));

            client.refreshListenKey(listenKey, "trc-refresh");

            assertEquals("PUT", exchangeRef.get().getRequestMethod());
            assertEquals("/api/v3/userDataStream?listenKey=listen-key-123", exchangeRef.get().getRequestURI().toString());
            assertEquals("test-api-key", exchangeRef.get().getRequestHeaders().getFirst("X-MBX-APIKEY"));
        }
    }

    @Test
    void shouldRaiseStructuredErrorWhenRefreshFails() throws Exception {
        AtomicReference<HttpExchange> exchangeRef = new AtomicReference<>();
        try (TestServer server = new TestServer(exchangeRef)) {
            BinanceListenKeyClient client = new BinanceListenKeyClient(
                    HttpClient.newHttpClient(),
                    new ObjectMapper(),
                    server.baseUrl(),
                    Duration.ofSeconds(2),
                    new BinanceApiCredentials("test-api-key", "test-secret")
            );
            server.failRefresh();

            BinanceApiException exception = assertThrows(
                    BinanceApiException.class,
                    () -> client.refreshListenKey("expired-listen-key", "trc-refresh-fail")
            );

            assertEquals("PUT", exchangeRef.get().getRequestMethod());
            assertEquals("-1125", exception.errorCode());
        }
    }

    private static final class TestServer implements AutoCloseable {

        private final HttpServer server;
        private volatile boolean refreshShouldFail;

        private TestServer(AtomicReference<HttpExchange> exchangeRef) throws IOException {
            this.server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/api/v3/userDataStream", exchange -> {
                exchangeRef.set(exchange);
                String responseBody;
                int statusCode = 200;
                if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    responseBody = "{\"listenKey\":\"listen-key-123\"}";
                } else if ("PUT".equalsIgnoreCase(exchange.getRequestMethod()) && refreshShouldFail) {
                    statusCode = 400;
                    responseBody = "{\"code\":-1125,\"msg\":\"This listenKey does not exist.\"}";
                } else {
                    responseBody = "{}";
                }
                byte[] response = responseBody.getBytes();
                exchange.sendResponseHeaders(statusCode, response.length);
                try (OutputStream outputStream = exchange.getResponseBody()) {
                    outputStream.write(response);
                }
            });
            server.start();
        }

        private String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private void failRefresh() {
            this.refreshShouldFail = true;
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
