package com.guidinglight.nexusquant.strategy.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.account.domain.fee.SpotCommissionTerms;
import com.guidinglight.nexusquant.account.domain.fee.SpotCommissionTerms.Discount;
import com.guidinglight.nexusquant.account.domain.fee.SpotCommissionTerms.Rates;
import com.guidinglight.nexusquant.strategy.domain.SpotFeeModel.Policy;
import com.guidinglight.nexusquant.strategy.domain.SpotFeeModel.Precision;
import com.guidinglight.nexusquant.strategy.domain.SpotTargetSizer.DirectionalRules;
import com.guidinglight.nexusquant.strategy.domain.SpotTargetSizer.State;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 相同现金下正/零/负base费用只改变成交后持仓；在途现金和卖出可用数量仍有硬边界。 */
class DirectionalSpotTargetSizerTest {
    @Test
    void buyChargesBaseAndKeepsQuotePrincipalSeparate() {
        var result =
                SpotTargetSizer.size(
                        d("0.5"), state("100", "0", "0", "0", "0"), rules("0.01", "0.02"));
        assertTrue(result.executable());
        assertEquals("BTC", result.feeCharge().currency());
        equal("0.50505050", result.quantity());
        equal("0.00505051", result.feeCharge().amount());
        equal("0.49999999", result.settledBaseDelta("BTC-USDT"));
        equal("49.49495000", result.remainingCash());
        equal("0.505051", result.fee());
    }

    @Test
    void fullCashBuyNeverPrecreditsRebate() {
        for (String rate : new String[] {"0.01", "0", "-0.01"}) {
            var result =
                    SpotTargetSizer.size(
                            BigDecimal.ONE, state("100", "0", "0", "0", "0"), rules(rate, "0"));
            assertTrue(result.executable());
            assertTrue(result.quantity().compareTo(d("1")) <= 0);
            assertTrue(result.remainingCash().signum() >= 0);
            equal(
                    rate.equals("0.01") ? "0.99" : rate.equals("0") ? "1" : "0.99999999",
                    result.settledBaseDelta("BTC-USDT"));
        }
    }

    @Test
    void sellUsesSellerQuoteFeeAndCannotSpendPendingSellPosition() {
        var result =
                SpotTargetSizer.size(
                        BigDecimal.ZERO, state("0", "1", "0", "0.4", "0"), rules("0.01", "0.02"));
        assertTrue(result.executable());
        assertEquals("SELL", result.side());
        equal("0.6", result.quantity());
        assertEquals("USDT", result.feeCharge().currency());
        equal("1.2", result.feeCharge().amount());
        equal("58.8", result.remainingCash());
        equal("-0.6", result.settledBaseDelta("BTC-USDT"));
    }

    @Test
    void pendingQuoteReserveStillLimitsBuyWithoutChargingQuoteFee() {
        var result =
                SpotTargetSizer.size(
                        BigDecimal.ONE, state("100", "0", "0.5", "0", "50"), rules("0.01", "0.02"));
        assertTrue(result.executable());
        equal("0.5", result.quantity());
        equal("50", result.remainingCash());
        equal("0.495", result.settledBaseDelta("BTC-USDT"));
        var none =
                SpotTargetSizer.size(
                        BigDecimal.ONE, state("100", "0", "0", "0", "100"), rules("-0.01", "0"));
        assertFalse(none.executable());
        assertEquals("INSUFFICIENT_CASH", none.reason());
    }

    @Test
    void unfrozenDiscountFundingCannotBeEnabledByDirectionalSizing() {
        var model =
                SpotFeeModel.commission(
                        terms("0.01", "0.02"),
                        Policy.DISCOUNT_ASSET_IF_FUNDED,
                        Precision.canonical());
        var rules =
                new DirectionalRules(
                        "BTC-USDT",
                        d("0.00000001"),
                        d("0.01"),
                        d("0.00000001"),
                        d("0.01"),
                        BigDecimal.ZERO,
                        model);
        assertEquals(
                "FEE_DISCOUNT_FUNDING_REQUIRED",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        SpotTargetSizer.size(
                                                d("0.5"), state("100", "0", "0", "0", "0"), rules))
                        .getMessage());
    }

    /** 独立审查原反例：费用单位粗于lot时拒绝，不能让舍入为零的费用扩大净持仓。 */
    @Test
    void coarseFeePrecisionAndSubCanonicalLotAreRejectedBeforeSizing() {
        for (int scale : new int[] {0, 2, 8}) {
            var model =
                    SpotFeeModel.commission(
                            terms("0.1", "0.1"), Policy.RECEIVED_ASSET, new Precision(scale, 8, 8));
            String step = scale == 8 ? "0.000000001" : "0.00000001";
            assertEquals(
                    "FEE_DIRECTIONAL_QUANTITY_PRECISION_MISMATCH",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () ->
                                            new DirectionalRules(
                                                    "BTC-USDT",
                                                    d(step),
                                                    d("0.01"),
                                                    d(step),
                                                    d("0.01"),
                                                    BigDecimal.ZERO,
                                                    model))
                            .getMessage());
        }
    }

    /** 旧净持仓可比当前费用单位更细；反解必须按真实量化后的净量收敛，而不能只靠连续公式。 */
    @Test
    void quantizedFeeCannotOvershootTargetWhenExistingPositionHasFinerPrecision() {
        var state = state("100", "0.000000004", "0", "0", "0");
        var result = SpotTargetSizer.size(d("0.5"), state, rules("0.002", "0.001"));
        assertTrue(result.executable());
        equal("0.50100199", result.quantity());
        equal("0.49999999", result.settledBaseDelta("BTC-USDT"));
        assertTrue(
                state.positionQuantity()
                                .add(result.settledBaseDelta("BTC-USDT"))
                                .compareTo(d("0.5"))
                        <= 0);
    }

    @Test
    void highRateQuantizationPlateauUsesBoundedSearchAndKeepsTargetInvariant() {
        // rate接近1会让逐lot回退很慢；正常精度的二分应在固定上限内找到可行数量。
        var state = state("100000000", "0.000000004", "0", "0", "0");
        var result = SpotTargetSizer.size(d("0.0000005"), state, rules("0.999999", "0"));
        assertTrue(result.executable());
        BigDecimal target = d("0.5");
        assertTrue(
                state.positionQuantity().add(result.settledBaseDelta("BTC-USDT")).compareTo(target)
                        <= 0);
        assertTrue(result.remainingCash().signum() >= 0);
    }

    @Test
    void receivedQuantityNeverExceedsQuantizedTargetAcrossSignedRatesAndFeePrecisions() {
        for (int scale : new int[] {2, 8, 18})
            for (String rate : new String[] {"-0.9", "-0.002", "0", "0.002", "0.1", "0.9"}) {
                BigDecimal step = BigDecimal.ONE.scaleByPowerOfTen(-scale);
                var model =
                        SpotFeeModel.commission(
                                terms(rate, "0"),
                                Policy.RECEIVED_ASSET,
                                new Precision(scale, 8, 8));
                var rules =
                        new DirectionalRules(
                                "BTC-USDT",
                                step,
                                d("0.01"),
                                step,
                                d("0.01"),
                                BigDecimal.ZERO,
                                model);
                var state = state("100", "0.000000004", "0", "0", "0");
                var result = SpotTargetSizer.size(d("0.5"), state, rules);
                assertTrue(result.executable());
                BigDecimal target =
                        state.cash()
                                .add(state.positionQuantity().multiply(state.referencePrice()))
                                .multiply(d("0.5"))
                                .divide(state.referencePrice(), 18, RoundingMode.DOWN)
                                .divideToIntegralValue(step)
                                .multiply(step);
                assertTrue(
                        state.positionQuantity()
                                        .add(result.settledBaseDelta("BTC-USDT"))
                                        .compareTo(target)
                                <= 0,
                        "scale=" + scale + ", rate=" + rate);
                assertTrue(result.remainingCash().signum() >= 0);
            }
    }

    private static State state(
            String cash, String position, String pendingBuy, String pendingSell, String reserved) {
        return new State(
                d(cash), d(position), d(pendingBuy), d(pendingSell), d(reserved), d("100"));
    }

    private static DirectionalRules rules(String buy, String sell) {
        return new DirectionalRules(
                "BTC-USDT",
                d("0.00000001"),
                d("0.01"),
                d("0.00000001"),
                d("0.01"),
                BigDecimal.ZERO,
                SpotFeeModel.commission(
                        terms(buy, sell), Policy.RECEIVED_ASSET, Precision.canonical()));
    }

    private static SpotCommissionTerms terms(String buy, String sell) {
        var zero = new Rates(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        return new SpotCommissionTerms(
                "BTCUSDT",
                new Rates(BigDecimal.ZERO, BigDecimal.ZERO, d(buy), d(sell)),
                zero,
                zero,
                new Discount(true, true, "BNB", d("0.25")),
                "a".repeat(64));
    }

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static void equal(String expected, BigDecimal actual) {
        assertEquals(
                0, d(expected).compareTo(actual), "expected=" + expected + ", actual=" + actual);
    }
}
