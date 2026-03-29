package com.guidinglight.nexusquant.research.domain.backtest;

import com.guidinglight.nexusquant.marketdata.domain.HistoricalDatasetSpec;

import java.time.Instant;

/**
 * BacktestExecutionRequest 表示最小回测执行输入。
 */
public record BacktestExecutionRequest(
        String backtestRunId,
        String researchConfigId,
        String backtestConfigId,
        String sourceStrategyId,
        String sourceStrategyType,
        String strategySnapshot,
        HistoricalDatasetSpec datasetSpec,
        Instant startTime,
        Instant endTime,
        java.math.BigDecimal initialCapital,
        String executionSpecJson
) {
}

