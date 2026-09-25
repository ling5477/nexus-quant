package com.guidinglight.nexusquant.app.marketdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import java.net.URI;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PublicMarketReplayCaptureServiceTest {
    private static final Instant START = Instant.parse("2026-09-20T00:00:00Z");
    private static final Instant END = Instant.parse("2026-09-23T00:00:00Z");
    private static final Instant OBSERVED = Instant.parse("2026-09-25T12:22:36Z");
    private final ObjectMapper mapper = new ObjectMapper();
    private final PublicMarketReplayCaptureService service = new PublicMarketReplayCaptureService(
            HttpClient.newHttpClient(), URI.create("http://127.0.0.1:1"), mapper,
            mock(JdbcTemplate.class), mock(TransactionTemplate.class),
            Clock.fixed(OBSERVED, ZoneOffset.UTC));

    @Test
    void manualProfileWiresCaptureApiWithExplicitOutboundFlag() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.setEnvironment(new MockEnvironment()
                    .withProperty("nq.public-marketdata.outbound.enabled", "true"));
            context.getEnvironment().setActiveProfiles("public-marketdata-manual");
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.registerBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class));
            context.registerBean(TransactionTemplate.class, () -> mock(TransactionTemplate.class));
            context.register(PublicMarketReplayCaptureService.class,
                    PublicMarketReplayCaptureController.class);
            context.refresh();
            assertTrue(context.containsBeanDefinition("publicMarketReplayCaptureService"));
            assertTrue(context.containsBeanDefinition("publicMarketReplayCaptureController"));
        }
    }

    @Test
    void capturedPublicResponseHasStableRawNormalizedAndConsumedIdentity() throws Exception {
        byte[] raw = raw();
        var first = service.parse(raw, START, END, OBSERVED);
        var replay = service.parse(raw, START, END, OBSERVED);
        assertEquals("100ed4ea22667255c3f0be4ec5ff68b92a60fa0e851bb5f83cb7336f778c21af",
                first.rawSha256());
        assertEquals(first.normalizedSha256(), replay.normalizedSha256());
        assertEquals(first.consumed().sha256(), replay.consumed().sha256());
        ArrayNode bars = (ArrayNode) mapper.readTree(first.consumed().canonicalJson());
        assertEquals(72, bars.size());
        assertEquals("2026-09-20T01:00:00Z", bars.get(0).path("availableAt").asText());
        assertEquals("2026-09-20T00:59:59.999Z", bars.get(0).path("closeTime").asText());
        assertTrue(OBSERVED.isAfter(Instant.parse(bars.get(0).path("availableAt").asText())));

        ObjectNode revised = (ObjectNode) mapper.readTree(raw);
        ((ArrayNode) revised.path("data").get(0)).set(4, mapper.getNodeFactory().textNode("86209.2"));
        var laterCapture = service.parse(revised.toString().getBytes(StandardCharsets.UTF_8),
                START, END, OBSERVED);
        assertNotEquals(first.rawSha256(), laterCapture.rawSha256());
        assertNotEquals(first.normalizedSha256(), laterCapture.normalizedSha256());
        assertNotEquals(first.consumed().sha256(), laterCapture.consumed().sha256());
    }

    @Test
    void malformedHistoricalWindowsFailClosed() throws Exception {
        ObjectNode gap = (ObjectNode) mapper.readTree(raw());
        ((ArrayNode) gap.path("data")).remove(0);
        assertThrows(IllegalStateException.class, () -> parse(gap));

        ObjectNode duplicate = (ObjectNode) mapper.readTree(raw());
        ArrayNode duplicated = (ArrayNode) duplicate.path("data");
        duplicated.set(1, duplicated.get(0).deepCopy());
        assertThrows(IllegalStateException.class, () -> parse(duplicate));

        ObjectNode outOfOrder = (ObjectNode) mapper.readTree(raw());
        ArrayNode unordered = (ArrayNode) outOfOrder.path("data");
        var first = unordered.get(0).deepCopy();
        unordered.set(0, unordered.get(1));
        unordered.set(1, first);
        assertThrows(IllegalStateException.class, () -> parse(outOfOrder));

        ObjectNode unclosed = (ObjectNode) mapper.readTree(raw());
        ((ArrayNode) unclosed.path("data").get(0)).set(8, mapper.getNodeFactory().textNode("0"));
        assertThrows(IllegalStateException.class, () -> parse(unclosed));

        ObjectNode invalidOhlc = (ObjectNode) mapper.readTree(raw());
        ((ArrayNode) invalidOhlc.path("data").get(0)).set(2, mapper.getNodeFactory().textNode("1"));
        assertThrows(IllegalStateException.class, () -> parse(invalidOhlc));
        assertThrows(IllegalArgumentException.class,
                () -> service.validateWindow(START, START.plusSeconds(101 * 3600L)));
    }

    @Test
    void missingUnavailableAndStalePublicRulesRejectBeforePersistence() throws Exception {
        ObjectNode missing = (ObjectNode) mapper.readTree(ruleRaw());
        ((ArrayNode) missing.path("data")).removeAll();
        assertThrows(IllegalStateException.class, () -> captureWithRule(
                missing.toString().getBytes(StandardCharsets.UTF_8), Clock.fixed(OBSERVED, ZoneOffset.UTC)));

        ObjectNode unavailable = (ObjectNode) mapper.readTree(ruleRaw());
        ((ObjectNode) unavailable.path("data").get(0)).put("state", "suspend");
        assertEquals("PUBLIC_RULE_NOT_TRADABLE", assertThrows(IllegalStateException.class,
                () -> captureWithRule(unavailable.toString().getBytes(StandardCharsets.UTF_8),
                        Clock.fixed(OBSERVED, ZoneOffset.UTC))).getMessage());

        AtomicInteger calls = new AtomicInteger();
        Clock stale = new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() {
                return calls.incrementAndGet() == 4 ? OBSERVED.minusSeconds(600) : OBSERVED;
            }
        };
        assertEquals("PUBLIC_RULE_STALE", assertThrows(IllegalStateException.class,
                () -> captureWithRule(ruleRaw(), stale)).getMessage());
    }

    private void captureWithRule(byte[] rule, Clock clock) throws Exception {
        byte[] candles = raw();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v5/market/history-candles", exchange -> {
            exchange.sendResponseHeaders(200, candles.length);
            try (var output = exchange.getResponseBody()) { output.write(candles); }
        });
        server.createContext("/api/v5/public/instruments", exchange -> {
            exchange.sendResponseHeaders(200, rule.length);
            try (var output = exchange.getResponseBody()) { output.write(rule); }
        });
        server.start();
        try {
            var bounded = new PublicMarketReplayCaptureService(HttpClient.newHttpClient(),
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()), mapper,
                    mock(JdbcTemplate.class), mock(TransactionTemplate.class), clock);
            bounded.capture(START, END, "test");
        } finally {
            server.stop(0);
        }
    }

    private byte[] ruleRaw() throws Exception {
        try (var stream = getClass().getResourceAsStream(
                "/public-market-replay/okx-btc-usdt-public-instrument-20260925.json")) {
            return stream.readAllBytes();
        }
    }

    private PublicMarketReplayCaptureService.ParsedCapture parse(ObjectNode payload) {
        return service.parse(payload.toString().getBytes(StandardCharsets.UTF_8), START, END, OBSERVED);
    }

    private byte[] raw() throws Exception {
        try (var stream = getClass().getResourceAsStream(
                "/public-market-replay/okx-btc-usdt-1h-20260920-20260923.json")) {
            return stream.readAllBytes();
        }
    }
}
