package com.guidinglight.nexusquant.adapter.okx.marketdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class OkxClosedBarMarketFeedTest {
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
