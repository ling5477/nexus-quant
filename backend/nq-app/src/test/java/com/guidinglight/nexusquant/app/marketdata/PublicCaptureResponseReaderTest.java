package com.guidinglight.nexusquant.app.marketdata;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/** 本机协议源验证完整正文的期限和字节边界，不使用真实交易所或私有凭据。 */
@Timeout(10)
class PublicCaptureResponseReaderTest {
    @Test
    void cancelsSlowBodyEvenAfterHeadersHaveArrived() throws Exception {
        AtomicBoolean headers = new AtomicBoolean();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/",
                exchange -> {
                    exchange.sendResponseHeaders(200, 10);
                    try (var stream = exchange.getResponseBody()) {
                        stream.write('[');
                        stream.flush();
                        headers.set(true);
                        try {
                            Thread.sleep(700);
                        } catch (InterruptedException failure) {
                            Thread.currentThread().interrupt();
                        }
                        stream.write(new byte[9]);
                    }
                });
        server.start();
        try {
            assertThrows(
                    HttpTimeoutException.class,
                    () ->
                            PublicCaptureResponseReader.read(
                                    HttpClient.newHttpClient(),
                                    request(server),
                                    Duration.ofMillis(200)));
            assertTrue(headers.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void acceptsExactByteLimitAndRejectsNextByteForBothTransferModes() throws Exception {
        for (int length : new int[] {256000, 256001}) {
            for (boolean chunked : new boolean[] {false, true}) {
                byte[] bytes = new byte[length];
                HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                server.createContext(
                        "/",
                        exchange -> {
                            exchange.sendResponseHeaders(200, chunked ? 0 : bytes.length);
                            try (var stream = exchange.getResponseBody()) {
                                stream.write(bytes);
                            }
                        });
                server.start();
                try {
                    if (length == 256000) {
                        assertArrayEquals(
                                bytes,
                                PublicCaptureResponseReader.read(
                                                HttpClient.newHttpClient(),
                                                request(server),
                                                Duration.ofSeconds(2))
                                        .body());
                    } else {
                        assertEquals(
                                "PUBLIC_MARKET_RESPONSE_TOO_LARGE",
                                assertThrows(
                                                IllegalStateException.class,
                                                () ->
                                                        PublicCaptureResponseReader.read(
                                                                HttpClient.newHttpClient(),
                                                                request(server),
                                                                Duration.ofSeconds(2)))
                                        .getMessage());
                    }
                } finally {
                    server.stop(0);
                }
            }
        }
    }

    @Test
    void rejectsUnadvertisedCompressionRatherThanPassingEncodedBytesToParser() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/",
                exchange -> {
                    exchange.getResponseHeaders().set("Content-Encoding", "gzip");
                    exchange.sendResponseHeaders(200, 2);
                    try (var stream = exchange.getResponseBody()) {
                        stream.write(new byte[2]);
                    }
                });
        server.start();
        try {
            assertEquals(
                    "PUBLIC_MARKET_RESPONSE_ENCODING",
                    assertThrows(
                                    IllegalStateException.class,
                                    () ->
                                            PublicCaptureResponseReader.read(
                                                    HttpClient.newHttpClient(),
                                                    request(server),
                                                    Duration.ofSeconds(2)))
                            .getMessage());
        } finally {
            server.stop(0);
        }
    }

    private static HttpRequest request(HttpServer server) {
        return HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"))
                .GET()
                .build();
    }
}
