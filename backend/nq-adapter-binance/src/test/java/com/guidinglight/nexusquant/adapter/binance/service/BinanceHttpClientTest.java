package com.guidinglight.nexusquant.adapter.binance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.binance.model.BinanceApiCredentials;
import com.guidinglight.nexusquant.adapter.binance.model.BinanceKeyType;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/**
 * BinanceHttpClientTest 验证 Binance query 签名、方法路径与错误解析。
 */
class BinanceHttpClientTest {

    @Test
    void shouldAttachSignedQueryForGetRequest() throws Exception {
        AtomicReference<HttpExchange> exchangeRef = new AtomicReference<>();
        try (TestServer server = new TestServer(exchangeRef, 200, "{\"makerCommission\":15}")) {
            BinanceRequestSigner signer = new BinanceRequestSigner();
            BinanceApiCredentials credentials = new BinanceApiCredentials("test-api-key", "test-secret-key");
            BinanceHttpClient client = new BinanceHttpClient(
                    HttpClient.newHttpClient(),
                    new ObjectMapper(),
                    server.baseUrl(),
                    Duration.ofSeconds(2),
                    signer,
                    () -> 1_700_000_000_123L,
                    credentials
            );
            LinkedHashMap<String, Object> query = new LinkedHashMap<>();
            query.put("symbol", "BTCUSDT");

            client.get("/api/v3/order", query, true, "trc-binance-get");

            HttpExchange exchange = exchangeRef.get();
            String expectedUnsigned = "symbol=BTCUSDT&timestamp=1700000000123&recvWindow=5000";
            String expectedSignature = signer.sign(expectedUnsigned, credentials);
            assertEquals("GET", exchange.getRequestMethod());
            assertEquals(
                    "/api/v3/order?symbol=BTCUSDT&timestamp=1700000000123&recvWindow=5000&signature=" + expectedSignature,
                    exchange.getRequestURI().toString()
            );
            assertEquals("test-api-key", exchange.getRequestHeaders().getFirst("X-MBX-APIKEY"));
        }
    }

    @Test
    void shouldAttachSignedQueryForPostAndDeleteRequests() throws Exception {
        AtomicReference<HttpExchange> exchangeRef = new AtomicReference<>();
        try (TestServer server = new TestServer(exchangeRef, 200, "{\"symbol\":\"BTCUSDT\"}")) {
            BinanceRequestSigner signer = new BinanceRequestSigner();
            BinanceApiCredentials credentials = new BinanceApiCredentials("test-api-key", "test-secret-key");
            BinanceHttpClient client = new BinanceHttpClient(
                    HttpClient.newHttpClient(),
                    new ObjectMapper(),
                    server.baseUrl(),
                    Duration.ofSeconds(2),
                    signer,
                    () -> 1_700_000_000_456L,
                    credentials
            );
            LinkedHashMap<String, Object> query = new LinkedHashMap<>();
            query.put("symbol", "BTCUSDT");
            query.put("side", "BUY");

            client.post("/api/v3/order", query, true, "trc-binance-post");
            String expectedUnsigned = "symbol=BTCUSDT&side=BUY&timestamp=1700000000456&recvWindow=5000";
            String expectedSignature = signer.sign(expectedUnsigned, credentials);
            assertEquals("POST", exchangeRef.get().getRequestMethod());
            assertEquals(
                    "/api/v3/order?symbol=BTCUSDT&side=BUY&timestamp=1700000000456&recvWindow=5000&signature=" + expectedSignature,
                    exchangeRef.get().getRequestURI().toString()
            );

            client.delete("/api/v3/order", Map.of("symbol", "BTCUSDT"), true, "trc-binance-delete");
            String expectedDeleteUnsigned = "symbol=BTCUSDT&timestamp=1700000000456&recvWindow=5000";
            String expectedDeleteSignature = signer.sign(expectedDeleteUnsigned, credentials);
            assertEquals("DELETE", exchangeRef.get().getRequestMethod());
            assertEquals(
                    "/api/v3/order?symbol=BTCUSDT&timestamp=1700000000456&recvWindow=5000&signature=" + expectedDeleteSignature,
                    exchangeRef.get().getRequestURI().toString()
            );
        }
    }

    @Test
    void shouldParseStructuredErrorResponse() throws Exception {
        try (TestServer server = new TestServer(
                new AtomicReference<>(),
                400,
                "{\"code\":-1022,\"msg\":\"Signature for this request is not valid.\"}"
        )) {
            BinanceHttpClient client = new BinanceHttpClient(
                    HttpClient.newHttpClient(),
                    new ObjectMapper(),
                    server.baseUrl(),
                    Duration.ofSeconds(2),
                    new BinanceRequestSigner(),
                    () -> 1_700_000_000_789L,
                    new BinanceApiCredentials("test-api-key", "test-secret-key")
            );

            BinanceApiException exception = assertThrows(
                    BinanceApiException.class,
                    () -> client.get("/api/v3/account", Map.of(), true, "trc-binance-error")
            );

            assertEquals(400, exception.httpStatus());
            assertEquals("-1022", exception.errorCode());
            assertEquals("Signature for this request is not valid.", exception.errorMessage());
            assertTrue(exception.getMessage().contains("endpoint=/api/v3/account"));
        }
    }

    @Test
    void shouldAttachUrlEncodedEd25519Signature() throws Exception {
        AtomicReference<HttpExchange> exchangeRef = new AtomicReference<>();
        KeyPair keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path privateKeyFile = Files.createTempFile("binance-ed25519", ".pem");
        try {
            Files.writeString(privateKeyFile, toPem(keyPair));
            BinanceRequestSigner signer = new BinanceRequestSigner();
            BinanceApiCredentials credentials = new BinanceApiCredentials(
                    "test-api-key",
                    "",
                    BinanceKeyType.ED25519,
                    "",
                    privateKeyFile.toString()
            );
            try (TestServer server = new TestServer(exchangeRef, 200, "{\"makerCommission\":15}")) {
                BinanceHttpClient client = new BinanceHttpClient(
                        HttpClient.newHttpClient(),
                        new ObjectMapper(),
                        server.baseUrl(),
                        Duration.ofSeconds(2),
                        signer,
                        () -> 1_700_000_000_123L,
                        credentials
                );
                LinkedHashMap<String, Object> query = new LinkedHashMap<>();
                query.put("symbol", "BTCUSDT");

                client.get("/api/v3/order", query, true, "trc-binance-ed25519");

                HttpExchange exchange = exchangeRef.get();
                String expectedUnsigned = "symbol=BTCUSDT&timestamp=1700000000123&recvWindow=5000";
                String expectedSignature = URLEncoder
                        .encode(signer.sign(expectedUnsigned, credentials), StandardCharsets.UTF_8)
                        .replace("+", "%20");
                assertEquals("GET", exchange.getRequestMethod());
                assertEquals(
                        "/api/v3/order?symbol=BTCUSDT&timestamp=1700000000123&recvWindow=5000&signature=" + expectedSignature,
                        exchange.getRequestURI().toString()
                );
                assertEquals("test-api-key", exchange.getRequestHeaders().getFirst("X-MBX-APIKEY"));
            }
        } finally {
            Files.deleteIfExists(privateKeyFile);
        }
    }

    @Test
    void shouldWrapInvalidEd25519ConfigAsStructuredException() throws Exception {
        try (TestServer server = new TestServer(new AtomicReference<>(), 200, "{\"makerCommission\":15}")) {
            BinanceHttpClient client = new BinanceHttpClient(
                    HttpClient.newHttpClient(),
                    new ObjectMapper(),
                    server.baseUrl(),
                    Duration.ofSeconds(2),
                    new BinanceRequestSigner(),
                    () -> 1_700_000_000_123L,
                    new BinanceApiCredentials("test-api-key", "", BinanceKeyType.ED25519, "not-a-pem", null)
            );

            BinanceApiException exception = assertThrows(
                    BinanceApiException.class,
                    () -> client.get("/api/v3/order", Map.of("symbol", "BTCUSDT"), true, "trc-binance-invalid-ed25519")
            );

            assertEquals("BINANCE_SIGNER_CONFIG_INVALID", exception.errorCode());
            assertTrue(exception.errorMessage().contains("failed to sign Binance request with Ed25519")
                    || exception.errorMessage().contains("Illegal base64 character")
                    || exception.errorMessage().contains("empty"));
        }
    }

    private String toPem(KeyPair keyPair) {
        return "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                .encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";
    }

    private static final class TestServer implements AutoCloseable {

        private final HttpServer server;

        private TestServer(AtomicReference<HttpExchange> exchangeRef, int status, String responseBody) throws IOException {
            this.server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/", exchange -> {
                exchangeRef.set(exchange);
                byte[] response = responseBody.getBytes();
                exchange.sendResponseHeaders(status, response.length);
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
