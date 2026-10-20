package com.guidinglight.nexusquant.strategy.domain;

import static org.junit.jupiter.api.Assertions.*;

import com.guidinglight.nexusquant.account.domain.fee.SpotCommissionTerms;
import com.guidinglight.nexusquant.account.domain.fee.SpotCommissionTerms.Discount;
import com.guidinglight.nexusquant.account.domain.fee.SpotCommissionTerms.Rates;
import com.guidinglight.nexusquant.strategy.domain.SpotFeeModel.DiscountFunding;
import com.guidinglight.nexusquant.strategy.domain.SpotFeeModel.Policy;
import com.guidinglight.nexusquant.strategy.domain.SpotFeeModel.Precision;
import com.guidinglight.nexusquant.strategy.domain.SpotTargetSizer.DirectionalRules;
import com.guidinglight.nexusquant.strategy.domain.SpotTargetSizer.State;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/** 足额阈值不是单调净量函数；以实际收费币与净持仓、现金不变量验证候选选择。 */
class DiscountSpotTargetSizerTest {
    private static final Instant AT = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void fundedFeeKeepsFullNetBaseAndDoesNotDebitQuoteTwice() {
        var result = size("0.5", "100", "1", "0.1");
        assertTrue(result.executable());
        assertEquals("BNB", result.feeCharge().currency());
        equal("0.5", result.quantity());
        equal("0.0125", result.feeCharge().amount());
        equal("1.25", result.fee());
        equal("50", result.remainingCash());
        equal("0.5", result.settledBaseDelta("BTC-USDT"));
    }

    @Test
    void equalityIsFundedAndOneUnitShortFallsBackWithoutOvershootingTarget() {
        var exact = size("0.5", "100", "0.0125", "0.1");
        assertEquals("DISCOUNT_ASSET_USED", exact.feeCharge().paymentStatus());
        equal("0.5", exact.quantity());
        var shortOne = size("0.5", "100", "0.01249999", "0.1");
        assertEquals("DISCOUNT_ASSET_INSUFFICIENT_FALLBACK", shortOne.feeCharge().paymentStatus());
        assertTrue(shortOne.settledBaseDelta("BTC-USDT").compareTo(d("0.5")) <= 0);
        assertTrue(shortOne.quantity().compareTo(d("0.5")) > 0);
    }

    @Test
    void rebateCannotCreatePrincipalAndThirdAssetEquityDoesNotBecomeCash() {
        var result =
                SpotTargetSizer.size(
                        BigDecimal.ONE, state("80"), rules("-0.1"), funding("2"), AT, d("20"));
        assertTrue(result.executable());
        equal("0.8", result.quantity());
        equal("0", result.remainingCash());
        assertEquals("BNB", result.feeCharge().currency());
        equal("-0.02", result.feeCharge().amount());
        var none =
                SpotTargetSizer.size(
                        BigDecimal.ONE, state("0"), rules("-0.1"), funding("2"), AT, d("200"));
        assertFalse(none.executable());
        assertEquals("INSUFFICIENT_CASH", none.reason());
    }

    @Test
    void staleFutureAndPrimaryCurrencyFundingAreRejected() {
        for (Instant observed : new Instant[] {AT.plusSeconds(1), AT.minusSeconds(301)}) {
            var funding =
                    new DiscountFunding(
                            "BNB",
                            "USDT",
                            d("100"),
                            d("1"),
                            observed,
                            Duration.ofMinutes(5),
                            "a".repeat(64));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            SpotTargetSizer.size(
                                    d("0.5"),
                                    state("100"),
                                    rules("0.1"),
                                    funding,
                                    AT,
                                    BigDecimal.ZERO));
        }
        var primary =
                new DiscountFunding(
                        "BTC", "USDT", d("100"), d("1"), AT, Duration.ofMinutes(5), "a".repeat(64));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SpotTargetSizer.size(
                                d("0.5"),
                                state("100"),
                                rules("0.1"),
                                primary,
                                AT,
                                BigDecimal.ZERO));
    }

    @Test
    void signedRatesAndThresholdCrossingPreserveNetTargetAndCash() {
        for (String rate : new String[] {"-0.9", "-0.1", "-0.001", "0", "0.001", "0.1", "0.9"})
            for (String balance :
                    new String[] {"0", "0.000001", "0.000125", "0.01249999", "0.0125", "1"})
                for (String cash : new String[] {"1", "49", "100"}) {
                    var result = size("0.5", cash, balance, rate);
                    if (result.executable()) {
                        assertTrue(
                                result.settledBaseDelta("BTC-USDT")
                                                .compareTo(
                                                        d(cash).multiply(d("0.5")).divide(d("100")))
                                        <= 0,
                                "rate=" + rate + ",balance=" + balance + ",cash=" + cash);
                        assertTrue(result.remainingCash().signum() >= 0);
                        if ("BNB".equals(result.feeCharge().currency()))
                            assertTrue(
                                    result.feeCharge().positiveReserve().compareTo(d(balance))
                                            <= 0);
                    }
                }
    }

    private static SpotTargetSizer.Result size(
            String target, String cash, String balance, String rate) {
        return SpotTargetSizer.size(
                d(target), state(cash), rules(rate), funding(balance), AT, BigDecimal.ZERO);
    }

    private static State state(String cash) {
        return new State(
                d(cash),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                d("100"));
    }

    private static DirectionalRules rules(String rate) {
        var zero = new Rates(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        var terms =
                new SpotCommissionTerms(
                        "BTCUSDT",
                        new Rates(BigDecimal.ZERO, d(rate), BigDecimal.ZERO, BigDecimal.ZERO),
                        zero,
                        zero,
                        new Discount(true, true, "BNB", d("0.25")),
                        "a".repeat(64));
        return new DirectionalRules(
                "BTC-USDT",
                d("0.00000001"),
                d("0.01"),
                d("0.00000001"),
                d("0.01"),
                BigDecimal.ZERO,
                SpotFeeModel.commission(
                        terms, Policy.DISCOUNT_ASSET_IF_FUNDED, Precision.canonical()));
    }

    private static DiscountFunding funding(String balance) {
        return new DiscountFunding(
                "BNB", "USDT", d("100"), d(balance), AT, Duration.ofMinutes(5), "a".repeat(64));
    }

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static void equal(String expected, BigDecimal actual) {
        assertEquals(0, d(expected).compareTo(actual));
    }
}
