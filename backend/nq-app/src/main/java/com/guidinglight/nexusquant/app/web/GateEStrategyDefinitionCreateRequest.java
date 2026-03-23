package com.guidinglight.nexusquant.app.web;

/**
 * GateEStrategyDefinitionCreateRequest 描述 GateE-1.1 创建策略定义请求体。
 */
public record GateEStrategyDefinitionCreateRequest(
        String strategyCode,
        String strategyName,
        String strategyType,
        String exchangeCode,
        Long accountId,
        String tradeEnv,
        String configSnapshot
) {
}
