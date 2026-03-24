package com.guidinglight.nexusquant.core.model;

import java.time.Instant;

/**
 * StrategyRunSummary 表示运行列表中的最小摘要。
 */
public record StrategyRunSummary(
        String strategyId,
        String scheduleJobId,
        String strategyRunId,
        String requestId,
        String triggerType,
        StrategyRunStatus status,
        String exchangeCode,
        Long accountId,
        String tradeEnv,
        Instant startedAt,
        Instant finishedAt,
        String errorMessage
) {
}
