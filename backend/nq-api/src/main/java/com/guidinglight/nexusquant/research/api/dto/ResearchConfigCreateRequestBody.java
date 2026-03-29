package com.guidinglight.nexusquant.research.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * ResearchConfigCreateRequestBody 描述研究配置创建请求体。
 */
@Schema(name = "ResearchConfigCreateRequestBody", description = "研究配置创建请求体")
public record ResearchConfigCreateRequestBody(
        @NotBlank(message = "sourceStrategyId must not be blank")
        @Schema(description = "来源策略 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        String sourceStrategyId,
        @NotBlank(message = "name must not be blank")
        @Schema(description = "研究配置名称", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(description = "研究配置描述")
        String description,
        @NotBlank(message = "parameterSchema must not be blank")
        @Schema(description = "参数 schema", requiredMode = Schema.RequiredMode.REQUIRED)
        String parameterSchema,
        @NotBlank(message = "parameterDefaults must not be blank")
        @Schema(description = "参数默认值快照", requiredMode = Schema.RequiredMode.REQUIRED)
        String parameterDefaults,
        @NotBlank(message = "datasetSpec must not be blank")
        @Schema(description = "数据集规格", requiredMode = Schema.RequiredMode.REQUIRED)
        String datasetSpec
) {
}

