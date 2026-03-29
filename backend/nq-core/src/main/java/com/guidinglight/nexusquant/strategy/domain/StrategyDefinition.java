package com.guidinglight.nexusquant.strategy.domain;

import java.time.Instant;

/**
 * StrategyDefinition 表示 GateE-1.1 的策略定义事实。
 * <p>
 * Why:
 * GateE-1.1 只解决“定义可管理”，因此模型只承载定义级字段、启停开关与配置快照，
 * 不混入 strategyRun 或 schedule job 语义。
 */
public record StrategyDefinition(
        String strategyId,
        String strategyCode,
        String strategyName,
        String strategyType,
        String exchangeCode,
        Long accountId,
        String tradeEnv,
        boolean enabled,
        String configSnapshot,
        int version,
        Instant createdAt,
        Instant updatedAt
) {

    public StrategyDefinitionStatus status() {
        return StrategyDefinitionStatus.fromEnabled(enabled);
    }

    public StrategyDefinition withEnabled(boolean nextEnabled, Instant nextUpdatedAt) {
        return new StrategyDefinition(
                strategyId,
                strategyCode,
                strategyName,
                strategyType,
                exchangeCode,
                accountId,
                tradeEnv,
                nextEnabled,
                configSnapshot,
                version,
                createdAt,
                nextUpdatedAt
        );
    }
}

