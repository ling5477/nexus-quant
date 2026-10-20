package com.guidinglight.nexusquant.research.domain.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guidinglight.nexusquant.marketdata.domain.DiscountAssetPriceSnapshot;
import com.guidinglight.nexusquant.research.domain.backtest.SimTrade;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 用可整除的独立现金收支例证明闭合盈亏；base费用不能留下虚假持仓或再加一次quote成本。 */
class TradeOutcomeCalculatorTest {
    private final TradeOutcomeCalculator calculator = new TradeOutcomeCalculator();

    @Test
    void independentNearBreakEvenCounterexampleRemainsALoss() {
        // 独立PG反例：100本金，净0.999 base全卖，收到99.99999960 quote，不能标为盈利。
        var result =
                calculateDeclaredOrder(
                        List.of(
                                nativeTrade("BUY", "1", "100", "0.001"),
                                nativeTrade("SELL", "0.999", "100.2003", "0.10010010")));
        assertCounts(result, 0, 1, 0);
        equal("0.00000040", result.grossLoss());
        equal("0", result.grossProfit());
    }

    @Test
    void baseCostAtExactBreakEvenIsFlat() {
        // 支付99 quote，实际收到0.99 base；以100卖出收到99 quote，净收支严格为零。
        var result =
                calculateDeclaredOrder(
                        List.of(
                                nativeTrade("BUY", "1", "99", "0.01"),
                                nativeTrade("SELL", "0.99", "100", "0")));
        assertCounts(result, 0, 0, 1);
        equal("0", result.grossProfit());
        equal("0", result.grossLoss());
    }

    @Test
    void baseRebateAtExactBreakEvenIsFlat() {
        // 支付101 quote，实际收到1.01 base；全部卖出收到101 quote，返佣已体现在数量中。
        var result =
                calculateDeclaredOrder(
                        List.of(
                                nativeTrade("BUY", "1", "101", "-0.01"),
                                nativeTrade("SELL", "1.01", "100", "0")));
        assertCounts(result, 0, 0, 1);
        equal("0", result.grossProfit());
        equal("0", result.grossLoss());
    }

    @Test
    void fullCloseDoesNotLeaveGhostBaseForTheNextRound() {
        var result =
                calculateDeclaredOrder(
                        List.of(
                                nativeTrade("BUY", "1", "99", "0.01"),
                                nativeTrade("SELL", "0.99", "100", "0"),
                                nativeTrade("BUY", "1", "100", "0"),
                                nativeTrade("SELL", "1", "100", "0")));
        assertCounts(result, 0, 0, 2);
        equal("0", result.grossProfit());
        equal("0", result.grossLoss());
    }

    @Test
    void twoPartialClosesAllocateActualPrincipalToNetQuantity() {
        // 两次分别支付50.49和48.51，分摊本金各49.5；盈亏各0.99，比例为1。
        var result =
                calculateDeclaredOrder(
                        List.of(
                                nativeTrade("BUY", "1", "99", "0.01"),
                                nativeTrade("SELL", "0.495", "102", "0"),
                                nativeTrade("SELL", "0.495", "98", "0")));
        assertCounts(result, 1, 1, 0);
        equal("0.99", result.grossProfit());
        equal("0.99", result.grossLoss());
        equal("1", result.profitLossRatio());
    }

    @Test
    void mixedBaseCostAndRebateAverageActualCashPrincipal() {
        var result =
                calculateDeclaredOrder(
                        List.of(
                                nativeTrade("BUY", "1", "99", "0.01"),
                                nativeTrade("BUY", "1", "101", "-0.01"),
                                nativeTrade("SELL", "2", "100", "0")));
        assertCounts(result, 0, 0, 1);
        equal("0", result.grossProfit());
        equal("0", result.grossLoss());
    }

    @Test
    void quoteCostAndRebateAffectOnlyActualSaleProceeds() {
        var charged =
                calculateDeclaredOrder(
                        List.of(
                                nativeTrade("BUY", "1", "100", "0"),
                                nativeTrade("SELL", "1", "101", "1")));
        assertCounts(charged, 0, 0, 1);
        var rebated =
                calculateDeclaredOrder(
                        List.of(
                                nativeTrade("BUY", "1", "100", "0"),
                                nativeTrade("SELL", "1", "100", "-0.1")));
        assertCounts(rebated, 1, 0, 0);
        equal("0.1", rebated.grossProfit());
    }

    @Test
    void legacyMissingNativeDataPreservesSeparateFeeAndSlippageContract() {
        var result =
                calculateDeclaredOrder(
                        List.of(
                                legacyTrade("BUY", "99", "1", "2"),
                                legacyTrade("SELL", "105", "1", "1")));
        assertCounts(result, 1, 0, 0);
        equal("1", result.grossProfit());
    }

    @Test
    void nativeFrozenFillDoesNotPayExplanatorySlippageAgain() {
        var trades =
                List.of(
                        nativeTrade("BUY", "1", "99", "0.01", "5"),
                        nativeTrade("SELL", "0.99", "100", "0", "3"));
        var frozen = calculateDeclaredOrder(trades, true);
        assertCounts(frozen, 0, 0, 1);
        equal("0", frozen.grossLoss());
        // 旧未冻结模型明确另外扣除这8 quote；相同原币字段不足以改变这个执行合同。
        var separate = calculateDeclaredOrder(trades, false);
        assertCounts(separate, 0, 1, 0);
        equal("8", separate.grossLoss());
    }

    @Test
    void frozenLegacyScalarFillAlsoConsumesSlippageOnlyOnce() {
        var result =
                calculateDeclaredOrder(
                        List.of(
                                legacyTrade("BUY", "99", "1", "2"),
                                legacyTrade("SELL", "105", "1", "1")),
                        true);
        assertCounts(result, 1, 0, 0);
        equal("4", result.grossProfit());
    }

    @Test
    void nativeOversellAndMissingPriorBuyAreRejectedInsteadOfClipped() {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculateDeclaredOrder(List.of(nativeTrade("SELL", "1", "100", "0"))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        calculateDeclaredOrder(
                                List.of(
                                        nativeTrade("BUY", "1", "99", "0.01"),
                                        nativeTrade("SELL", "1", "100", "0"))));
    }

    /** 第三币费用不能从现金重扣，但胜率必须计入其当笔冻结quote成本及返佣。 */
    @Test
    void discountAssetQuoteCostAndRebateAreAllocatedAcrossPartialCloses() {
        var charged =
                calculateDeclaredOrder(
                        List.of(
                                discountTrade("BUY", "1", "100", "0.1", "20"),
                                discountTrade("SELL", "0.5", "104", "0.01", "30"),
                                discountTrade("SELL", "0.5", "100", "-0.01", "30")),
                        true);
        assertCounts(charged, 1, 1, 0);
        equal("0.7", charged.grossProfit());
        equal("0.7", charged.grossLoss());
        var zero =
                calculateDeclaredOrder(
                        List.of(
                                discountTrade("BUY", "1", "100", "0", "20"),
                                discountTrade("SELL", "1", "100", "0", "30")),
                        true);
        assertCounts(zero, 0, 0, 1);
        var rebate =
                calculateDeclaredOrder(
                        List.of(
                                discountTrade("BUY", "1", "100", "-0.1", "20"),
                                discountTrade("SELL", "1", "100", "-0.01", "30")),
                        true);
        assertCounts(rebate, 1, 0, 0);
        equal("2.3", rebate.grossProfit());
    }

    private static SimTrade discountTrade(
            String side, String quantity, String price, String nativeFee, String conversionPrice) {
        var at = Instant.parse("2026-09-25T00:00:00Z");
        String source =
                "{\"symbol\":\"BNB-USDT\",\"marketType\":\"SPOT\",\"closePrice\":\""
                        + conversionPrice
                        + "\",\"observedAt\":\""
                        + at.minusSeconds(1)
                        + "\",\"visibleAt\":\""
                        + at
                        + "\"}";
        return new SimTrade(
                "discount-" + UUID.randomUUID(),
                "order",
                "run",
                "BTC-USDT",
                side,
                d(quantity),
                d(price),
                d(nativeFee).multiply(d(conversionPrice)).setScale(8, RoundingMode.HALF_UP),
                BigDecimal.ZERO,
                at,
                at,
                at,
                "BNB",
                d(nativeFee),
                "DISCOUNT_ASSET_USED",
                8,
                d(conversionPrice),
                at.minusSeconds(1),
                at,
                DiscountAssetPriceSnapshot.sha256(source),
                source);
    }

    /** 数值夹具按各例明确写出的成交因果顺序赋值；旧未知顺序拒绝另由ResearchEventOrderTest覆盖。 */
    private TradeOutcomeCalculator.Result calculateDeclaredOrder(List<SimTrade> trades) {
        return calculateDeclaredOrder(trades, false);
    }

    private TradeOutcomeCalculator.Result calculateDeclaredOrder(
            List<SimTrade> trades, boolean slipped) {
        var sequenced = new ArrayList<SimTrade>();
        for (int index = 0; index < trades.size(); index++)
            sequenced.add(trades.get(index).withExecutionSequence(index + 1L));
        return calculator.calculate(sequenced, slipped);
    }

    private static SimTrade nativeTrade(
            String side, String quantity, String price, String nativeFee) {
        return nativeTrade(side, quantity, price, nativeFee, "0");
    }

    private static SimTrade nativeTrade(
            String side, String quantity, String price, String nativeFee, String slippage) {
        var fee = d(nativeFee);
        var quote =
                (side.equals("BUY") ? fee.multiply(d(price)) : fee)
                        .setScale(8, RoundingMode.HALF_UP);
        var now = Instant.parse("2026-09-25T00:00:00Z");
        return new SimTrade(
                "trade-" + UUID.randomUUID(),
                "order",
                "run",
                "BTC-USDT",
                side,
                d(quantity),
                d(price),
                quote,
                d(slippage),
                now,
                now,
                now,
                side.equals("BUY") ? "BTC" : "USDT",
                fee,
                "RECEIVED_ASSET",
                8);
    }

    private static SimTrade legacyTrade(String side, String price, String fee, String slippage) {
        var now = Instant.parse("2026-09-25T00:00:00Z");
        return new SimTrade(
                "legacy-" + UUID.randomUUID(),
                "order",
                "run",
                "BTC-USDT",
                side,
                BigDecimal.ONE,
                d(price),
                d(fee),
                d(slippage),
                now,
                now,
                now);
    }

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static void equal(String expected, BigDecimal actual) {
        assertEquals(0, d(expected).compareTo(actual));
    }

    private static void assertCounts(
            TradeOutcomeCalculator.Result result, int winning, int losing, int flat) {
        assertEquals(winning, result.winningTradeCount());
        assertEquals(losing, result.losingTradeCount());
        assertEquals(flat, result.flatTradeCount());
    }
}
