package com.guidinglight.nexusquant.adapter.okx.marketdata;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.api.model.HistoricalKlineRequest;
import com.guidinglight.nexusquant.adapter.okx.http.OkxHttpClient;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/** 用供应方排他游标协议验证NQ包含起始整点，不靠数据库中的旧行情填补缺失。 */
class OkxHistoricalKlineRangeTest {
    @Test
    void exclusiveBeforeCursorIncludesRequestedFirstBarWithoutPrivateHeaders() throws Exception {
        var start = Instant.parse("2026-10-01T00:00:00Z");
        var mapper = new ObjectMapper();
        var privateHeadersAbsent = new AtomicBoolean(false);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/api/v5/market/history-candles",
                exchange -> {
                    Map<String, String> query =
                            Arrays.stream(exchange.getRequestURI().getRawQuery().split("&"))
                                    .map(part -> part.split("=", 2))
                                    .collect(Collectors.toMap(part -> part[0], part -> part[1]));
                    long before = Long.parseLong(query.get("before")),
                            after = Long.parseLong(query.get("after"));
                    privateHeadersAbsent.set(
                            exchange.getRequestHeaders().keySet().stream()
                                    .noneMatch(
                                            key ->
                                                    key.toUpperCase().startsWith("OK-ACCESS-")
                                                            || key.equalsIgnoreCase(
                                                                    "Authorization")));
                    var data = mapper.createArrayNode();
                    for (int index = 7; index >= 0; index--) {
                        long at = start.plusSeconds(index * 3600L).toEpochMilli();
                        // OKX的before返回严格晚于该游标的数据，after返回严格早于游标的数据。
                        if (at > before && at < after) {
                            var row = data.addArray();
                            row.add(Long.toString(at));
                            for (String value :
                                    new String[] {"100", "110", "90", "105", "1", "1", "105", "1"})
                                row.add(value);
                        }
                    }
                    byte[] body =
                            mapper.createObjectNode()
                                    .put("code", "0")
                                    .set("data", data)
                                    .toString()
                                    .getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    try (var stream = exchange.getResponseBody()) {
                        stream.write(body);
                    } finally {
                        exchange.close();
                    }
                });
        server.start();
        try (var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            var adapter =
                    new OkxHistoricalKlineAdapter(
                            new OkxHttpClient(
                                    http,
                                    mapper,
                                    "http://127.0.0.1:" + server.getAddress().getPort(),
                                    Duration.ofSeconds(3)));
            var bars =
                    adapter.fetchHistoricalKlines(
                            new HistoricalKlineRequest(
                                    "OKX",
                                    "SPOT",
                                    "BTC-USDT",
                                    "1h",
                                    start,
                                    start.plusSeconds(8 * 3600).minusMillis(1),
                                    8));
            assertEquals(8, bars.size());
            assertTrue(bars.stream().anyMatch(bar -> bar.openTime().equals(start)));
            assertTrue(privateHeadersAbsent.get());
        } finally {
            server.stop(0);
        }
    }
}
