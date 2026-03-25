package com.guidinglight.nexusquant.research.model;

import java.time.Instant;

/**
 * BacktestRun 表示 GateF-1 的回测运行事实。
 * <p>
 * Why:
 * 回测运行必须独立于 GateE 的 strategy_runs、orders、trades，
 * 否则后续模拟成交、评估结果和研究运行状态会与实盘/执行域事实混淆。
 */
public record BacktestRun(
        String backtestRunId,
        String backtestConfigId,
        String researchConfigId,
        String sourceStrategyId,
        String strategySnapshot,
        String backtestConfigSnapshot,
        BacktestRunStatus status,
        Instant requestedAt,
        Instant startedAt,
        Instant finishedAt,
        String failureCode,
        String failureMessage,
        String summaryJson,
        Instant createdAt,
        Instant updatedAt
) {
}
