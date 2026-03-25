package com.guidinglight.nexusquant.backtest.service;

import com.guidinglight.nexusquant.backtest.model.BacktestExecutionContext;
import com.guidinglight.nexusquant.backtest.model.HistoricalBar;
import com.guidinglight.nexusquant.backtest.model.SignalIntent;

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
