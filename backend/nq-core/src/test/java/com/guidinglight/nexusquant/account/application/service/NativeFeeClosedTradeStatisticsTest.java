package com.guidinglight.nexusquant.account.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.guidinglight.nexusquant.account.domain.performance.ClosedTradeStatistics;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 原币费用按实际现金本金和净base消费，重放成交明细不能把base费用估值再扣一次。 */
class NativeFeeClosedTradeStatisticsTest {
    private static final Instant START = Instant.parse("2026-09-25T00:00:00Z");

    @Test
    void originalMicroLossRemainsLossAfterNativeBaseFee() {
        var result =
                calculate(
                        trade("b", 1, "BUY", "1", "100", "0.001", "BTC"),
                        trade("s", 2, "SELL", "0.999", "100.2003", "0.10010010", "USDT"));
        assertEquals("AVAILABLE", result.status());
        assertEquals(0, result.winningCount());
        assertEquals(1, result.losingCount());
        equal("0.00000040", result.netLoss());
        equal("0", result.winRate());
    }

    @Test
    void baseRebateChangesNetQuantityAndDoesNotBecomeAdditionalCashCost() {
        var result =
                calculate(
                        trade("b", 1, "BUY", "1", "100", "-0.001", "BTC"),
                        trade("s", 2, "SELL", "1.001", "100", "0", "USDT"));
        assertEquals(1, result.winningCount());
        equal("0.1", result.netProfit());
    }

    @Test
    void partialThenFullCloseConsumesRemainingCashBasisExactly() {
        var result =
                calculate(
                        trade("b", 1, "BUY", "1", "100", "0.002", "BTC"),
                        trade("s1", 2, "SELL", "0.499", "100", "0", "USDT"),
                        trade("s2", 3, "SELL", "0.499", "100", "0", "USDT"));
        assertEquals(2, result.losingCount());
        equal("0.2", result.netLoss());
    }

    @Test
    void baseFeeOnSellConsumesNetOutgoingPositionWithOriginalGrossProceeds() {
        var charged =
                calculate(
                        trade("b", 1, "BUY", "1", "100", "0", "USDT"),
                        trade("s", 2, "SELL", "0.999", "100", "0.001", "BTC"));
        assertEquals(1, charged.losingCount());
        equal("0.1", charged.netLoss());
        var rebate =
                calculate(
                        trade("b", 1, "BUY", "1", "100", "0", "USDT"),
                        trade("s", 2, "SELL", "1.001", "100", "-0.001", "BTC"));
        assertEquals(1, rebate.winningCount());
        equal("0.1", rebate.netProfit());
    }

    @Test
    void unknownAssetAndOversoldNetBasisRemainUnavailable() {
        assertEquals(
                "FEE_BASIS_UNAVAILABLE",
                calculate(trade("b", 1, "BUY", "1", "100", "0.001", "BNB")).status());
        assertEquals(
                "OPENING_BASIS_UNAVAILABLE",
                calculate(
                                trade("b", 1, "BUY", "1", "100", "0.001", "BTC"),
                                trade("s", 2, "SELL", "1", "100", "0", "USDT"))
                        .status());
    }

    private static ClosedTradeStatistics.Result calculate(ClosedTradeStatistics.Trade... trades) {
        return ClosedTradeStatistics.calculate(
                List.of(trades), "USDT", START, START.plusSeconds(3600), true);
    }

    private static ClosedTradeStatistics.Trade trade(
            String id,
            int minute,
            String side,
            String quantity,
            String price,
            String fee,
            String currency) {
        return new ClosedTradeStatistics.Trade(
                id,
                "BTC-USDT",
                side,
                new BigDecimal(quantity),
                new BigDecimal(price),
                new BigDecimal(fee),
                currency,
                START.plusSeconds(minute * 60L));
    }

    private static void equal(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
