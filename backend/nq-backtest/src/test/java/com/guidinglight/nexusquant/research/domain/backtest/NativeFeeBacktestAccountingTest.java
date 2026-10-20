package com.guidinglight.nexusquant.research.domain.backtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/** 原币支付与quote估值分别断言；base收费不重复扣quote，返佣只在成交后增加净base。 */
class NativeFeeBacktestAccountingTest {
    private static final Instant AT = Instant.parse("2026-10-14T00:00:00Z");

    @Test
    void signedBaseFeeChangesOnlySettledBaseAndQuoteCostSummary() {
        for (String signed : new String[] {"0.001", "0", "-0.001"}) {
            BigDecimal fee = d(signed);
            var trade = nativeTrade("BUY", "BTC", fee, fee.multiply(d("100")));
            var context = new BacktestExecutionContext("run", "BTC-USDT", d("100"));
            context.applyAtSlippedFillPrice(trade, null);
            equal("0", context.cashBalance());
            equal(fee.multiply(d("100")).toPlainString(), context.totalFee());
            equal(d("1").subtract(fee).toPlainString(), trade.settledBaseDelta());
            equal("0", trade.cashFeeAmount());
        }
    }

    @Test
    void signedQuoteFeeChangesCashAfterSellWithoutExtraBaseDebit() {
        for (String signed : new String[] {"0.1", "0", "-0.1"}) {
            var context = new BacktestExecutionContext("run", "BTC-USDT", d("100"));
            context.applyAtSlippedFillPrice(legacy("BUY", d("0")), null);
            var trade = nativeTrade("SELL", "USDT", d(signed), d(signed));
            context.applyAtSlippedFillPrice(trade, null);
            equal(d("100").subtract(d(signed)).toPlainString(), context.cashBalance());
            equal(signed, context.totalFee());
            equal("-1", trade.settledBaseDelta());
        }
    }

    @Test
    void oldTradeHasNoInventedNativeAssetAndKeepsScalarCashAccounting() {
        var trade = legacy("BUY", d("0.1"));
        assertNull(trade.feeCurrency());
        assertNull(trade.feeNativeAmount());
        var context = new BacktestExecutionContext("run", "BTC-USDT", d("101"));
        context.applyAtSlippedFillPrice(trade, null);
        equal("0.9", context.cashBalance());
        equal("1", trade.settledBaseDelta());
    }

    @Test
    void incompleteWrongCurrencyAndWrongQuoteValuationAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> nativeTrade("BUY", "USDT", d("0.001"), d("0.1")));
        assertThrows(
                IllegalArgumentException.class,
                () -> nativeTrade("BUY", "BTC", d("0.001"), d("0.001")));
        assertThrows(
                IllegalArgumentException.class,
                () -> nativeTrade("SELL", "BTC", d("0.1"), d("0.1")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SimTrade(
                                "trade",
                                "order",
                                "run",
                                "BTC-USDT",
                                "BUY",
                                d("1"),
                                d("100"),
                                d("0.1"),
                                d("0"),
                                AT,
                                AT,
                                AT,
                                "BTC",
                                null,
                                "RECEIVED_ASSET",
                                8));
    }

    @Test
    void fullBaseChargeCannotCreateEmptyOrNegativeHeldPosition() {
        assertThrows(
                IllegalArgumentException.class, () -> nativeTrade("BUY", "BTC", d("1"), d("100")));
        assertThrows(
                IllegalArgumentException.class, () -> nativeTrade("BUY", "BTC", d("2"), d("200")));
    }

    private static SimTrade nativeTrade(
            String side, String currency, BigDecimal fee, BigDecimal quoteValue) {
        return new SimTrade(
                "trade",
                "order",
                "run",
                "BTC-USDT",
                side,
                d("1"),
                d("100"),
                quoteValue.setScale(8, RoundingMode.HALF_UP),
                d("0"),
                AT,
                AT,
                AT,
                currency,
                fee,
                "RECEIVED_ASSET",
                8);
    }

    private static SimTrade legacy(String side, BigDecimal fee) {
        return new SimTrade(
                "trade",
                "order",
                "run",
                "BTC-USDT",
                side,
                d("1"),
                d("100"),
                fee,
                d("0"),
                AT,
                AT,
                AT);
    }

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static void equal(String expected, BigDecimal actual) {
        assertEquals(0, d(expected).compareTo(actual));
    }
}
