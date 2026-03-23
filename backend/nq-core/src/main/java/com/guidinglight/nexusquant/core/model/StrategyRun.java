package com.guidinglight.nexusquant.core.model;

import java.time.Instant;

/**
 * StrategyRun 表示 GateE-1.2 的最小运行事实。
 */
public record StrategyRun(
        String strategyRunId,
        String strategyId,
        Long accountId,
        String exchangeCode,
        String tradeEnv,
        String triggerType,
        StrategyRunStatus status,
        String configSnapshot,
        String requestId,
        Instant startedAt,
        Instant finishedAt,
        String errorMessage,
        String traceId
) {
}
