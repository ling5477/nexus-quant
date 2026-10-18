package com.guidinglight.nexusquant.adapter.okx.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.okx.error.OkxApiException;
import com.guidinglight.nexusquant.adapter.okx.http.OkxHttpClient;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** 用明确屏障停住供应方正文，证明截止时间和取消释放，不依赖概率性的慢网路。 */
class OkxBoundedResponseDeadlineTest {
    private static final class Fixture implements AutoCloseable {
        final CountDownLatch headersSent = new CountDownLatch(1);
        final CountDownLatch releaseBody = new CountDownLatch(1);
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        final ExecutorService handlers = Executors.newCachedThreadPool();
        final HttpClient http =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        final OkxHttpClient client;

        Fixture(boolean partialBody) throws Exception {
            server.setExecutor(handlers);
            server.createContext(
                    "/stalled",
                    exchange -> {
                        try {
                            byte[] payload = "{\"code\":\"0\"}".getBytes(StandardCharsets.UTF_8);
                            exchange.sendResponseHeaders(200, payload.length);
                            if (partialBody) {
                                exchange.getResponseBody().write(payload, 0, 1);
                                exchange.getResponseBody().flush();
                            }
                            headersSent.countDown();
                            if (!releaseBody.await(8, TimeUnit.SECONDS)) {
                                throw new IOException("fixture body release timed out");
                            }
                            exchange.getResponseBody()
                                    .write(
                                            payload,
                                            partialBody ? 1 : 0,
                                            payload.length - (partialBody ? 1 : 0));
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                        } finally {
                            exchange.close();
                        }
                    });
            server.createContext(
                    "/ok",
                    exchange -> {
                        byte[] payload = "{\"code\":\"0\"}".getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(200, payload.length);
                        try (var body = exchange.getResponseBody()) {
                            body.write(payload);
                        } finally {
                            exchange.close();
                        }
                    });
            server.start();
            client =
                    new OkxHttpClient(
                            http,
                            new ObjectMapper(),
                            "http://127.0.0.1:" + server.getAddress().getPort(),
                            Duration.ofSeconds(1));
        }

        @Override
        public void close() {
            releaseBody.countDown();
            server.stop(0);
            handlers.shutdownNow();
            http.shutdownNow();
        }
    }

    @Test
    void headersWithoutBodyEndAtDeadlineAndReleaseHttpRequest() throws Exception {
        verifyDeadline(false);
    }

    @Test
    void partialBodyCannotKeepRequestAliveAndNextRequestStillWorks() throws Exception {
        verifyDeadline(true);
    }

    private void verifyDeadline(boolean partialBody) throws Exception {
        try (var fixture = new Fixture(partialBody);
                var caller = Executors.newSingleThreadExecutor()) {
            var result =
                    caller.submit(
                            () ->
                                    assertThrows(
                                            OkxApiException.class,
                                            () ->
                                                    fixture.client.getBounded(
                                                            "/stalled", "body-deadline", 65536)));
            assertTrue(fixture.headersSent.await(2, TimeUnit.SECONDS));
            OkxApiException failure = result.get(3, TimeUnit.SECONDS);
            assertEquals("HTTP_TIMEOUT", failure.errorCode());
            assertEquals(1, fixture.releaseBody.getCount(), "供应方仍未放行正文，必须由客户端自行终止");
            assertEquals(
                    "0",
                    fixture.client.getBounded("/ok", "after-timeout", 65536).path("code").asText());
            fixture.http.shutdown();
            assertTrue(
                    fixture.http.awaitTermination(Duration.ofSeconds(2)), "取消后HTTP请求必须收敛，不能只释放调用方");
        }
    }

    @Test
    void interruptedCallerCancelsBodyAndPreservesInterruptFlag() throws Exception {
        try (var fixture = new Fixture(true)) {
            var finished = new CountDownLatch(1);
            var flag = new AtomicBoolean();
            var failureCode = new AtomicReference<String>();
            Thread caller =
                    new Thread(
                            () -> {
                                try {
                                    fixture.client.getBounded("/stalled", "interrupted", 65536);
                                } catch (OkxApiException failure) {
                                    failureCode.set(failure.errorCode());
                                    flag.set(Thread.currentThread().isInterrupted());
                                } finally {
                                    finished.countDown();
                                }
                            });
            caller.start();
            try {
                assertTrue(fixture.headersSent.await(2, TimeUnit.SECONDS));
                caller.interrupt();
                assertTrue(finished.await(2, TimeUnit.SECONDS));
                assertTrue(flag.get());
                assertEquals("HTTP_CLIENT_ERROR", failureCode.get());
                fixture.http.shutdown();
                assertTrue(fixture.http.awaitTermination(Duration.ofSeconds(2)));
            } finally {
                caller.interrupt();
                caller.join(3000);
            }
        }
    }
}
