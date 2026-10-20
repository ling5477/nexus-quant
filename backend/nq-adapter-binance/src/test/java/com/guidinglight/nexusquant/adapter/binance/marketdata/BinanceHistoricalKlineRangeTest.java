package com.guidinglight.nexusquant.adapter.binance.marketdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.api.model.HistoricalKlineRequest;
import com.guidinglight.nexusquant.adapter.api.service.error.HistoricalKlineAdapterException;
import com.guidinglight.nexusquant.adapter.binance.http.BinanceHttpClient;
import com.guidinglight.nexusquant.adapter.binance.signing.BinanceRequestSigner;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** 本机协议源按Binance实际的开盘时间闭区间筛选，证明完整K线窗口没有多取或漏取。 */
class BinanceHistoricalKlineRangeTest {
    private static final Instant START = Instant.parse("2026-10-01T00:00:00Z");

    @ParameterizedTest
    @CsvSource({"1m,60000", "5m,300000", "15m,900000", "1h,3600000", "4h,14400000", "1d,86400000"})
    void shouldOnlyRequestBarsWhollyInsideWindowForEverySupportedInterval(
            String interval, long period) throws Exception {
        try (var source = new Source(period)) {
            Instant end = START.plusMillis(3 * period);
            var bars = source.adapter().fetchHistoricalKlines(request(interval, START, end, 500));
            assertEquals(3, bars.size(), "结束边界开盘的下一根不属于完整K线窗口");
            assertEquals(START.plusMillis(2 * period), bars.getLast().openTime());
            assertEquals(end.minusMillis(1), bars.getLast().closeTime());
            assertTrue(
                    bars.stream()
                            .allMatch(
                                    bar ->
                                            !bar.openTime().isBefore(START)
                                                    && !bar.closeTime().isAfter(end)));
            assertEquals(
                    Long.toString(end.toEpochMilli() - period + 1), source.query.get("endTime"));
            assertEquals(0, bars.getFirst().openPrice().compareTo(new BigDecimal("10.12345678")));
            assertEquals(0, bars.getFirst().quoteVolume().compareTo(new BigDecimal("22.87654321")));
            assertEquals(1, source.calls.get());
            assertFalse(source.signed);
        }
    }

    @Test
    void shouldIncludeExactCloseMillisecondAndExcludeOneMillisecondEarlier() throws Exception {
        try (var source = new Source(60000)) {
            assertEquals(
                    3,
                    source.adapter()
                            .fetchHistoricalKlines(
                                    request("1m", START, START.plusMillis(179999), 500))
                            .size());
            assertEquals(
                    2,
                    source.adapter()
                            .fetchHistoricalKlines(
                                    request("1m", START, START.plusMillis(179998), 500))
                            .size());
        }
    }

    @Test
    void shouldNotRoundSubMillisecondStartBackIntoPreviousBar() throws Exception {
        try (var source = new Source(60000)) {
            var bars =
                    source.adapter()
                            .fetchHistoricalKlines(
                                    request(
                                            "1m",
                                            START.plusNanos(500000),
                                            START.plusSeconds(180),
                                            500));
            assertEquals(2, bars.size());
            assertEquals(START.plusSeconds(60), bars.getFirst().openTime());
            assertEquals(Long.toString(START.toEpochMilli() + 1), source.query.get("startTime"));
        }
    }

    @Test
    void shouldNotContactSourceForWindowWithoutACompleteBar() throws Exception {
        try (var source = new Source(60000)) {
            assertTrue(
                    source.adapter()
                            .fetchHistoricalKlines(request("1m", START, START.plusSeconds(30), 500))
                            .isEmpty());
            assertTrue(
                    source.adapter()
                            .fetchHistoricalKlines(request("1m", START, START, 500))
                            .isEmpty());
            assertEquals(0, source.calls.get());
        }
    }

    @Test
    void shouldRejectReversedRangeBeforeSourceRequest() throws Exception {
        try (var source = new Source(60000)) {
            assertThrows(
                    HistoricalKlineAdapterException.class,
                    () ->
                            source.adapter()
                                    .fetchHistoricalKlines(
                                            request("1m", START.plusSeconds(60), START, 500)));
            assertEquals(0, source.calls.get());
        }
    }

    @Test
    void shouldKeepExplicitProviderLimitAndUnsupportedIntervalGuard() throws Exception {
        try (var source = new Source(60000)) {
            assertEquals(
                    1,
                    source.adapter()
                            .fetchHistoricalKlines(request("1m", START, START.plusSeconds(180), 1))
                            .size());
            assertEquals("1", source.query.get("limit"));
            assertThrows(
                    HistoricalKlineAdapterException.class,
                    () ->
                            source.adapter()
                                    .fetchHistoricalKlines(
                                            request("2m", START, START.plusSeconds(180), 500)));
            assertEquals(1, source.calls.get());
        }
    }

    private static HistoricalKlineRequest request(
            String interval, Instant start, Instant end, int limit) {
        return new HistoricalKlineRequest(
                "BINANCE", "SPOT", "BTC-USDT", interval, start, end, limit);
    }

    private static final class Source implements AutoCloseable {
        private final HttpServer server;
        private final AtomicInteger calls = new AtomicInteger();
        private final Map<String, String> query = new HashMap<>();
        private boolean signed;

        private Source(long period) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(
                    "/api/v3/klines",
                    exchange -> {
                        calls.incrementAndGet();
                        query.clear();
                        for (String item : exchange.getRequestURI().getRawQuery().split("&")) {
                            String[] pair = item.split("=", 2);
                            query.put(pair[0], URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
                        }
                        signed =
                                exchange.getRequestHeaders().containsKey("X-MBX-APIKEY")
                                        || query.containsKey("signature");
                        long from = Long.parseLong(query.get("startTime"));
                        long to = Long.parseLong(query.get("endTime"));
                        int limit = Integer.parseInt(query.get("limit"));
                        var rows = new ArrayList<String>();
                        for (int index = 0; index < 4 && rows.size() < limit; index++) {
                            long open = START.toEpochMilli() + index * period;
                            if (open >= from && open <= to) {
                                rows.add(
                                        "["
                                                + open
                                                + ",\"10.12345678\",\"11\",\"9\",\"10.5\",\"2\","
                                                + (open + period - 1)
                                                + ",\"22.87654321\",1]");
                            }
                        }
                        byte[] body =
                                ("[" + String.join(",", rows) + "]")
                                        .getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(200, body.length);
                        try (var output = exchange.getResponseBody()) {
                            output.write(body);
                        }
                    });
            server.start();
        }

        private BinanceHistoricalKlineAdapter adapter() {
            return new BinanceHistoricalKlineAdapter(
                    new BinanceHttpClient(
                            HttpClient.newHttpClient(),
                            new ObjectMapper(),
                            "http://127.0.0.1:" + server.getAddress().getPort(),
                            Duration.ofSeconds(3),
                            new BinanceRequestSigner(),
                            System::currentTimeMillis,
                            null));
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
