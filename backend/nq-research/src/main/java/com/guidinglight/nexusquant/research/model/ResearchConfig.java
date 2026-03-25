package com.guidinglight.nexusquant.research.model;

import java.time.Instant;

/**
 * ResearchConfig 表示 GateF-1 的研究配置事实。
 * <p>
 * Why:
 * 研究域必须保存 `sourceStrategyId + strategySnapshot` 的双重信息，
 * 这样后续 strategy_definitions 继续演进时，历史研究配置仍能保持可复盘、可审计。
 */
public record ResearchConfig(
        String researchConfigId,
        String sourceStrategyId,
        String strategySnapshot,
        String name,
        String description,
        String parameterSchema,
        String parameterDefaults,
        String datasetSpec,
        Instant createdAt,
        Instant updatedAt
) {
}
