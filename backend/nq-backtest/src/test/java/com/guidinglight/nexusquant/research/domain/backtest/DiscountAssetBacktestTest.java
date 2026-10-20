package com.guidinglight.nexusquant.research.domain.backtest;

import static org.junit.jupiter.api.Assertions.*;

import com.guidinglight.nexusquant.account.domain.fee.SpotCommissionTerms;
import com.guidinglight.nexusquant.account.domain.fee.SpotCommissionTerms.Discount;
import com.guidinglight.nexusquant.account.domain.fee.SpotCommissionTerms.Rates;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.BarProvenance;
import com.guidinglight.nexusquant.marketdata.domain.DiscountAssetPriceSnapshot;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataDataset;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataDatasetStatus;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataQualityStatus;
import com.guidinglight.nexusquant.marketdata.domain.PriceEventTimes;
import com.guidinglight.nexusquant.strategy.domain.SpotFeeModel;
import com.guidinglight.nexusquant.strategy.domain.SpotFeeModel.Policy;
import com.guidinglight.nexusquant.strategy.domain.SpotFeeModel.Precision;
import com.guidinglight.nexusquant.strategy.domain.SpotTargetSizer;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/** 优惠资产余额与现金分别消费；价格跨事件变化时，策略和基准复用同一初始预算与来源。 */
class DiscountAssetBacktestTest {
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void initialBudgetIsConservedAndRoundingTailStaysInCash() {
        var context = context(prices(), "0.2");
        equal("2", context.discountBalance());
        equal("80", context.cashBalance());
        equal("100", context.cashBalance().add(context.discountQuoteValueAt(START)));
        var fraction = context(prices(), "0.12345678");
        equal("100", fraction.cashBalance().add(fraction.discountQuoteValueAt(START)));
        assertThrows(IllegalArgumentException.class, () -> context(prices(), "0.123456789"));
        assertThrows(IllegalArgumentException.class, () -> context(prices(), "1"));
        var tiny = new BacktestExecutionContext("tiny", "BTC-USDT", d("0.01"));
        assertThrows(
                IllegalArgumentException.class,
                () -> tiny.initializeDiscountAssets(prices(), d("0.00000001"), START));
        assertThrows(IllegalArgumentException.class, () -> context(prices(), "1.01"));
    }

    @Test
    void signedDiscountFeesSettleInOriginalCurrencyWithoutSecondCashDebit() {
        for (String rate : new String[] {"0.1", "0", "-0.1"}) {
            var context = context(prices(), "0.2");
            var result =
                    SpotTargetSizer.size(
                            BigDecimal.ONE,
                            state(context),
                            rules(rate),
                            context.discountFundingAt(START.plusSeconds(60)),
                            START.plusSeconds(60),
                            context.discountQuoteValueAt(START.plusSeconds(60)));
            var trade = trade(context, result);
            context.applyAtSlippedFillPrice(trade, null);
            equal("0", context.cashBalance());
            equal(
                    rate.equals("0.1") ? "1.9" : rate.equals("0") ? "2" : "2.1",
                    context.discountBalance());
            equal(rate.equals("0.1") ? "2" : rate.equals("0") ? "0" : "-2", context.totalFee());
            equal("0.8", trade.settledBaseDelta());
            equal("0", trade.cashFeeAmount());
            assertEquals(prices().points().size(), context.discountPrices().points().size());
        }
    }

    @Test
    void insufficientAssetFallsBackAndDoesNotSpendItsRemainingBalance() {
        var context = context(prices(), "0.01");
        var result =
                SpotTargetSizer.size(
                        BigDecimal.ONE,
                        state(context),
                        rules("0.1"),
                        context.discountFundingAt(START.plusSeconds(60)),
                        START.plusSeconds(60),
                        context.discountQuoteValueAt(START.plusSeconds(60)));
        assertEquals("DISCOUNT_ASSET_INSUFFICIENT_FALLBACK", result.feeCharge().paymentStatus());
        context.applyAtSlippedFillPrice(trade(context, result), null);
        equal("0.1", context.discountBalance());
        equal("0", context.cashBalance());
        equal("0.891", result.settledBaseDelta("BTC-USDT"));
    }

    @Test
    void benchmarkUsesSameBudgetAndVisibleConversionAtEntryAndEnd() {
        var prices = prices();
        var bars = List.of(bar("BTC-USDT", 0, "100", "110"), bar("BTC-USDT", 1, "100", "120"));
        var result = BuyAndHoldCalculator.calculate(bars, d("100"), rules("0.1"), prices, d("0.2"));
        assertEquals("AVAILABLE", result.status());
        equal("0.8", result.quantity());
        equal("153", result.finalEquity());
        equal("0.53", result.benchmarkReturn());
        equal("80", result.discountAsset().initialCash());
        equal("2", result.discountAsset().initialUnits());
        equal("1.9", result.discountAsset().remainingUnits());
        equal("10", result.discountAsset().initialConversion().quotePerAsset());
        equal("30", result.discountAsset().finalConversion().quotePerAsset());
        assertEquals(prices.sourceSha256(), result.discountAsset().priceSnapshotSha256());
    }

    @Test
    void futureStaleMissingConversionAndWrongCurrencyFactsAreRejected() {
        var context = context(prices(), "0.2");
        assertThrows(
                IllegalArgumentException.class,
                () -> context.discountQuoteValueAt(START.plusSeconds(120)));
        var at = START.plusSeconds(60);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SimTrade(
                                "t",
                                "o",
                                "r",
                                "BTC-USDT",
                                "BUY",
                                d("0.8"),
                                d("100"),
                                d("2"),
                                BigDecimal.ZERO,
                                at,
                                at,
                                at,
                                "BNB",
                                d("0.1"),
                                "DISCOUNT_ASSET_USED",
                                8));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SimTrade(
                                "t",
                                "o",
                                "r",
                                "BTC-USDT",
                                "BUY",
                                d("0.8"),
                                d("100"),
                                d("2"),
                                BigDecimal.ZERO,
                                at,
                                at,
                                at,
                                "BNB",
                                d("0.1"),
                                "DISCOUNT_ASSET_USED",
                                8,
                                d("20"),
                                at.minusSeconds(301),
                                at.minusSeconds(300),
                                "a".repeat(64),
                                "{}"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SimTrade(
                                "t",
                                "o",
                                "r",
                                "BTC-USDT",
                                "BUY",
                                d("0.8"),
                                d("100"),
                                d("2"),
                                BigDecimal.ZERO,
                                at,
                                at,
                                at,
                                "BNB",
                                d("0.1"),
                                "DISCOUNT_ASSET_USED",
                                8,
                                d("20"),
                                at,
                                at.plusSeconds(1),
                                "a".repeat(64),
                                "{}"));
    }

    private static BacktestExecutionContext context(
            DiscountAssetPriceSnapshot prices, String fraction) {
        var context = new BacktestExecutionContext("run", "BTC-USDT", d("100"));
        context.initializeDiscountAssets(prices, d(fraction), START);
        return context;
    }

    private static SpotTargetSizer.State state(BacktestExecutionContext context) {
        return new SpotTargetSizer.State(
                context.cashBalance(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                d("100"));
    }

    private static SimTrade trade(BacktestExecutionContext context, SpotTargetSizer.Result result) {
        Instant at = START.plusSeconds(60);
        var point =
                "DISCOUNT_ASSET_USED".equals(result.feeCharge().paymentStatus())
                        ? context.discountPriceAt(at)
                        : null;
        return new SimTrade(
                "t",
                "o",
                "run",
                "BTC-USDT",
                result.side(),
                result.quantity(),
                result.fillPrice(),
                result.fee(),
                result.slippageCost(),
                at,
                at,
                at,
                result.feeCharge().currency(),
                result.feeCharge().amount(),
                result.feeCharge().paymentStatus(),
                8,
                point == null ? null : point.quotePerAsset(),
                point == null ? null : point.observedAt(),
                point == null ? null : point.visibleAt(),
                point == null ? null : point.sourceSha256(),
                point == null ? null : point.sourceJson());
    }

    private static SpotTargetSizer.DirectionalRules rules(String rate) {
        var zero = new Rates(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        var terms =
                new SpotCommissionTerms(
                        "BTCUSDT",
                        new Rates(BigDecimal.ZERO, d(rate), BigDecimal.ZERO, BigDecimal.ZERO),
                        zero,
                        zero,
                        new Discount(true, true, "BNB", d("0.25")),
                        "a".repeat(64));
        return new SpotTargetSizer.DirectionalRules(
                "BTC-USDT",
                d("0.00000001"),
                d("0.01"),
                d("0.00000001"),
                d("0.01"),
                BigDecimal.ZERO,
                SpotFeeModel.commission(
                        terms, Policy.DISCOUNT_ASSET_IF_FUNDED, Precision.canonical()));
    }

    static DiscountAssetPriceSnapshot prices() {
        var bars = new ArrayList<HistoricalBar>();
        for (int minute = -5; minute <= 1; minute++)
            bars.add(
                    bar(
                            "BNB-USDT",
                            minute,
                            minute < 0 ? "10" : minute == 0 ? "20" : "30",
                            minute < 0 ? "10" : minute == 0 ? "20" : "30"));
        Instant end = START.plusSeconds(119);
        var dataset =
                new MarketdataDataset(
                        UUID.randomUUID(),
                        "discount-fixture",
                        "BINANCE",
                        "SPOT",
                        "BNB-USDT",
                        BarInterval.ONE_MINUTE,
                        START.minusSeconds(300),
                        end,
                        MarketdataDatasetStatus.READY,
                        MarketdataQualityStatus.OK,
                        bars.size(),
                        0,
                        "marketdata_bars",
                        "1",
                        end,
                        end,
                        "{}");
        var provenance = new LinkedHashMap<Instant, BarProvenance>();
        for (int index = 0; index < bars.size(); index++) {
            var bar = bars.get(index);
            provenance.put(
                    bar.openTime(),
                    new BarProvenance(
                            bar.openTime(),
                            "FIXTURE_SYNC",
                            bar.availableAt(),
                            (long) index + 1,
                            "a".repeat(64),
                            PriceEventTimes.unavailable("NOT_PROVIDED")));
        }
        return DiscountAssetPriceSnapshot.capture(
                1, dataset, "BINANCE", "BNB", "USDT", START, end, end, bars, provenance);
    }

    private static HistoricalBar bar(String symbol, int minute, String open, String close) {
        Instant at = START.plusSeconds(minute * 60L);
        return new HistoricalBar(
                "BINANCE",
                symbol,
                BarInterval.ONE_MINUTE,
                at,
                at.plusSeconds(59),
                d(open),
                d(open).max(d(close)),
                d(open).min(d(close)),
                d(close),
                BigDecimal.ONE);
    }

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static void equal(String expected, BigDecimal actual) {
        assertEquals(0, d(expected).compareTo(actual), "actual=" + actual);
    }
}
