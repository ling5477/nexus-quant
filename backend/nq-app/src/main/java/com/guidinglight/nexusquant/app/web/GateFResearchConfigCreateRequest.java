package com.guidinglight.nexusquant.app.web;

/**
 * GateFResearchConfigCreateRequest 是创建研究配置的 HTTP 请求体。
 */
public record GateFResearchConfigCreateRequest(
        String sourceStrategyId,
        String name,
        String description,
        String parameterSchema,
        String parameterDefaults,
        String datasetSpec
) {
}
