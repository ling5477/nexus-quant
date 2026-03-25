package com.guidinglight.nexusquant.app.web;

import com.guidinglight.nexusquant.research.model.ResearchConfig;

import java.time.Instant;

/**
 * GateFResearchConfigResponse 是研究配置响应体。
 */
public record GateFResearchConfigResponse(
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
    public static GateFResearchConfigResponse from(ResearchConfig researchConfig) {
        return new GateFResearchConfigResponse(
                researchConfig.researchConfigId(),
                researchConfig.sourceStrategyId(),
                researchConfig.strategySnapshot(),
                researchConfig.name(),
                researchConfig.description(),
                researchConfig.parameterSchema(),
                researchConfig.parameterDefaults(),
                researchConfig.datasetSpec(),
                researchConfig.createdAt(),
                researchConfig.updatedAt()
        );
    }
}
