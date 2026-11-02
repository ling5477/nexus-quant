package com.guidinglight.nexusquant.strategy.domain;

import java.time.Instant;

/**
 * StrategyRun 表示执行与补偿契约的最小运行事实。
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
        String traceId,
        String admissionScheduleId) {
    /** 旧创建路径继续保持原参数；明确准入计划由正式持久化读取补充，不从调用UUID推测。 */
    public StrategyRun(
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
            String traceId) {
        this(
                strategyRunId,
                strategyId,
                accountId,
                exchangeCode,
                tradeEnv,
                triggerType,
                status,
                configSnapshot,
                requestId,
                startedAt,
                finishedAt,
                errorMessage,
                traceId,
                null);
    }
}
