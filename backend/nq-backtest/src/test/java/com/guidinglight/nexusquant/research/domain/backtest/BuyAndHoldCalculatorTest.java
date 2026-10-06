package com.guidinglight.nexusquant.research.domain.backtest;

import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.strategy.domain.SpotTargetSizer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BuyAndHoldCalculatorTest {
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private SpotTargetSizer.Rules rules(String fee, String slip) {
        return new SpotTargetSizer.Rules(new BigDecimal("0.0001"), new BigDecimal("0.01"),
                new BigDecimal("0.0001"), new BigDecimal("1"), new BigDecimal(fee), new BigDecimal(slip));
    }
    @Test
    void deterministicNextOpenMarkToMarketHasNoExitCost() {
        var bars = List.of(bar(0, "100", "999"), bar(1, "100", "110"));
        var result = BuyAndHoldCalculator.calculate(bars, new BigDecimal("100"), rules("0", "0"));
        assertEquals(START.plusSeconds(60).toString(), result.entryTime());
        assertEquals(0, new BigDecimal("110").compareTo(result.finalEquity()));
        assertEquals(0, new BigDecimal("0.1").compareTo(result.benchmarkReturn()));
        assertEquals("MARK_TO_MARKET", result.valuationPolicy());
        assertEquals(result, BuyAndHoldCalculator.calculate(bars, new BigDecimal("100"), rules("0", "0")));
        var costs = BuyAndHoldCalculator.calculate(bars, new BigDecimal("100"), rules("0.001", "10"));
        assertEquals(0, new BigDecimal("100.10").compareTo(costs.entryPrice()));
        assertEquals(0, new BigDecimal("0.0998998").compareTo(costs.fee()));
        assertEquals(0, new BigDecimal("0.0998").compareTo(costs.slippage()));
        assertEquals(0, new BigDecimal("109.7803002").compareTo(costs.finalEquity()));
    }
    @Test
    void insufficientBarsAndCapitalHaveNullMetrics() {
        var one = BuyAndHoldCalculator.calculate(List.of(bar(0, "100", "100")), new BigDecimal("100"), rules("0", "0"));
        assertEquals("INSUFFICIENT_DATA", one.status());
        assertNull(one.benchmarkReturn());
        var dust = BuyAndHoldCalculator.calculate(List.of(bar(0, "100", "100"), bar(1, "100", "100")),
                new BigDecimal("0.001"), rules("0", "0"));
        assertEquals("NOT_AVAILABLE", dust.status());
        assertNull(dust.finalEquity());
        assertNull(dust.fee());
    }
    @Test
    void availabilityEqualityRequiresStrictlyLaterOpenAndFutureCloseCannotPickEntry() {
        var original = bar(0, "100", "100");
        var delayed = new HistoricalBar(original.exchangeCode(), original.marketType(), original.symbol(), original.interval(),
                original.openTime(), original.closeTime(), original.openPrice(), original.highPrice(), original.lowPrice(),
                original.closePrice(), original.volume(), null, null, "OK", "{}", START.plusSeconds(60));
        var bars = List.of(delayed, bar(1, "100", "1"), bar(2, "120", "200"));
        var result = BuyAndHoldCalculator.calculate(bars, new BigDecimal("100"), rules("0", "0"));
        assertEquals(START.plusSeconds(120).toString(), result.entryTime());
        assertEquals(0, new BigDecimal("120").compareTo(result.entryPrice()));
    }
    private HistoricalBar bar(int minute, String open, String close) {
        var start = START.plusSeconds(minute * 60L);
        return new HistoricalBar("OKX", "BTC-USDT", BarInterval.ONE_MINUTE, start, start.plusSeconds(59),
                new BigDecimal(open), new BigDecimal(open).max(new BigDecimal(close)),
                new BigDecimal(open).min(new BigDecimal(close)), new BigDecimal(close), BigDecimal.ONE);
    }
}
