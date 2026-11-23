package com.guidinglight.nexusquant.research.domain.backtest;

import com.guidinglight.nexusquant.marketdata.domain.DiscountAssetPriceSnapshot;
import com.guidinglight.nexusquant.marketdata.domain.DiscountAssetPriceSnapshot.Point;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.strategy.domain.SpotTargetSizer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.function.Function;

/** 沿用冻结回测的下一可交易开盘、成本和数量规则；最后收盘仅估值，不收虚构退出费用。 */
public final class BuyAndHoldCalculator {
    private BuyAndHoldCalculator() {}

    public static ResearchRunFacts.Benchmark calculate(
            List<HistoricalBar> bars, BigDecimal capital, SpotTargetSizer.Rules rules) {
        return calculate(
                bars,
                capital,
                entry ->
                        SpotTargetSizer.size(
                                BigDecimal.ONE,
                                new SpotTargetSizer.State(
                                        capital,
                                        BigDecimal.ZERO,
                                        BigDecimal.ZERO,
                                        BigDecimal.ZERO,
                                        BigDecimal.ZERO,
                                        entry.openPrice()),
                                rules));
    }

    /** 方向基准沿用同一费用与数量合同；估值使用成交后净base，不能把base费用再扣quote现金。 */
    public static ResearchRunFacts.Benchmark calculate(
            List<HistoricalBar> bars, BigDecimal capital, SpotTargetSizer.DirectionalRules rules) {
        return calculate(
                bars,
                capital,
                entry ->
                        SpotTargetSizer.size(
                                BigDecimal.ONE,
                                new SpotTargetSizer.State(
                                        capital,
                                        BigDecimal.ZERO,
                                        BigDecimal.ZERO,
                                        BigDecimal.ZERO,
                                        BigDecimal.ZERO,
                                        entry.openPrice()),
                                rules));
    }

    /** 基准和策略从同一总预算及冻结series拆分；末尾保留优惠币真实剩余价值，不虚构退出成交。 */
    public static ResearchRunFacts.Benchmark calculate(
            List<HistoricalBar> bars,
            BigDecimal capital,
            SpotTargetSizer.DirectionalRules rules,
            DiscountAssetPriceSnapshot prices,
            BigDecimal fraction) {
        if (bars.size() < 2)
            return unavailable("INSUFFICIENT_DATA", "NO_LATER_TRADABLE_EVENT", bars, capital);
        HistoricalBar first = bars.getFirst();
        HistoricalBar entry =
                bars.stream()
                        .skip(1)
                        .filter(bar -> bar.openTime().isAfter(first.availableAt()))
                        .findFirst()
                        .orElse(null);
        if (entry == null)
            return unavailable("INSUFFICIENT_DATA", "NO_LATER_TRADABLE_EVENT", bars, capital);
        BacktestExecutionContext context =
                new BacktestExecutionContext("BUY_AND_HOLD", rules.symbol(), capital);
        context.initializeDiscountAssets(prices, fraction, first.openTime());
        var sizing =
                SpotTargetSizer.size(
                        BigDecimal.ONE,
                        new SpotTargetSizer.State(
                                context.cashBalance(),
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                entry.openPrice()),
                        rules,
                        context.discountFundingAt(entry.openTime()),
                        entry.openTime(),
                        context.discountQuoteValueAt(entry.openTime()));
        if (!sizing.executable())
            return unavailable("NOT_AVAILABLE", sizing.reason(), bars, capital);
        Point conversion =
                "DISCOUNT_ASSET_USED".equals(sizing.feeCharge().paymentStatus())
                        ? prices.priceAt(entry.openTime())
                        : null;
        SimTrade trade =
                new SimTrade(
                        "benchmark-trade",
                        "benchmark-order",
                        "BUY_AND_HOLD",
                        rules.symbol(),
                        "BUY",
                        sizing.quantity(),
                        sizing.fillPrice(),
                        sizing.fee(),
                        sizing.slippageCost(),
                        entry.openTime(),
                        entry.openTime(),
                        entry.openTime(),
                        sizing.feeCharge().currency(),
                        sizing.feeCharge().amount(),
                        sizing.feeCharge().paymentStatus(),
                        rules.feeModel().precision().quoteScale(),
                        conversion == null ? null : conversion.quotePerAsset(),
                        conversion == null ? null : conversion.observedAt(),
                        conversion == null ? null : conversion.visibleAt(),
                        conversion == null ? null : conversion.sourceSha256(),
                        conversion == null ? null : conversion.sourceJson());
        context.applyAtSlippedFillPrice(trade, null);
        HistoricalBar last = bars.getLast();
        BigDecimal held = sizing.settledBaseDelta(rules.symbol());
        BigDecimal equity =
                context.cashBalance()
                        .add(held.multiply(last.closePrice()))
                        .add(context.discountQuoteValueAt(last.availableAt()))
                        .setScale(18, RoundingMode.HALF_UP);
        return new ResearchRunFacts.Benchmark(
                "AVAILABLE",
                null,
                "BUY_AND_HOLD",
                "MARK_TO_MARKET",
                first.openTime().toString(),
                last.closeTime().toString(),
                entry.openTime().toString(),
                sizing.fillPrice(),
                last.closePrice(),
                capital,
                held,
                sizing.fee(),
                sizing.slippageCost(),
                equity,
                equity.subtract(capital).divide(capital, 18, RoundingMode.HALF_UP),
                sizing.feeCharge().currency(),
                sizing.feeCharge().amount(),
                sizing.feeCharge().paymentStatus(),
                context.discountFactsAt(last.availableAt()));
    }

    private static ResearchRunFacts.Benchmark calculate(
            List<HistoricalBar> bars,
            BigDecimal capital,
            Function<HistoricalBar, SpotTargetSizer.Result> sizingAtEntry) {
        if (bars.size() < 2)
            return unavailable("INSUFFICIENT_DATA", "NO_LATER_TRADABLE_EVENT", bars, capital);
        HistoricalBar first = bars.getFirst();
        HistoricalBar entry =
                bars.stream()
                        .skip(1)
                        .filter(bar -> bar.openTime().isAfter(first.availableAt()))
                        .findFirst()
                        .orElse(null);
        if (entry == null)
            return unavailable("INSUFFICIENT_DATA", "NO_LATER_TRADABLE_EVENT", bars, capital);
        // 入场选择只看首次已可见 bar 的时间，不看后续价格或策略表现。
        var sizing = sizingAtEntry.apply(entry);
        if (!sizing.executable())
            return unavailable("NOT_AVAILABLE", sizing.reason(), bars, capital);
        HistoricalBar last = bars.getLast();
        BigDecimal heldQuantity =
                sizing.feeCharge() == null
                        ? sizing.quantity()
                        : sizing.settledBaseDelta(entry.symbol());
        BigDecimal equity =
                sizing.remainingCash()
                        .add(heldQuantity.multiply(last.closePrice()))
                        .setScale(18, RoundingMode.HALF_UP);
        return new ResearchRunFacts.Benchmark(
                "AVAILABLE",
                null,
                "BUY_AND_HOLD",
                "MARK_TO_MARKET",
                first.openTime().toString(),
                last.closeTime().toString(),
                entry.openTime().toString(),
                sizing.fillPrice(),
                last.closePrice(),
                capital,
                heldQuantity,
                sizing.fee(),
                sizing.slippageCost(),
                equity,
                equity.subtract(capital).divide(capital, 18, RoundingMode.HALF_UP),
                sizing.feeCharge() == null ? null : sizing.feeCharge().currency(),
                sizing.feeCharge() == null ? null : sizing.feeCharge().amount(),
                sizing.feeCharge() == null ? null : sizing.feeCharge().paymentStatus());
    }

    private static ResearchRunFacts.Benchmark unavailable(
            String status, String reason, List<HistoricalBar> bars, BigDecimal capital) {
        return new ResearchRunFacts.Benchmark(
                status,
                reason,
                "BUY_AND_HOLD",
                "MARK_TO_MARKET",
                bars.isEmpty() ? null : bars.getFirst().openTime().toString(),
                bars.isEmpty() ? null : bars.getLast().closeTime().toString(),
                null,
                null,
                null,
                capital,
                null,
                null,
                null,
                null,
                null);
    }
}
