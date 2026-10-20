package com.guidinglight.nexusquant.strategy.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guidinglight.nexusquant.account.domain.fee.SpotCommissionTerms;
import com.guidinglight.nexusquant.account.domain.fee.SpotCommissionTerms.Discount;
import com.guidinglight.nexusquant.account.domain.fee.SpotCommissionTerms.LiquidityRole;
import com.guidinglight.nexusquant.account.domain.fee.SpotCommissionTerms.Rates;
import com.guidinglight.nexusquant.account.domain.fee.SpotCommissionTerms.Side;
import com.guidinglight.nexusquant.strategy.domain.SpotFeeModel.DiscountFunding;
import com.guidinglight.nexusquant.strategy.domain.SpotFeeModel.Policy;
import com.guidinglight.nexusquant.strategy.domain.SpotFeeModel.Precision;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/** 数值oracle分别验证原币扣款、quote估值和本金预算，防止quote费率替换base扣费。 */
class SpotFeeModelTest {
    private static final Instant AT = Instant.parse("2026-10-14T00:00:00Z");
    private static final String SHA = "a".repeat(64);
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    @Test
    void receivedAssetBuyAndSellUseDistinctSideAndLiquidityComponents() {
        var model =
                model(
                        "0.001",
                        "0.002",
                        "0.0002",
                        "0.0003",
                        "0.00001",
                        "0.00002",
                        Policy.RECEIVED_ASSET);
        var buy =
                model.charge("BTC-USDT", Side.BUY, LiquidityRole.TAKER, d("2"), d("100"), null, AT);
        assertEquals("BTC", buy.currency());
        equal("0.00446", buy.amount());
        equal("0.446", buy.quoteValue());
        equal("0", buy.debit("USDT"));
        equal("0.00446", buy.debit("BTC"));
        var sell =
                model.charge(
                        "BTC-USDT", Side.SELL, LiquidityRole.TAKER, d("2"), d("100"), null, AT);
        assertEquals("USDT", sell.currency());
        equal("0.466", sell.amount());
        equal("0.466", sell.quoteValue());
        var maker =
                model.charge("BTC-USDT", Side.BUY, LiquidityRole.MAKER, d("2"), d("100"), null, AT);
        equal("0.00246", maker.amount());
        equal("200", model.quoteReserve(d("2"), d("100")));
    }

    @Test
    void signedAndZeroFeesKeepOriginalAssetAndNeverReducePrincipalReserve() {
        for (String rate : new String[] {"0.001", "0", "-0.001"}) {
            var nativeModel = model(rate, rate, "0", "0", "0", "0", Policy.RECEIVED_ASSET);
            var buy =
                    nativeModel.charge(
                            "BTC-USDT", Side.BUY, LiquidityRole.TAKER, d("1"), d("100"), null, AT);
            equal(rate, buy.amount());
            equal(d(rate).multiply(d("100")).toPlainString(), buy.quoteValue());
            equal("100", nativeModel.quoteReserve(d("1"), d("100")));
            var scalar = SpotFeeModel.quoteNotional(d(rate), Precision.canonical());
            var fee =
                    scalar.charge(
                            "BTC-USDT", Side.BUY, LiquidityRole.TAKER, d("1"), d("100"), null, AT);
            assertEquals("USDT", fee.currency());
            equal(d(rate).multiply(d("100")).toPlainString(), fee.amount());
            equal(rate.equals("0.001") ? "100.1" : "100", scalar.quoteReserve(d("1"), d("100")));
        }
    }

    @Test
    void fundedDiscountConvertsIntoPaymentAssetAndDiscountsOnlyStandard() {
        var model =
                model(
                        "0.001",
                        "0.002",
                        "0.0002",
                        "0.0003",
                        "0.00001",
                        "0.00002",
                        Policy.DISCOUNT_ASSET_IF_FUNDED);
        var buy =
                model.charge(
                        "BTC-USDT",
                        Side.BUY,
                        LiquidityRole.TAKER,
                        d("2"),
                        d("100"),
                        funded("1", AT),
                        AT);
        assertEquals("BNB", buy.currency());
        assertEquals("DISCOUNT_ASSET_USED", buy.paymentStatus());
        // (0.002+0.0002)*0.25 + 0.00001 + 0.00002 = 0.00058；200*0.00058/400 = 0.00029BNB。
        equal("0.00029", buy.amount());
        equal("0.116", buy.quoteValue());
        equal("0", buy.debit("USDT"));
        assertEquals(SHA, buy.fundingSourceSha256());
    }

    @Test
    void insufficientDiscountDoesNotUseDiscountedQuoteFee() {
        var model =
                model(
                        "0.001",
                        "0.002",
                        "0.0002",
                        "0.0003",
                        "0.00001",
                        "0.00002",
                        Policy.DISCOUNT_ASSET_IF_FUNDED);
        var buy =
                model.charge(
                        "BTC-USDT",
                        Side.BUY,
                        LiquidityRole.TAKER,
                        d("2"),
                        d("100"),
                        funded("0.00028999", AT),
                        AT);
        assertEquals("BTC", buy.currency());
        equal("0.00446", buy.amount());
        equal("0.446", buy.quoteValue());
        assertEquals("DISCOUNT_ASSET_INSUFFICIENT_FALLBACK", buy.paymentStatus());
        var exact =
                model.charge(
                        "BTC-USDT",
                        Side.BUY,
                        LiquidityRole.TAKER,
                        d("2"),
                        d("100"),
                        funded("0.00029", AT),
                        AT);
        assertEquals("BNB", exact.currency());
    }

    @Test
    void switchesDoNotCreateFundingAndUnknownOrFutureConversionIsRejected() {
        var model = model("0", "0.002", "0", "0", "0", "0", Policy.DISCOUNT_ASSET_IF_FUNDED);
        reason(
                "FEE_DISCOUNT_FUNDING_REQUIRED",
                () ->
                        model.charge(
                                "BTC-USDT",
                                Side.BUY,
                                LiquidityRole.TAKER,
                                d("1"),
                                d("100"),
                                null,
                                AT));
        reason(
                "FEE_DISCOUNT_PRICE_UNAVAILABLE",
                () ->
                        model.charge(
                                "BTC-USDT",
                                Side.BUY,
                                LiquidityRole.TAKER,
                                d("1"),
                                d("100"),
                                funded("1", AT.plusNanos(1)),
                                AT));
        reason(
                "FEE_DISCOUNT_PRICE_UNAVAILABLE",
                () ->
                        model.charge(
                                "BTC-USDT",
                                Side.BUY,
                                LiquidityRole.TAKER,
                                d("1"),
                                d("100"),
                                funded("1", AT.minusSeconds(31)),
                                AT));
        var wrong =
                new DiscountFunding(
                        "OTHER", "USDT", d("400"), d("1"), AT, Duration.ofSeconds(30), SHA);
        reason(
                "FEE_DISCOUNT_FUNDING_SCOPE_MISMATCH",
                () ->
                        model.charge(
                                "BTC-USDT",
                                Side.BUY,
                                LiquidityRole.TAKER,
                                d("1"),
                                d("100"),
                                wrong,
                                AT));
    }

    @Test
    void canonicalFeeRoundingPreservesSignedHalfUpAndReserveRoundsPositiveCostUp() {
        var model = SpotFeeModel.quoteNotional(d("0.000000005"), Precision.canonical());
        equal(
                "0.00000001",
                model.charge("BTC-USDT", Side.BUY, LiquidityRole.TAKER, d("1"), d("1"), null, AT)
                        .amount());
        equal("1.00000001", model.quoteReserve(d("1"), d("1")));
        var negative = SpotFeeModel.quoteNotional(d("-0.000000005"), Precision.canonical());
        equal(
                "-0.00000001",
                negative.charge("BTC-USDT", Side.BUY, LiquidityRole.TAKER, d("1"), d("1"), null, AT)
                        .amount());
        equal("1", negative.quoteReserve(d("1"), d("1")));
    }

    @Test
    void invalidCompoundRatesPrecisionAndCurrencyFailBeforeConsumption() {
        reason(
                "FEE_MODEL_RATE_INVALID",
                () -> model("0.8", "0.8", "0.2", "0", "0", "0", Policy.RECEIVED_ASSET));
        reason("FEE_PRECISION_INVALID", () -> new Precision(19, 8, 8));
        var model = model("0.001", "0.001", "0", "0", "0", "0", Policy.RECEIVED_ASSET);
        reason(
                "FEE_MODEL_SYMBOL_MISMATCH",
                () ->
                        model.charge(
                                "ETH-USDT",
                                Side.BUY,
                                LiquidityRole.TAKER,
                                d("1"),
                                d("100"),
                                null,
                                AT));
        reason(
                "FEE_MODEL_SYMBOL_INVALID",
                () ->
                        model.charge(
                                "BTC-BTC",
                                Side.BUY,
                                LiquidityRole.TAKER,
                                d("1"),
                                d("100"),
                                null,
                                AT));
        reason(
                "FEE_TRADE_VALUE_INVALID",
                () ->
                        model.charge(
                                "BTC-USDT",
                                Side.BUY,
                                LiquidityRole.TAKER,
                                ZERO,
                                d("100"),
                                null,
                                AT));
    }

    /** 真实PG价格的无信息尾零不改变数值；真正超精度的小数和超大指数仍失败关闭。 */
    @Test
    void databaseTrailingZerosAreAcceptedWithoutRelaxingEffectivePrecisionBounds() {
        var model = model("0.001", "0.001", "0", "0", "0", "0", Policy.RECEIVED_ASSET);
        var fee =
                model.charge(
                        "BTC-USDT",
                        Side.BUY,
                        LiquidityRole.TAKER,
                        d("1.000000000000000000000000000000000000"),
                        d("81.000000000000000000000000000000000000"),
                        null,
                        AT);
        equal("0.001", fee.amount());
        equal("0.081", fee.quoteValue());
        equal(
                "81",
                model.quoteReserve(
                        d("1.000000000000000000000000000000000000"),
                        d("81.000000000000000000000000000000000000")));
        reason(
                "FEE_TRADE_VALUE_INVALID",
                () ->
                        model.charge(
                                "BTC-USDT",
                                Side.BUY,
                                LiquidityRole.TAKER,
                                d("1.0000000000000000001"),
                                d("81"),
                                null,
                                AT));
        reason("FEE_TRADE_VALUE_INVALID", () -> model.quoteReserve(d("1e1000"), d("81")));
    }

    private static SpotFeeModel model(
            String maker,
            String taker,
            String buyer,
            String seller,
            String tax,
            String special,
            Policy policy) {
        var terms =
                new SpotCommissionTerms(
                        "BTCUSDT",
                        new Rates(d(maker), d(taker), d(buyer), d(seller)),
                        new Rates(d(tax), d(tax), ZERO, ZERO),
                        new Rates(d(special), d(special), ZERO, ZERO),
                        new Discount(true, true, "BNB", d("0.25")),
                        SHA);
        return SpotFeeModel.commission(terms, policy, Precision.canonical());
    }

    private static DiscountFunding funded(String balance, Instant time) {
        return new DiscountFunding(
                "BNB", "USDT", d("400"), d(balance), time, Duration.ofSeconds(30), SHA);
    }

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static void equal(String expected, BigDecimal actual) {
        assertEquals(
                0, d(expected).compareTo(actual), "expected=" + expected + ", actual=" + actual);
    }

    private static void reason(String code, Runnable action) {
        assertEquals(code, assertThrows(IllegalArgumentException.class, action::run).getMessage());
    }
}
