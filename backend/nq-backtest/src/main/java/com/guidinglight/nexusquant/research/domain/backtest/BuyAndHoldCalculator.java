package com.guidinglight.nexusquant.research.domain.backtest;

import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.strategy.domain.SpotTargetSizer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** 沿用冻结回测的下一可交易开盘、成本和数量规则；最后收盘仅估值，不收虚构退出费用。 */
public final class BuyAndHoldCalculator {
    private BuyAndHoldCalculator() { }

    public static ResearchRunFacts.Benchmark calculate(List<HistoricalBar> bars, BigDecimal capital,
                                                       SpotTargetSizer.Rules rules) {
        if (bars.size() < 2) return unavailable("INSUFFICIENT_DATA", "NO_LATER_TRADABLE_EVENT", bars, capital);
        HistoricalBar first = bars.getFirst();
        HistoricalBar entry = bars.stream().skip(1)
                .filter(bar -> bar.openTime().isAfter(first.availableAt()))
                .findFirst().orElse(null);
        if (entry == null) return unavailable("INSUFFICIENT_DATA", "NO_LATER_TRADABLE_EVENT", bars, capital);
        // 入场选择只看首次已可见 bar 的时间，不看后续价格或策略表现。
        var sizing = SpotTargetSizer.size(BigDecimal.ONE, new SpotTargetSizer.State(capital,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, entry.openPrice()), rules);
        if (!sizing.executable()) return unavailable("NOT_AVAILABLE", sizing.reason(), bars, capital);
        HistoricalBar last = bars.getLast();
        BigDecimal equity = sizing.remainingCash().add(sizing.quantity().multiply(last.closePrice()))
                .setScale(18, RoundingMode.HALF_UP);
        return new ResearchRunFacts.Benchmark("AVAILABLE", null, "BUY_AND_HOLD", "MARK_TO_MARKET",
                first.openTime().toString(), last.closeTime().toString(), entry.openTime().toString(),
                sizing.fillPrice(), last.closePrice(), capital, sizing.quantity(), sizing.fee(),
                sizing.slippageCost(), equity, equity.subtract(capital).divide(capital, 18, RoundingMode.HALF_UP));
    }

    private static ResearchRunFacts.Benchmark unavailable(String status, String reason,
                                                          List<HistoricalBar> bars, BigDecimal capital) {
        return new ResearchRunFacts.Benchmark(status, reason, "BUY_AND_HOLD", "MARK_TO_MARKET",
                bars.isEmpty() ? null : bars.getFirst().openTime().toString(),
                bars.isEmpty() ? null : bars.getLast().closeTime().toString(),
                null, null, null, capital, null, null, null, null, null);
    }
}
