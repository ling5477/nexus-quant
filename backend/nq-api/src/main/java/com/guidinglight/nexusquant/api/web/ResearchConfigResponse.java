package com.guidinglight.nexusquant.api.web;

import com.guidinglight.nexusquant.research.model.ResearchConfig;

import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * ResearchConfigResponse 描述接口响应体。
 */
@Schema(name = "ResearchConfigResponse", description = "接口响应体")
public record ResearchConfigResponse(
        @Schema(description = "researchConfigId")
        String researchConfigId,
        @Schema(description = "sourceStrategyId")
        String sourceStrategyId,
        @Schema(description = "strategySnapshot")
        String strategySnapshot,
        @Schema(description = "name")
        String name,
        @Schema(description = "description")
        String description,
        @Schema(description = "parameterSchema")
        String parameterSchema,
        @Schema(description = "parameterDefaults")
        String parameterDefaults,
        @Schema(description = "datasetSpec")
        String datasetSpec,
        @Schema(description = "createdAt")
        Instant createdAt,
        @Schema(description = "updatedAt")
        Instant updatedAt
) {
    public static ResearchConfigResponse from(ResearchConfig researchConfig) {
        return new ResearchConfigResponse(
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
