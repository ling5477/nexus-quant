package com.guidinglight.nexusquant.strategy.domain;

import java.time.Instant;

/**
 * StrategySchedule 表示 GateE-2.1 的最小计划配置事实。
 */
public record StrategySchedule(
        String scheduleJobId,
        String strategyId,
        String scheduleType,
        String cronExpr,
        String timezone,
        boolean enabled,
        String windowConfig,
        String dedupScope,
        String exchangeCode,
        Long accountId,
        String tradeEnv,
        Instant lastTriggeredAt,
        Instant createdAt,
        Instant updatedAt
) {
    public StrategyScheduleStatus status() {
        return StrategyScheduleStatus.fromEnabled(enabled);
    }

    public StrategySchedule withEnabled(boolean nextEnabled, Instant nextUpdatedAt) {
        return new StrategySchedule(
                scheduleJobId,
                strategyId,
                scheduleType,
                cronExpr,
                timezone,
                nextEnabled,
                windowConfig,
                dedupScope,
                exchangeCode,
                accountId,
                tradeEnv,
                lastTriggeredAt,
                createdAt,
                nextUpdatedAt
        );
    }

    public StrategySchedule withLastTriggeredAt(Instant nextLastTriggeredAt, Instant nextUpdatedAt) {
        return new StrategySchedule(
                scheduleJobId,
                strategyId,
                scheduleType,
                cronExpr,
                timezone,
                enabled,
                windowConfig,
                dedupScope,
                exchangeCode,
                accountId,
                tradeEnv,
                nextLastTriggeredAt,
                createdAt,
                nextUpdatedAt
        );
    }
}

