package com.guidinglight.nexusquant.account.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.account.domain.performance.ClosedTradeStatistics;
import com.guidinglight.nexusquant.account.domain.performance.PerformanceStatistics;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 使用字面数值验证定义与缺失边界，不依赖被测实现生成预期值。 */
class PerformanceStatisticsTest {
    private final Instant start = Instant.parse("2026-10-01T00:00:00Z");

    private PerformanceStatistics.Point point(int bucket, String equity, String flow) {
        return new PerformanceStatistics.Point(
                start.plusSeconds(bucket * 900L), new BigDecimal(equity), new BigDecimal(flow));
    }

    private ClosedTradeStatistics.Trade trade(
            String id,
            int bucket,
            String side,
            String qty,
            String price,
            String fee,
            String currency) {
        return new ClosedTradeStatistics.Trade(
                id,
                "BTC-USDT",
                side,
                new BigDecimal(qty),
                new BigDecimal(price),
                fee == null ? null : new BigDecimal(fee),
                currency,
                start.plusSeconds(bucket * 900L));
    }

    @Test
    void sharpeIsNonAnnualizedPopulationAndFundingDoesNotBecomeReturn() {
        // 扣除净注资后为100、110、99，两段收益为+10%、-10%，均值为0且方差非零。
        var result =
                PerformanceStatistics.sharpe(
                        List.of(
                                point(0, "100", "100"),
                                point(1, "210", "200"),
                                point(2, "199", "200")),
                        900);
        assertEquals("AVAILABLE", result.status());
        assertEquals(0, result.value().compareTo(BigDecimal.ZERO));
        assertEquals(2, result.returnCount());
        // 两段收益+10%、+20%，总体标准差5%，Sharpe=3，不乘年化系数。
        var positive =
                PerformanceStatistics.sharpe(
                        List.of(point(0, "100", "0"), point(1, "110", "0"), point(2, "132", "0")),
                        900);
        assertEquals(0, positive.value().compareTo(new BigDecimal("3")));
    }

    @Test
    void undefinedSharpeDoesNotBecomeZero() {
        assertEquals(
                "INSUFFICIENT_SAMPLES",
                PerformanceStatistics.sharpe(
                                List.of(point(0, "100", "0"), point(1, "110", "0")), 900)
                        .status());
        var flat =
                PerformanceStatistics.sharpe(
                        List.of(point(0, "100", "0"), point(1, "100", "0"), point(2, "100", "0")),
                        900);
        assertNull(flat.value());
        assertEquals("ZERO_VARIANCE", flat.status());
        assertEquals(
                "IRREGULAR_INTERVALS",
                PerformanceStatistics.sharpe(
                                List.of(
                                        point(0, "100", "0"),
                                        point(1, "110", "0"),
                                        point(3, "132", "0")),
                                900)
                        .status());
        assertEquals(
                "IRREGULAR_INTERVALS",
                PerformanceStatistics.sharpe(
                                List.of(
                                        point(0, "100", "0"),
                                        point(1, "110", "0"),
                                        point(1, "132", "0")),
                                900)
                        .status());
        assertEquals(
                "NON_POSITIVE_EQUITY",
                PerformanceStatistics.sharpe(
                                List.of(point(0, "0", "0"), point(1, "1", "0"), point(2, "2", "0")),
                                900)
                        .status());
        assertEquals(
                "INCOMPLETE_SAMPLES",
                PerformanceStatistics.sharpe(
                                List.of(
                                        point(0, "100", "0"),
                                        new PerformanceStatistics.Point(
                                                start.plusSeconds(900), null, null),
                                        point(2, "132", "0")),
                                900)
                        .status());
    }

    @Test
    void lookbackBuildsCostButOnlyCoveredPartialSellFillsCount() {
        var result =
                ClosedTradeStatistics.calculate(
                        List.of(
                                trade("b", 0, "BUY", "2", "100", "2", "USDT"),
                                trade("s1", 1, "SELL", "1", "110", "1", "USDT"),
                                trade("s2", 2, "SELL", "1", "100", "1", "USDT")),
                        "USDT",
                        start.plusSeconds(1),
                        start.plusSeconds(1800),
                        true);
        assertEquals("AVAILABLE", result.status());
        assertEquals(1, result.winningCount());
        assertEquals(1, result.losingCount());
        assertEquals(0, result.winRate().compareTo(new BigDecimal("0.5")));
        assertEquals(0, result.netProfit().compareTo(new BigDecimal("8")));
        assertEquals(0, result.netLoss().compareTo(new BigDecimal("2")));
        assertEquals(0, result.profitLossRatio().compareTo(new BigDecimal("4")));
    }

    @Test
    void rebatesAreNegativeCostsAndOpenBuysAreNotWins() {
        var open =
                ClosedTradeStatistics.calculate(
                        List.of(trade("b", 0, "BUY", "1", "100", "-1", "USDT")),
                        "USDT",
                        start,
                        start.plusSeconds(900),
                        true);
        assertEquals("NO_CLOSED_TRADES", open.status());
        assertNull(open.winRate());
        var result =
                ClosedTradeStatistics.calculate(
                        List.of(
                                trade("b", 0, "BUY", "1", "100", "-1", "USDT"),
                                trade("s", 1, "SELL", "1", "100", "-1", "USDT")),
                        "USDT",
                        start,
                        start.plusSeconds(900),
                        true);
        assertEquals(0, result.netProfit().compareTo(new BigDecimal("2")));
        assertEquals(0, result.winRate().compareTo(BigDecimal.ONE));
        assertNull(result.profitLossRatio());
    }

    @Test
    void missingOpeningFeeOrUnsupportedCurrencyRejectsOutcomeInsteadOfClipping() {
        assertEquals(
                "OPENING_BASIS_UNAVAILABLE",
                ClosedTradeStatistics.calculate(
                                List.of(trade("s", 1, "SELL", "1", "100", "0", "USDT")),
                                "USDT",
                                start,
                                start.plusSeconds(900),
                                true)
                        .status());
        assertEquals(
                "FEE_BASIS_UNAVAILABLE",
                ClosedTradeStatistics.calculate(
                                List.of(trade("b", 0, "BUY", "1", "100", null, "USDT")),
                                "USDT",
                                start,
                                start.plusSeconds(900),
                                true)
                        .status());
        assertEquals(
                "FEE_BASIS_UNAVAILABLE",
                ClosedTradeStatistics.calculate(
                                List.of(trade("b", 0, "BUY", "1", "100", "1", "BTC")),
                                "USDT",
                                start,
                                start.plusSeconds(900),
                                true)
                        .status());
        assertNull(
                ClosedTradeStatistics.calculate(
                                List.of(), "USDT", start, start.plusSeconds(900), false)
                        .winRate());
    }

    @Test
    void originalCashPrecisionAndFlatDenominatorArePreserved() {
        var result =
                ClosedTradeStatistics.calculate(
                        List.of(
                                trade("b", 0, "BUY", "0.00000001", "1.12345678", "0", "USDT"),
                                trade("s", 1, "SELL", "0.00000001", "1.23456789", "0", "USDT")),
                        "USDT",
                        start,
                        start.plusSeconds(900),
                        true);
        assertEquals(1, result.flatCount());
        assertEquals(0, result.winRate().compareTo(BigDecimal.ZERO));
        assertTrue(result.netProfit().signum() == 0 && result.netLoss().signum() == 0);
    }

    @Test
    void sellAtInitialValuationDoesNotEnterTheFollowingReturnPeriod() {
        var result =
                ClosedTradeStatistics.calculate(
                        List.of(
                                trade("b", 0, "BUY", "2", "100", "0", "USDT"),
                                trade("s1", 1, "SELL", "1", "110", "0", "USDT"),
                                trade("s2", 2, "SELL", "1", "90", "0", "USDT")),
                        "USDT",
                        start.plusSeconds(900),
                        start.plusSeconds(1800),
                        true);
        assertEquals(0, result.winningCount());
        assertEquals(1, result.losingCount());
    }
}
