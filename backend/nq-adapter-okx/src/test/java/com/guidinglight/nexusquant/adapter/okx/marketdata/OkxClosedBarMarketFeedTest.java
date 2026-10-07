package com.guidinglight.nexusquant.adapter.okx.marketdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class OkxClosedBarMarketFeedTest {
    @Test
    void springDefaultOriginRemainsOfficialAndDoesNotMakeRequests() throws Exception {
        try (var context = context(Map.of(), Map.of(), "public-marketdata-manual", "true")) {
            var feed = context.getBean(OkxClosedBarMarketFeed.class);
            assertEquals(URI.create("https://www.okx.com"), field(feed, "origin"));
            assertEquals(Duration.ofSeconds(3), ((HttpClient) field(feed, "http")).connectTimeout().orElseThrow());
        }
        assertEquals(URI.create("https://www.okx.com"), field(new OkxClosedBarMarketFeed(), "origin"));
    }

    @Test
    void invalidOriginsFailBeforeAnyObservationAndDoNotExposeInput() {
        for (String value : List.of("ftp://example.com", "http://public-host.example",
                "https://user:password@example.com", "https://example.com/api",
                "https://example.com?secret=value", "https://example.com#secret",
                "https:///missing-host", "not an origin", "", "https://example.com:0",
                "https://example.com:65536", "https://example.com:",
                "http://localhost.example.com", "http://127.0.0.1.example.com")) {
            var failure = assertThrows(IllegalArgumentException.class, () -> new OkxClosedBarMarketFeed(value));
            assertEquals("PUBLIC_MARKETDATA_ORIGIN_INVALID", failure.getMessage());
            assertEquals(null, failure.getCause());
        }
        assertThrows(RuntimeException.class, () -> {
            try (var ignored = context(Map.of("nq.public-marketdata.outbound.base-url", "http://public-host.example"),
                    Map.of(), "public-marketdata-manual", "true")) { }
        });
    }

    @Test
    void acceptsHttpsProviderOriginsWithoutRelaxingHttpLoopbackRule() throws Exception {
        for (String value : List.of("https://gateway.example.com", "https://gateway.example.com/",
                "https://gateway.example.com:8443", "http://127.0.0.1:32123", "http://localhost:32124/")) {
            assertEquals(URI.create(value), field(new OkxClosedBarMarketFeed(value), "origin"));
        }
    }

    @Test
    void springCapabilityStillRequiresManualProfileAndExplicitEnable() {
        for (String profile : List.of("default", "local", "test", "ci", "paper", "prod", "public-marketdata-manual")) {
            for (String enabled : List.of("false", "invalid", "true")) {
                try (var context = context(Map.of(), Map.of(), profile, enabled)) {
                    assertEquals("public-marketdata-manual".equals(profile) && "true".equals(enabled),
                            !context.getBeansOfType(OkxClosedBarMarketFeed.class).isEmpty());
                }
            }
        }
        try (var context = context(Map.of(), Map.of(), "public-marketdata-manual", null)) {
            assertTrue(context.getBeansOfType(OkxClosedBarMarketFeed.class).isEmpty());
        }
    }

    @Test
    void formalSpringPropertyAndEnvironmentBindingObtainFreshObservation() throws Exception {
        Instant boundary = Instant.now().truncatedTo(ChronoUnit.HOURS);
        Instant first = boundary.minus(Duration.ofHours(2));
        Instant serverTime = boundary.plusSeconds(60);
        List<String> paths = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().toString();
            paths.add(path);
            calls.incrementAndGet();
            String body;
            if (path.startsWith("/api/v5/public/time")) {
                body = "{\"code\":\"0\",\"data\":[{\"ts\":\"" + serverTime.toEpochMilli() + "\"}]}";
            } else if (path.startsWith("/api/v5/public/instruments")) {
                body = """
                        {"code":"0","data":[{"instId":"BTC-USDT","instType":"SPOT",
                        "state":"live","baseCcy":"BTC","quoteCcy":"USDT","tickSz":"0.1",
                        "lotSz":"0.0001","minSz":"0.0001"}]}
                        """;
            } else if (path.startsWith("/api/v5/market/ticker")) {
                body = "{\"code\":\"0\",\"data\":[{\"instId\":\"BTC-USDT\",\"last\":\"105\",\"ts\":\""
                        + serverTime.plusSeconds(1).toEpochMilli() + "\"}]}";
            } else {
                body = "{\"code\":\"0\",\"data\":[" + candle(first.plusSeconds(3600), "1")
                        + "," + candle(first, "1") + "]}";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        try {
            for (String host : List.of("127.0.0.1", "localhost")) {
                String origin = "http://" + host + ":" + server.getAddress().getPort() + "/";
                // 保留正式组件构造及条件装配，同时验证 property 与完整环境变量名两条启动来源。
                Map<String, Object> properties = "127.0.0.1".equals(host)
                        ? Map.of("nq.public-marketdata.outbound.base-url", origin) : Map.of();
                Map<String, Object> environment = "localhost".equals(host)
                        ? Map.of("NQ_PUBLIC_MARKETDATA_OUTBOUND_BASE_URL", origin) : Map.of();
                int before = calls.get();
                paths.clear();
                try (var context = context(properties, environment, "public-marketdata-manual", "true")) {
                    assertEquals(before, calls.get(), "组件构造不得发起请求");
                    var observation = context.getBean(OkxClosedBarMarketFeed.class).observe(first, 24);
                    assertEquals(List.of("/api/v5/public/time",
                            "/api/v5/public/instruments?instType=SPOT&instId=BTC-USDT",
                            "/api/v5/market/ticker?instId=BTC-USDT",
                            "/api/v5/market/history-candles?instId=BTC-USDT&bar=1H&after="
                                    + boundary.toEpochMilli() + "&before=" + (first.toEpochMilli() - 1) + "&limit=24"), paths);
                    assertEquals(2, observation.bars().size());
                    assertEquals(first, observation.bars().getFirst().openTime());
                    assertEquals(first.plusSeconds(3600), observation.bars().getLast().openTime());
                    var latest = observation.bars().getLast();
                    assertEquals(serverTime, latest.availableAt());
                    assertFalse(latest.availableAt().isBefore(latest.closeTime()));
                    assertFalse(latest.availableAt().isAfter(latest.closeTime().plus(Duration.ofMinutes(15))));
                    assertEquals(serverTime, observation.rule().observedAt());
                    assertTrue(Duration.between(observation.rule().observedAt(), observation.serverTime()).abs()
                            .compareTo(Duration.ofMinutes(5)) <= 0);
                    assertTrue(observation.quote().observedAt().isAfter(latest.availableAt()));
                    assertTrue(observation.quote().observedAt().isAfter(latest.closeTime()));
                    assertFalse(observation.rule().observedAt().isAfter(latest.closeTime().plus(Duration.ofMinutes(15))));
                    assertFalse(observation.quote().observedAt().isAfter(latest.closeTime().plus(Duration.ofMinutes(15))));
                    assertFalse(observation.quote().observedAt().isBefore(serverTime.minus(Duration.ofMinutes(2))));
                    assertFalse(observation.quote().observedAt().isAfter(serverTime.plusSeconds(5)));
                    assertEquals(4, calls.get() - before);
                    System.out.println("FRESH_OBSERVATION_PROOF close=" + latest.closeTime()
                            + " availableAt=" + latest.availableAt() + " serverTime=" + observation.serverTime()
                            + " ruleObservedAt=" + observation.rule().observedAt()
                            + " quoteObservedAt=" + observation.quote().observedAt());
                }
            }
        } finally {
            server.stop(0);
        }
    }

    private static AnnotationConfigApplicationContext context(Map<String, Object> properties,
            Map<String, Object> environment, String profile, String enabled) {
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles(profile);
        context.getEnvironment().getPropertySources().addFirst(new SystemEnvironmentPropertySource("fixture-env", environment));
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("origin", properties));
        if (enabled != null) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("capability",
                    Map.of("nq.public-marketdata.outbound.enabled", enabled)));
        }
        context.register(OkxClosedBarMarketFeed.class);
        try {
            context.refresh();
            return context;
        } catch (RuntimeException failure) {
            context.close();
            throw failure;
        }
    }

    private static Object field(OkxClosedBarMarketFeed feed, String name) throws Exception {
        Field field = OkxClosedBarMarketFeed.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(feed);
    }

    @Test
    void consumesOnlyConfirmedClosedBarsAndUsesFixedPublicEndpoints() throws Exception {
        Instant first = Instant.parse("2026-09-25T08:00:00Z");
        Instant serverTime = first.plusSeconds(2 * 3600 + 60);
        List<String> paths = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().toString();
            paths.add(path);
            String body;
            if (path.startsWith("/api/v5/public/time")) {
                body = "{\"code\":\"0\",\"data\":[{\"ts\":\"" + serverTime.toEpochMilli() + "\"}]}";
            } else if (path.startsWith("/api/v5/public/instruments")) {
                body = """
                        {"code":"0","data":[{"instId":"BTC-USDT","instType":"SPOT",
                        "state":"live","baseCcy":"BTC","quoteCcy":"USDT","tickSz":"0.1",
                        "lotSz":"0.0001","minSz":"0.0001"}]}
                        """;
            } else if (path.startsWith("/api/v5/market/ticker")) {
                body = "{\"code\":\"0\",\"data\":[{\"instId\":\"BTC-USDT\",\"last\":\"105\",\"ts\":\""
                        + serverTime.plusSeconds(1).toEpochMilli() + "\"}]}";
            } else if (path.startsWith("/api/v5/market/history-candles")) {
                body = "{\"code\":\"0\",\"data\":["
                        + candle(first, "1") + "," + candle(first.plusSeconds(3600), "1")
                        + "," + candle(first.plusSeconds(7200), "0") + "]}";
            } else throw new IllegalStateException("unexpected public path");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        try {
            var feed = new OkxClosedBarMarketFeed(HttpClient.newHttpClient(),
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                    new ObjectMapper());
            var observed = feed.observe(first, 24);
            assertEquals(2, observed.bars().size());
            assertEquals(first, observed.bars().getFirst().openTime());
            assertEquals(first.plusSeconds(3600), observed.bars().getLast().openTime());
            assertEquals(serverTime, observed.bars().getLast().availableAt());
            assertEquals(serverTime.plusSeconds(1), observed.quote().observedAt());
            assertEquals(4, paths.size());
            assertTrue(paths.getLast().contains("after=" + first.plusSeconds(7200).toEpochMilli()));
            assertTrue(paths.stream().allMatch(path -> path.startsWith("/api/v5/public/")
                    || path.startsWith("/api/v5/market/")));
        } finally {
            server.stop(0);
        }
    }

    private static String candle(Instant open, String confirmed) {
        return "[\"" + open.toEpochMilli()
                + "\",\"100\",\"105\",\"99\",\"104\",\"1\",\"1\",\"104\",\""
                + confirmed + "\"]";
    }
}
