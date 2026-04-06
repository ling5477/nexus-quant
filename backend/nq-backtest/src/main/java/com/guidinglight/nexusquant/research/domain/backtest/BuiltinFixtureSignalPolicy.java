package com.guidinglight.nexusquant.research.domain.backtest;

import com.guidinglight.nexusquant.research.domain.backtest.BacktestExecutionContext;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.research.domain.backtest.SignalIntent;
import com.guidinglight.nexusquant.research.domain.backtest.SignalIntentType;

import java.math.BigDecimal;
import java.time.Instant;


/**
 * BuiltinFixtureSignalPolicy 提供 GateF-3 的最小内建策略意图。
 * <p>
 * Why:
 * 本批不接 Python runtime bridge，但必须把 sim_* 事实链跑通，因此使用稳定可复现的内建 fixture 策略作为过渡。
 */
public class BuiltinFixtureSignalPolicy implements BacktestSignalPolicy {

    private static final String BUY_AND_HOLD_FIXTURE = "BUY_AND_HOLD_FIXTURE";

    @Override
    public SignalIntent evaluate(
            String sourceStrategyType,
            HistoricalBar historicalBar,
            int barIndex,
            int totalBars,
            BacktestExecutionContext backtestExecutionContext
    ) {
        if (!BUY_AND_HOLD_FIXTURE.equalsIgnoreCase(sourceStrategyType)) {
            throw new IllegalArgumentException("unsupported fixture strategy type: " + sourceStrategyType);
        }
        if (barIndex == 0) {
            return new SignalIntent(
                    backtestExecutionContext.backtestRunId(),
                    historicalBar.symbol(),
                    SignalIntentType.BUY,
                    BigDecimal.ONE,
                    "BUY_AND_HOLD_FIXTURE:first_bar_buy",
                    historicalBar.closeTime()
            );
        }
        if (barIndex == totalBars - 1 && backtestExecutionContext.currentPosition() != null
                && backtestExecutionContext.currentPosition().quantity().compareTo(BigDecimal.ZERO) > 0) {
            return new SignalIntent(
                    backtestExecutionContext.backtestRunId(),
                    historicalBar.symbol(),
                    SignalIntentType.CLOSE,
                    backtestExecutionContext.currentPosition().quantity(),
                    "BUY_AND_HOLD_FIXTURE:last_bar_close",
                    historicalBar.closeTime()
            );
        }
        return new SignalIntent(
                backtestExecutionContext.backtestRunId(),
                historicalBar.symbol(),
                SignalIntentType.HOLD,
                BigDecimal.ZERO,
                "BUY_AND_HOLD_FIXTURE:hold",
                historicalBar.closeTime()
        );
    }
}


