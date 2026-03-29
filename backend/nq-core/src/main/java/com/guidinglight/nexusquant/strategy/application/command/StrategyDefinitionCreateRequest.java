package com.guidinglight.nexusquant.strategy.application;

/**
 * StrategyDefinitionCreateRequest 表示创建策略定义的最小入口参数。
 */
public record StrategyDefinitionCreateRequest(
        String strategyCode,
        String strategyName,
        String strategyType,
        String exchangeCode,
        Long accountId,
        String tradeEnv,
        String configSnapshot
) {
}


