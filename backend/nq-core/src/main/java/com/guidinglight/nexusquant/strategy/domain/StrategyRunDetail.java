package com.guidinglight.nexusquant.strategy.domain;

import java.time.Instant;

/**
 * StrategyRunDetail 表示单次运行的最小详情聚合。
 */
public record StrategyRunDetail(
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
        String errorMessage,
        StrategyRunExecutionResult executionResult
) {
}

