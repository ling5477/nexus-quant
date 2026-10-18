package com.guidinglight.nexusquant.marketdata.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.StoredBarIdentity;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 明确来源与缺失边界的字面样本；不是完整逐笔或真实供应方认证证明。 */
class PriceEventSourceCodecTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final PriceEventSourceCodec codec = new PriceEventSourceCodec(mapper);
    private final Instant start = Instant.parse("2026-10-01T00:00:00Z"),
            end = start.plusSeconds(59).plusMillis(999);
    private final StoredBarIdentity target =
            new StoredBarIdentity(
                    7,
                    new HistoricalBar(
                            "OKX",
                            "SPOT",
                            "BTC-USDT",
                            BarInterval.ONE_MINUTE,
                            start,
                            end,
                            new BigDecimal("100"),
                            new BigDecimal("110"),
                            new BigDecimal("90"),
                            new BigDecimal("105"),
                            BigDecimal.ONE,
                            null,
                            4L,
                            "OK",
                            "{}",
                            end),
                    "EXCHANGE_HISTORICAL");

    private ObjectNode source() {
        ObjectNode node = mapper.createObjectNode();
        node.put("exchangeCode", "OKX");
        node.put("marketType", "SPOT");
        node.put("symbol", "BTC-USDT");
        node.put("interval", "1m");
        node.put("openTime", start.toString());
        node.put("closeTime", end.toString());
        node.put("openPrice", "100.00000000");
        node.put("highPrice", "110");
        node.put("lowPrice", "90");
        node.put("closePrice", "105");
        node.put("openPriceAt", start.plusMillis(123).toString());
        node.put("highPriceAt", start.plusSeconds(5).toString());
        node.put("lowPriceAt", start.plusSeconds(17).toString());
        node.put("closePriceAt", start.plusSeconds(58).plusNanos(456000).toString());
        return node;
    }

    @Test
    void explicitTimesArePreservedAndDoNotBecomeCandleBoundaries() {
        var result = codec.parse(source().toString(), target);
        assertEquals(start.plusMillis(123), result.openPriceAt());
        assertEquals(start.plusSeconds(58).plusNanos(456000), result.closePriceAt());
        assertNotEquals(start, result.openPriceAt());
        assertNotEquals(end, result.closePriceAt());
        ObjectNode alternate = source();
        alternate.put("openPriceAt", "2026-10-01T08:00:00.123+08:00");
        alternate.put("openPrice", "100.0");
        assertEquals(
                result.documentSha256(),
                codec.parse(alternate.toString(), target).documentSha256());
    }

    @Test
    void partialUnknownNullOrDuplicateSourceFieldsAreRejected() {
        for (String key : List.of("openPriceAt", "highPriceAt", "lowPriceAt", "closePriceAt")) {
            ObjectNode node = source();
            node.remove(key);
            String partial = node.toString();
            assertThrows(IllegalArgumentException.class, () -> codec.parse(partial, target));
            node = source();
            node.putNull(key);
            String input = node.toString();
            assertThrows(IllegalArgumentException.class, () -> codec.parse(input, target));
        }
        ObjectNode extra = source();
        extra.put("unsupported", "not-a-source-field");
        assertThrows(IllegalArgumentException.class, () -> codec.parse(extra.toString(), target));
        String duplicate =
                source().toString()
                        .replace(
                                "\"exchangeCode\":\"OKX\"",
                                "\"exchangeCode\":\"OKX\",\"exchangeCode\":\"BINANCE\"");
        assertThrows(IllegalArgumentException.class, () -> codec.parse(duplicate, target));
    }

    @Test
    void wrongScopePricesOrEventOrderCannotAttachToThisCandle() {
        for (String key :
                List.of(
                        "symbol",
                        "highPrice",
                        "openTime",
                        "openPriceAt",
                        "closePriceAt",
                        "highPriceAt")) {
            ObjectNode node = source();
            node.put(
                    key,
                    switch (key) {
                        case "symbol" -> "ETH-USDT";
                        case "highPrice" -> "111";
                        case "openTime", "openPriceAt" -> start.minusSeconds(1).toString();
                        case "closePriceAt" -> end.plusNanos(1000).toString();
                        default -> start.plusSeconds(59).toString();
                    });
            assertThrows(
                    IllegalArgumentException.class, () -> codec.parse(node.toString(), target));
        }
    }

    @Test
    void excessNumericTimeDepthOrLengthPrecisionDoesNotSilentlyNormalize() {
        for (String value : List.of("1e2147483647", "1e-2147483647", "100.000000001", "NaN")) {
            ObjectNode node = source();
            node.put("openPrice", value);
            assertThrows(
                    IllegalArgumentException.class, () -> codec.parse(node.toString(), target));
        }
        ObjectNode nano = source();
        nano.put("highPriceAt", "2026-10-01T00:00:05.000000001Z");
        assertThrows(IllegalArgumentException.class, () -> codec.parse(nano.toString(), target));
        assertThrows(IllegalArgumentException.class, () -> codec.parse(" ".repeat(32769), target));
        assertThrows(
                IllegalArgumentException.class,
                () -> codec.parse("{\"x\":{\"x\":{\"x\":{\"x\":{\"x\":1}}}}}", target));
        assertThrows(IllegalArgumentException.class, () -> codec.parse(source() + " {}", target));
    }

    @Test
    void sourceAndQualityBelongToTheVersionIdentity() {
        assertNotEquals(
                codec.barSha256(target),
                codec.barSha256(new StoredBarIdentity(7, target.bar(), "IMPORT")));
        assertEquals(codec.barSha256(target), codec.barSha256(target));
    }
}
