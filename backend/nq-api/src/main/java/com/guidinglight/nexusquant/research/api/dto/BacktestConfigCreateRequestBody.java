package com.guidinglight.nexusquant.research.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * BacktestConfigCreateRequestBody 描述回测配置创建请求体。
 */
@Schema(name = "BacktestConfigCreateRequestBody", description = "回测配置创建请求体")
public record BacktestConfigCreateRequestBody(
        @NotBlank(message = "researchConfigId must not be blank")
        @Schema(description = "研究配置 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        String researchConfigId,
        @NotBlank(message = "name must not be blank")
        @Schema(description = "回测配置名称", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(description = "配置描述")
        String description,
        @NotNull(message = "startTime must not be null")
        @Schema(description = "回测开始时间", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant startTime,
        @NotNull(message = "endTime must not be null")
        @Schema(description = "回测结束时间", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant endTime,
        @NotNull(message = "initialCapital must not be null")
        @Positive(message = "initialCapital must be positive")
        @Schema(description = "初始资金", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal initialCapital,
        @NotBlank(message = "executionSpec must not be blank")
        @Schema(description = "执行参数快照", requiredMode = Schema.RequiredMode.REQUIRED)
        String executionSpec,
        @NotBlank(message = "evaluationSpec must not be blank")
        @Schema(description = "评估参数快照", requiredMode = Schema.RequiredMode.REQUIRED)
        String evaluationSpec
) {
}

