package com.guidinglight.nexusquant.adapter.okx.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.okx.http.OkxHttpClient;
import com.guidinglight.nexusquant.adapter.okx.marketdata.OkxInstrumentsCache;
import com.guidinglight.nexusquant.adapter.okx.signing.OkxRequestSigner;
import com.guidinglight.nexusquant.adapter.okx.trading.OkxExchangeAdapter;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** 原始字段符号在适配层保留，canonical消费反转并按持久化精度舍入；缺失不补零。 */
class OkxSignedFeeCostTest {
    @Test
    void costSignAndHalfUpPrecisionDistinguishChargesRebatesAndZero() {
        String[][] cases = {
            {"-0.1", "0.10000000"},
            {"0", "0.00000000"},
            {"0.1", "-0.10000000"},
            {"-0.000000005", "0.00000001"},
            {"0.000000005", "-0.00000001"},
            {"-0.000000000001", "0.00000000"},
            {"0.000000000001", "0.00000000"}
        };
        for (String[] row : cases) {
            assertEquals(new BigDecimal(row[1]), OkxFeeCost.amount(new BigDecimal(row[0])));
        }
        assertThrows(IllegalArgumentException.class, () -> OkxFeeCost.amount(null));
    }

    @Test
    void actualFillDecoderPreservesProviderSignAndRefusesMissingFee() throws Exception {
        var response = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/api/v5/trade/fills",
                exchange -> {
                    byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (var output = exchange.getResponseBody()) {
                        output.write(bytes);
                    }
                });
        server.start();
        try {
            ObjectMapper mapper = new ObjectMapper();
            Clock clock = Clock.systemUTC();
            var client =
                    new OkxHttpClient(
                            HttpClient.newHttpClient(),
                            mapper,
                            "http://127.0.0.1:" + server.getAddress().getPort(),
                            Duration.ofSeconds(2),
                            new OkxRequestSigner(),
                            () -> "2026-10-13T00:00:00.000Z",
                            OkxApiCredentials.unconfigured(),
                            false);
            var adapter =
                    new OkxExchangeAdapter(
                            new OkxExchangeAdapter.Dependencies(
                                    mapper,
                                    client,
                                    new OkxInstrumentsCache(client, clock, Duration.ofMinutes(5)),
                                    clock,
                                    "SIM"));
            for (String fee : List.of("-0.1", "0", "0.1")) {
                response.set(
                        "{\"code\":\"0\",\"data\":[{\"tradeId\":\"fill-test\",\"ordId\":\"venue-test\",\"instId\":\"BTC-USDT\",\"side\":\"buy\",\"fillPx\":\"100\",\"fillSz\":\"1\",\"fee\":\""
                                + fee
                                + "\",\"feeCcy\":\"USDT\",\"ts\":\"1791849600000\"}]}");
                assertEquals(
                        0,
                        new BigDecimal(fee)
                                .compareTo(
                                        adapter.listFills("BTC-USDT", "venue-test", "fixture")
                                                .getFirst()
                                                .fee()));
                assertEquals(
                        0,
                        new BigDecimal(fee)
                                .compareTo(
                                        adapter.listTradeReports(
                                                        "BTC-USDT", "venue-test", "fixture")
                                                .getFirst()
                                                .fee()));
            }
            response.set(
                    "{\"code\":\"0\",\"data\":[{\"tradeId\":\"fill-test\",\"ordId\":\"venue-test\",\"instId\":\"BTC-USDT\",\"fillPx\":\"100\",\"fillSz\":\"1\",\"feeCcy\":\"USDT\"}]}");
            assertThrows(
                    IllegalStateException.class,
                    () -> adapter.listFills("BTC-USDT", "venue-test", "fixture"));
        } finally {
            server.stop(0);
        }
    }
}
