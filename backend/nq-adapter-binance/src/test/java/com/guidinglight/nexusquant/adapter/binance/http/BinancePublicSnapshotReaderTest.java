package com.guidinglight.nexusquant.adapter.binance.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.binance.error.BinanceApiException;
import com.guidinglight.nexusquant.adapter.binance.model.BinanceApiCredentials;
import com.guidinglight.nexusquant.adapter.binance.signing.BinanceRequestSigner;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;

/** 公开目录即使先返回响应头，也必须在完整正文的读取期限内结束，不能接受残缺规则。 */
@Timeout(10)
class BinancePublicSnapshotReaderTest {

    @Test
    void shouldTimeoutWhileReadingBodyAfterHeaders() throws Exception {
        AtomicBoolean headersSent = new AtomicBoolean();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/api/v3/exchangeInfo",
                exchange -> {
                    byte[] body = "{\"symbols\":[]}".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, body.length);
                    try (var output = exchange.getResponseBody()) {
                        output.write(body, 0, 1);
                        output.flush();
                        headersSent.set(true);
                        try {
                            Thread.sleep(700);
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        output.write(body, 1, body.length - 1);
                    }
                });
        server.start();
        try {
            BinanceHttpClient client = client(server, Duration.ofMillis(200));
            BinanceApiException failure =
                    assertThrows(
                            BinanceApiException.class,
                            () ->
                                    client.get(
                                            "/api/v3/exchangeInfo",
                                            Map.of(),
                                            false,
                                            "body-timeout"));
            assertEquals("HTTP_TIMEOUT", failure.errorCode());
            assertTrue(headersSent.get(), "必须覆盖已收到响应头后的慢正文");
            assertTrue(failure.getMessage().contains("/api/v3/exchangeInfo"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldReadCompleteGzipSnapshotWithoutCredentials() throws Exception {
        try (var fixture =
                new Fixture(
                        gzip(
                                "{\"symbols\":[{\"symbol\":\"BTCUSDT\"},{\"symbol\":\"ETHUSDT\"}]}"
                                        .getBytes(StandardCharsets.UTF_8)),
                        "gzip",
                        200,
                        false)) {
            var result =
                    client(fixture.server, Duration.ofSeconds(2))
                            .get(
                                    "/api/v3/exchangeInfo",
                                    Map.of("showPermissionSets", false),
                                    false,
                                    "gzip-catalogue");
            assertEquals(2, result.path("symbols").size());
            assertEquals("ETHUSDT", result.path("symbols").get(1).path("symbol").asText());
            assertEquals(
                    "showPermissionSets=false",
                    fixture.request.get().getRequestURI().getRawQuery());
            assertEquals(
                    "gzip", fixture.request.get().getRequestHeaders().getFirst("Accept-Encoding"));
            assertNull(fixture.request.get().getRequestHeaders().getFirst("X-MBX-APIKEY"));
            assertEquals(1, fixture.calls.get());
        }
    }

    @Test
    void shouldAcceptIdentityBodyAtWireLimit() throws Exception {
        byte[] body = new byte[8 * 1024 * 1024];
        Arrays.fill(body, (byte) ' ');
        body[0] = '{';
        body[1] = '}';
        try (var fixture = new Fixture(body, "identity", 200, false)) {
            assertTrue(
                    client(fixture.server, Duration.ofSeconds(4))
                            .get("/api/v3/exchangeInfo", Map.of(), false, "at-limit")
                            .isObject());
        }
    }

    @Test
    void shouldRejectBothDeclaredAndChunkedOversizedBodyWithoutRetry() throws Exception {
        byte[] body = new byte[8 * 1024 * 1024 + 1];
        Arrays.fill(body, (byte) ' ');
        for (boolean chunked : new boolean[] {false, true}) {
            try (var fixture = new Fixture(body, "identity", 200, chunked)) {
                BinanceApiException failure =
                        assertThrows(
                                BinanceApiException.class,
                                () ->
                                        client(fixture.server, Duration.ofSeconds(4))
                                                .get(
                                                        "/api/v3/exchangeInfo",
                                                        Map.of(),
                                                        false,
                                                        "wire-limit"));
                assertEquals("HTTP_CLIENT_ERROR", failure.errorCode());
                assertTrue(failure.errorMessage().contains("wire body exceeds limit"));
                assertEquals(1, fixture.calls.get());
            }
        }
    }

    @Test
    void shouldRejectGzipExpansionPastDecodedLimit() throws Exception {
        byte[] expanded = new byte[24 * 1024 * 1024 + 1];
        Arrays.fill(expanded, (byte) ' ');
        try (var fixture = new Fixture(gzip(expanded), "gzip", 200, false)) {
            BinanceApiException failure =
                    assertThrows(
                            BinanceApiException.class,
                            () ->
                                    client(fixture.server, Duration.ofSeconds(4))
                                            .get(
                                                    "/api/v3/exchangeInfo",
                                                    Map.of(),
                                                    false,
                                                    "decoded-limit"));
            assertEquals("HTTP_CLIENT_ERROR", failure.errorCode());
            assertTrue(failure.errorMessage().contains("decoded body exceeds limit"));
        }
    }

    @Test
    void shouldRejectTruncatedAndCorruptGzipWithoutReturningPartialJson() throws Exception {
        byte[] complete = gzip("{\"symbols\":[]}".getBytes(StandardCharsets.UTF_8));
        byte[] truncated = Arrays.copyOf(complete, complete.length - 4);
        byte[] corrupt = complete.clone();
        corrupt[corrupt.length - 8] ^= 1;
        for (byte[] invalid : new byte[][] {truncated, corrupt}) {
            try (var fixture = new Fixture(invalid, "gzip", 200, false)) {
                BinanceApiException failure =
                        assertThrows(
                                BinanceApiException.class,
                                () ->
                                        client(fixture.server, Duration.ofSeconds(2))
                                                .get(
                                                        "/api/v3/exchangeInfo",
                                                        Map.of(),
                                                        false,
                                                        "invalid-gzip"));
                assertEquals("HTTP_CLIENT_ERROR", failure.errorCode());
                assertEquals(1, fixture.calls.get());
            }
        }
    }

    @Test
    void shouldPreserveStructuredProviderErrorWithoutRetry() throws Exception {
        try (var fixture =
                new Fixture(
                        gzip(
                                "{\"code\":-1003,\"msg\":\"Too many requests\"}"
                                        .getBytes(StandardCharsets.UTF_8)),
                        "gzip",
                        429,
                        false)) {
            BinanceApiException failure =
                    assertThrows(
                            BinanceApiException.class,
                            () ->
                                    client(fixture.server, Duration.ofSeconds(2))
                                            .get(
                                                    "/api/v3/exchangeInfo",
                                                    Map.of(),
                                                    false,
                                                    "provider-limit"));
            assertEquals(429, failure.httpStatus());
            assertEquals("-1003", failure.errorCode());
            assertEquals("Too many requests", failure.errorMessage());
            assertEquals(1, fixture.calls.get());
        }
    }

    @Test
    void shouldRejectUnsupportedEncoding() throws Exception {
        try (var fixture = new Fixture("{}".getBytes(StandardCharsets.UTF_8), "br", 200, false)) {
            BinanceApiException failure =
                    assertThrows(
                            BinanceApiException.class,
                            () ->
                                    client(fixture.server, Duration.ofSeconds(2))
                                            .get(
                                                    "/api/v3/exchangeInfo",
                                                    Map.of(),
                                                    false,
                                                    "unsupported-encoding"));
            assertEquals("HTTP_CLIENT_ERROR", failure.errorCode());
            assertTrue(failure.errorMessage().contains("unsupported"));
        }
    }

    @Test
    void shouldLeaveSignedRequestHeadersAndSignatureTransportUnchanged() throws Exception {
        try (var fixture =
                new Fixture("{}".getBytes(StandardCharsets.UTF_8), "identity", 200, false)) {
            client(fixture.server, Duration.ofSeconds(2))
                    .get("/api/v3/exchangeInfo", Map.of(), true, "signed-fixture-only");
            assertNull(fixture.request.get().getRequestHeaders().getFirst("Accept-Encoding"));
            assertEquals(
                    "unused-test-key",
                    fixture.request.get().getRequestHeaders().getFirst("X-MBX-APIKEY"));
            assertTrue(fixture.request.get().getRequestURI().getRawQuery().contains("signature="));
            assertEquals(1, fixture.calls.get());
        }
    }

    private static byte[] gzip(byte[] input) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var output = new GZIPOutputStream(bytes)) {
            output.write(input);
        }
        return bytes.toByteArray();
    }

    @Test
    void shouldUseSameCatalogueTransportAfterExistingMethodNormalization() throws Exception {
        try (var fixture =
                new Fixture(gzip("{}".getBytes(StandardCharsets.UTF_8)), "gzip", 200, false)) {
            assertTrue(
                    client(fixture.server, Duration.ofSeconds(2))
                            .send(
                                    " get ",
                                    "/api/v3/exchangeInfo",
                                    Map.of(),
                                    false,
                                    "normalized-get")
                            .isObject());
            assertEquals("GET", fixture.request.get().getRequestMethod());
            assertEquals(
                    "gzip", fixture.request.get().getRequestHeaders().getFirst("Accept-Encoding"));
        }
    }

    /** 本机协议源只用于网络边界反例，不代表真实交易所行情或真实私有调用。 */
    private static final class Fixture implements AutoCloseable {
        private final HttpServer server;
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicReference<HttpExchange> request = new AtomicReference<>();

        private Fixture(byte[] body, String encoding, int status, boolean chunked)
                throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(
                    "/api/v3/exchangeInfo",
                    exchange -> {
                        calls.incrementAndGet();
                        request.set(exchange);
                        exchange.getResponseHeaders().set("Content-Encoding", encoding);
                        exchange.sendResponseHeaders(status, chunked ? 0 : body.length);
                        try (var output = exchange.getResponseBody()) {
                            output.write(body);
                        }
                    });
            server.start();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private static BinanceHttpClient client(HttpServer server, Duration timeout) {
        return new BinanceHttpClient(
                HttpClient.newHttpClient(),
                new ObjectMapper(),
                "http://127.0.0.1:" + server.getAddress().getPort(),
                timeout,
                new BinanceRequestSigner(),
                () -> 1_700_000_000_000L,
                new BinanceApiCredentials("unused-test-key", "unused-test-secret"));
    }
}
