package com.guidinglight.nexusquant.research.domain;

/**
 * SourceStrategySnapshot 是 GateF-1 从 strategy_definitions 复制出的只读策略快照。
 * <p>
 * Why:
 * 研究域只需要引用已有策略定义的静态元信息，不需要复用 GateE 的运行语义或可变生命周期。
 */
public record SourceStrategySnapshot(
        String strategyId,
        String strategyCode,
        String strategyName,
        String strategyType,
        String exchangeCode,
        Long accountId,
        String tradeEnv,
        boolean enabled,
        String configSnapshot,
        int version
) {
}

