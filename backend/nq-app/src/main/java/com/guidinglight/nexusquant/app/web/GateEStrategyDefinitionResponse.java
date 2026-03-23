package com.guidinglight.nexusquant.app.web;

import com.guidinglight.nexusquant.core.model.StrategyDefinition;
import com.guidinglight.nexusquant.core.model.StrategyDefinitionStatus;

import java.time.Instant;

/**
 * GateEStrategyDefinitionResponse 描述 GateE-1.1 的最小策略定义管理响应。
 */
public record GateEStrategyDefinitionResponse(
        String strategyId,
        String strategyCode,
        String strategyName,
        String strategyType,
        String exchangeCode,
        Long accountId,
        String tradeEnv,
        boolean enabled,
        StrategyDefinitionStatus status,
        String configSnapshot,
        int version,
        Instant createdAt,
        Instant updatedAt
) {
    public static GateEStrategyDefinitionResponse from(StrategyDefinition definition) {
        return new GateEStrategyDefinitionResponse(
                definition.strategyId(),
                definition.strategyCode(),
                definition.strategyName(),
                definition.strategyType(),
                definition.exchangeCode(),
                definition.accountId(),
                definition.tradeEnv(),
                definition.enabled(),
                definition.status(),
                definition.configSnapshot(),
                definition.version(),
                definition.createdAt(),
                definition.updatedAt()
        );
    }
}
