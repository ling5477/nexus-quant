package com.guidinglight.nexusquant.research.domain.backtest;

import com.guidinglight.nexusquant.research.domain.backtest.BacktestExecutionContext;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.research.domain.backtest.SignalIntent;

/**
 * BacktestSignalPolicy 定义 bar -> signal intent 的最小策略接口。
 */
public interface BacktestSignalPolicy {

    SignalIntent evaluate(
            String sourceStrategyType,
            HistoricalBar historicalBar,
            int barIndex,
            int totalBars,
            BacktestExecutionContext backtestExecutionContext
    );
}


