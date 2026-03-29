package com.guidinglight.nexusquant.research.domain.backtest;

import com.guidinglight.nexusquant.research.domain.BacktestRunStatus;

import java.time.Instant;

/**
 * BacktestExecutionResult 表示 GateF-2 最小回测执行结果。
 * <p>
 * Why:
 * 本批不输出交易结果，因此结果对象只承载状态、时间窗口、bar 数量和摘要 JSON，
 * 让 GateF-3/GateF-4 可以在此基础上继续扩展。
 */
public record BacktestExecutionResult(
        String backtestRunId,
        BacktestRunStatus resultStatus,
        int barCount,
        Instant executionStartedAt,
        Instant executionFinishedAt,
        Instant actualDataStartTime,
        Instant actualDataEndTime,
        String summaryJson
) {
}


