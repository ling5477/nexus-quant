package com.guidinglight.nexusquant.app.marketdata;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

class ResearchPublicRulesTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void fetchesEthSourceAndRejectsBtcOrParameterSubstitution() throws Exception {
        JsonNodeSource fixture = fixture();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var query = new AtomicReference<String>();
        server.createContext("/api/v5/public/instruments", exchange -> {
            query.set(exchange.getRequestURI().getQuery());
            byte[] raw = fixture.raw().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, raw.length);
            try (var body = exchange.getResponseBody()) { body.write(raw); }
        });
        server.start();
        try {
            var source = new PublicMarketReplayCaptureService(HttpClient.newHttpClient(),
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()), mapper,
                    org.mockito.Mockito.mock(JdbcTemplate.class),
                    org.mockito.Mockito.mock(TransactionTemplate.class),
                    Clock.fixed(Instant.parse("2026-10-26T00:00:00Z"), ZoneOffset.UTC));
            var snapshot = source.researchRules("ETH-USDT");
            assertEquals("instType=SPOT&instId=ETH-USDT", query.get());
            ObjectNode dataset = mapper.createObjectNode();
            dataset.putObject("frozenInput");
            dataset.put("symbol", "ETH-USDT"); dataset.put("exchangeCode", "OKX"); dataset.put("marketType", "SPOT");
            ObjectNode spec = mapper.createObjectNode();
            spec.put("researchRuleSnapshotJson", snapshot.toString());
            spec.put("rulePolicy", "CURRENTLY_OBSERVED_PUBLIC_RULES");
            spec.put("priceTick", "0.01"); spec.put("quantityStep", "0.0001"); spec.put("minimumQuantity", "0.001");
            assertDoesNotThrow(() -> ResearchPublicRules.validate(spec, dataset, mapper));
            spec.put("quantityStep", "0.00000001");
            assertThrows(IllegalArgumentException.class, () -> ResearchPublicRules.validate(spec, dataset, mapper));
            dataset.put("symbol", "BTC-USDT");
            assertThrows(IllegalArgumentException.class, () -> ResearchPublicRules.validate(spec, dataset, mapper));
        } finally { server.stop(0); }
    }

    private JsonNodeSource fixture() throws Exception {
        try (var stream = getClass().getResourceAsStream("/public-market-replay/okx-btc-usdt-public-instrument-20260925.json")) {
            ObjectNode value = (ObjectNode) mapper.readTree(stream);
            ObjectNode fact = (ObjectNode) value.path("data").get(0);
            // 不同标的与步长作为反替换测试输入，不作为真实公开来源证据。
            fact.put("instId", "ETH-USDT"); fact.put("baseCcy", "ETH");
            fact.put("tickSz", "0.01"); fact.put("lotSz", "0.0001"); fact.put("minSz", "0.001");
            return new JsonNodeSource(value.toString());
        }
    }

    private record JsonNodeSource(String raw) {}
}
