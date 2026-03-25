package com.guidinglight.nexusquant.research.model;

/**
 * ExecutionStrategyDefinitionDraft 表示发布到执行域前的 strategy_definition 草稿。
 */
public record ExecutionStrategyDefinitionDraft(
        String targetStrategyDefinitionId,
        String strategyCode,
        String strategyName,
        String strategyType,
        String exchangeCode,
        Long accountId,
        String tradeEnv,
        String configSnapshotJson
) {
}
