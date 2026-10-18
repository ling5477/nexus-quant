package com.guidinglight.nexusquant.adapter.okx.marketdata;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.okx.http.OkxHttpClient;
import com.guidinglight.nexusquant.marketdata.domain.OrderBookSourceException;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** 只在供应方边界提供合成公开响应，证明协议、精度和未携带私有头，不mock NQ业务API。 */
class OkxPublicOrderBookAdapterTest {
    private static final Instant NOW = Instant.parse("2026-10-13T00:00:00Z");

    private static String body(String bids, String asks, long time) {
        return "{\"code\":\"0\",\"data\":[{\"ts\":\""
                + time
                + "\",\"bids\":"
                + bids
                + ",\"asks\":"
                + asks
                + "}]}";
    }

    private static class Fixture implements AutoCloseable {
        final HttpServer server;
        final HttpClient http;
        final OkxPublicOrderBookAdapter adapter;
        final AtomicBoolean noPrivateHeaders = new AtomicBoolean();
        final AtomicInteger requests = new AtomicInteger();

        Fixture(String payload) throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(
                    "/api/v5/market/books",
                    exchange -> {
                        requests.incrementAndGet();
                        noPrivateHeaders.set(
                                exchange.getRequestHeaders().keySet().stream()
                                        .noneMatch(
                                                key ->
                                                        key.toUpperCase().startsWith("OK-ACCESS-")
                                                                || key.equalsIgnoreCase(
                                                                        "Authorization")));
                        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(200, bytes.length);
                        try (var stream = exchange.getResponseBody()) {
                            stream.write(bytes);
                        } finally {
                            exchange.close();
                        }
                    });
            server.start();
            http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
            adapter =
                    new OkxPublicOrderBookAdapter(
                            new OkxHttpClient(
                                    http,
                                    new ObjectMapper(),
                                    "http://127.0.0.1:" + server.getAddress().getPort(),
                                    Duration.ofSeconds(2)),
                            Clock.fixed(NOW, ZoneOffset.UTC));
        }

        public void close() {
            server.stop(0);
            http.close();
        }
    }

    @Test
    void parsesExactCumulativeQuantityAndUsesOnlyPublicHeaders() throws Exception {
        try (var fixture =
                new Fixture(
                        body(
                                "[[\"100.123456789\",\"0.00000001\",\"0\",\"2\"],[\"100.1\",\"0.00000002\",\"0\",\"3\"]]",
                                "[[\"101\",\"0.00000004\",\"0\",\"4\"]]",
                                NOW.toEpochMilli()))) {
            var snapshot = fixture.adapter.fetch("BTC-USDT");
            assertEquals("100.123456789", snapshot.bids().getFirst().price());
            assertEquals("0.00000003", snapshot.bids().get(1).cumulativeQuantity());
            assertEquals(NOW, snapshot.sourceTime());
            assertEquals(NOW, snapshot.receivedAt());
            assertTrue(fixture.noPrivateHeaders.get());
            assertEquals(1, fixture.requests.get());
        }
    }

    @Test
    void rejectsCrossedBookMalformedOrderAndFutureSourceTime() throws Exception {
        for (String payload :
                new String[] {
                    body(
                            "[[\"102\",\"1\",\"0\",\"1\"]]",
                            "[[\"101\",\"1\",\"0\",\"1\"]]",
                            NOW.toEpochMilli()),
                    body(
                            "[[\"100\",\"1\",\"0\",\"1\"],[\"100\",\"2\",\"0\",\"1\"]]",
                            "[[\"101\",\"1\",\"0\",\"1\"]]",
                            NOW.toEpochMilli()),
                    body(
                            "[[\"100\",\"1\",\"0\",\"1\"]]",
                            "[[\"101\",\"1\",\"0\",\"1\"]]",
                            NOW.plusSeconds(6).toEpochMilli())
                }) {
            try (var fixture = new Fixture(payload)) {
                assertEquals(
                        "SOURCE_INVALID",
                        assertThrows(
                                        OrderBookSourceException.class,
                                        () -> fixture.adapter.fetch("BTC-USDT"))
                                .code());
            }
        }
    }

    @Test
    void oversizedResponseIsCancelledWithoutCreatingAUsableSnapshot() throws Exception {
        try (var fixture = new Fixture(" ".repeat(70000) + "{}")) {
            assertEquals(
                    "SOURCE_UNAVAILABLE",
                    assertThrows(
                                    OrderBookSourceException.class,
                                    () -> fixture.adapter.fetch("BTC-USDT"))
                            .code());
            assertTrue(fixture.noPrivateHeaders.get());
        }
    }

    @Test
    void injectedOrDerivativeSymbolNeverReachesHttp() throws Exception {
        try (var fixture = new Fixture("{}")) {
            for (String symbol :
                    new String[] {"BTC-USDT-SWAP", "BTC-USDT&sz=400", "BTC-USDT&signature=fixture"})
                assertEquals(
                        "SOURCE_REQUEST_DENIED",
                        assertThrows(
                                        OrderBookSourceException.class,
                                        () -> fixture.adapter.fetch(symbol))
                                .code());
            assertEquals(0, fixture.requests.get());
        }
    }

    @Test
    void requiresTheManualProfileAndBothPublicReadSwitches() {
        for (var scenario :
                List.of(
                        new String[] {"local", "true", "true"},
                        new String[] {"public-marketdata-manual", "true", "false"},
                        new String[] {"public-marketdata-manual", "false", "true"},
                        new String[] {"public-marketdata-manual", "true", "true"})) {
            try (var context = new AnnotationConfigApplicationContext()) {
                context.getBeanFactory().setConversionService(new ApplicationConversionService());
                context.getEnvironment().setActiveProfiles(scenario[0]);
                context.getEnvironment()
                        .getPropertySources()
                        .addFirst(
                                new MapPropertySource(
                                        "isolated-public-book",
                                        Map.of(
                                                "nq.public-marketdata.outbound.enabled",
                                                scenario[1],
                                                "nq.public-marketdata.order-book.enabled",
                                                scenario[2],
                                                "nq.public-marketdata.outbound.base-url",
                                                "http://127.0.0.1:0")));
                context.register(OkxPublicOrderBookAdapter.class);
                context.refresh();
                assertEquals(
                        "public-marketdata-manual".equals(scenario[0])
                                && "true".equals(scenario[1])
                                && "true".equals(scenario[2]),
                        context.getBeanNamesForType(OkxPublicOrderBookAdapter.class).length == 1);
            }
        }
    }
}
