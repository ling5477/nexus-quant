package com.guidinglight.nexusquant.scheduler.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.guidinglight.nexusquant.account.domain.performance.ClosedTradeStatistics;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 字面成交、账户分录与可见估值验证重建数学；不替代正式SQL读取或真实撮合证明。 */
class StrategySimPerformanceQueryTest {
    private final Instant start = Instant.parse("2026-10-01T00:00:00Z");

    private StrategySimPerformanceQuery.Mark mark(int bucket, String price) {
        Instant at = start.plusSeconds(bucket * 900L);
        return new StrategySimPerformanceQuery.Mark(
                "mark-" + bucket,
                at,
                price,
                "BTC-USDT",
                at.toString(),
                at.minusSeconds(1).toString());
    }

    private StrategySimPerformanceQuery.Fill fill(
            String id, int seconds, String side, String price, String fee, String cash) {
        return new StrategySimPerformanceQuery.Fill(
                new ClosedTradeStatistics.Trade(
                        id,
                        "BTC-USDT",
                        side,
                        BigDecimal.ONE,
                        new BigDecimal(price),
                        new BigDecimal(fee),
                        "USDT",
                        start.plusSeconds(seconds)),
                new BigDecimal(cash),
                4,
                BigDecimal.ZERO,
                true,
                true);
    }

    @Test
    void canonicalRebatesAndReplayEventClockProduceARealizedHistory() {
        var result =
                StrategySimPerformanceQuery.calculate(
                        "run",
                        "REPLAY_EVENT_TIME",
                        900,
                        start.plusSeconds(1_000_000),
                        new BigDecimal("1000"),
                        List.of(mark(0, "100"), mark(1, "110"), mark(2, "132")),
                        List.of(
                                fill("b", 1, "BUY", "100", "-1", "-99"),
                                fill("s", 901, "SELL", "120", "-1", "121")),
                        null,
                        null);
        assertEquals("AVAILABLE", result.status());
        assertEquals(0, result.pnl().compareTo(new BigDecimal("22")));
        assertEquals(0, result.samples().get(1).equity().compareTo(new BigDecimal("1011")));
        assertEquals(0, result.outcomes().netProfit().compareTo(new BigDecimal("22")));
        assertEquals(0, result.outcomes().winRate().compareTo(BigDecimal.ONE));
        assertEquals("AVAILABLE", result.sharpe().status());
        assertEquals("REPLAY_EVENT_TIME", result.timeBasis());
        assertEquals(start.plusSeconds(1800), result.coverageEnd());
    }

    @Test
    void rangeUsesPriorBuysForBasisAndExcludesTheInitialValuationClose() {
        var result =
                StrategySimPerformanceQuery.calculate(
                        "run",
                        "REPLAY_EVENT_TIME",
                        900,
                        start,
                        new BigDecimal("1000"),
                        List.of(mark(0, "100"), mark(1, "110"), mark(2, "120")),
                        List.of(
                                fill("b", 1, "BUY", "100", "1", "-101"),
                                fill("s", 901, "SELL", "120", "1", "119")),
                        start.plusSeconds(900),
                        start.plusSeconds(1800));
        assertEquals(2, result.samples().size());
        assertEquals(0, result.pnl().compareTo(new BigDecimal("9")));
        assertEquals(0, result.outcomes().netProfit().compareTo(new BigDecimal("18")));
        assertEquals(1, result.outcomes().winningCount());
        assertEquals("INSUFFICIENT_SAMPLES", result.sharpe().status());
    }

    @Test
    void missingOrFutureMarkNeverProducesACurve() {
        var future =
                new StrategySimPerformanceQuery.Mark(
                        "future",
                        start,
                        "100",
                        "BTC-USDT",
                        start.plusSeconds(1).toString(),
                        start.toString());
        var result =
                StrategySimPerformanceQuery.calculate(
                        "run",
                        "ACTUAL_EVENT_TIME",
                        900,
                        start,
                        new BigDecimal("1000"),
                        List.of(future),
                        List.of(),
                        null,
                        null);
        assertEquals("MARK_SOURCE_UNAVAILABLE", result.status());
        assertEquals(0, result.samples().size());
        assertNull(result.pnl());
        assertEquals(
                "NO_DATA",
                StrategySimPerformanceQuery.calculate(
                                "run",
                                "ACTUAL_EVENT_TIME",
                                900,
                                start,
                                new BigDecimal("1000"),
                                List.of(),
                                List.of(),
                                null,
                                null)
                        .status());
    }

    @Test
    void fillsAfterTheLastActualMarkRemainOutsideHistory() {
        var result =
                StrategySimPerformanceQuery.calculate(
                        "run",
                        "ACTUAL_EVENT_TIME",
                        900,
                        start,
                        new BigDecimal("1000"),
                        List.of(mark(0, "100"), mark(1, "110")),
                        List.of(fill("b", 901, "BUY", "120", "1", "-121")),
                        null,
                        null);
        assertEquals(1, result.tradesAfterCoverage());
        assertEquals(0, result.pnl().compareTo(BigDecimal.ZERO));
        assertEquals("NO_CLOSED_TRADES", result.outcomes().status());
    }

    /** 现金分录与净持仓各按原币事实计算；BTC收费不能被当作USDT现金扣款或毛持仓。 */
    @Test
    void nativeBaseFeeHistoryAgreesWithCashAndClosedOutcome() {
        var buy =
                new StrategySimPerformanceQuery.Fill(
                        new ClosedTradeStatistics.Trade(
                                "native-buy",
                                "BTC-USDT",
                                "BUY",
                                BigDecimal.ONE,
                                new BigDecimal("100"),
                                new BigDecimal("0.001"),
                                "BTC",
                                start.plusSeconds(1)),
                        new BigDecimal("-100"),
                        4,
                        BigDecimal.ZERO,
                        true,
                        true,
                        BigDecimal.ZERO);
        var sell =
                new StrategySimPerformanceQuery.Fill(
                        new ClosedTradeStatistics.Trade(
                                "native-sell",
                                "BTC-USDT",
                                "SELL",
                                new BigDecimal("0.999"),
                                new BigDecimal("100.2003"),
                                new BigDecimal("0.10010010"),
                                "USDT",
                                start.plusSeconds(901)),
                        new BigDecimal("99.99999960"),
                        4,
                        BigDecimal.ZERO,
                        true,
                        true,
                        BigDecimal.ZERO);
        var result =
                StrategySimPerformanceQuery.calculate(
                        "native-run",
                        "REPLAY_EVENT_TIME",
                        900,
                        start,
                        new BigDecimal("100"),
                        List.of(mark(0, "100"), mark(1, "100"), mark(2, "100.2003")),
                        List.of(buy, sell),
                        null,
                        null);
        assertEquals("AVAILABLE", result.status());
        assertEquals(
                0, result.samples().get(1).positionQuantity().compareTo(new BigDecimal("0.999")));
        assertEquals(0, result.finalEquity().compareTo(new BigDecimal("99.99999960")));
        assertEquals(0, result.pnl().compareTo(new BigDecimal("-0.00000040")));
        assertEquals(0, result.samples().getLast().positionQuantity().signum());
        assertEquals(1, result.outcomes().losingCount());
        assertEquals(0, result.outcomes().winningCount());
    }
}
