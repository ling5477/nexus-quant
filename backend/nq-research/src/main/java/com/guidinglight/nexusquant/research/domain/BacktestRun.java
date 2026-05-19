package com.guidinglight.nexusquant.research.domain;

import java.time.Instant;

/**
 * BacktestRun 表示回测运行事实。
 * <p>
 * Why:
 * 回测运行必须独立于 GateE 的 strategy_runs、orders、trades，
 * 否则后续模拟成交、评估结果和研究运行状态会与实盘/执行域事实混淆。
 * GateI-2 要求 run 在创建时固化 strategy version、dataset、参数和配置快照，
 * 因此这些字段是历史运行可复盘的输入事实，后续配置重新绑定不能改写。
 */
public record BacktestRun(
        String backtestRunId,
        String backtestConfigId,
        String researchConfigId,
        String sourceStrategyId,
        String strategySnapshot,
        String strategyVersionId,
        String strategyVersionSnapshotJson,
        String paramSnapshotJson,
        String backtestConfigSnapshot,
        String configSnapshotJson,
        String datasetSnapshotJson,
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
    public BacktestRun(
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
        this(
                backtestRunId,
                backtestConfigId,
                researchConfigId,
                sourceStrategyId,
                strategySnapshot,
                null,
                "{}",
                "{}",
                backtestConfigSnapshot,
                backtestConfigSnapshot,
                "{}",
                status,
                requestedAt,
                startedAt,
                finishedAt,
                failureCode,
                failureMessage,
                summaryJson,
                createdAt,
                updatedAt
        );
    }
}

