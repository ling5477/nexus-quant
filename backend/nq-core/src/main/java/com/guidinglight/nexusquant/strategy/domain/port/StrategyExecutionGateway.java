package com.guidinglight.nexusquant.strategy.domain.port;

/**
 * StrategyExecutionGateway 定义 Strategy 到 execution capability 的稳定桥接。
 */
public interface StrategyExecutionGateway {

    StrategyExecutionResult execute(StrategyExecutionIntent intent);
}


