package com.guidinglight.nexusquant.research.application.command;

/**
 * ResearchConfigCreateRequest 描述创建研究配置时需要的最小输入。
 */
public record ResearchConfigCreateRequest(
        String sourceStrategyId,
        String name,
        String description,
        String parameterSchema,
        String parameterDefaults,
        String datasetSpec
) {
}

