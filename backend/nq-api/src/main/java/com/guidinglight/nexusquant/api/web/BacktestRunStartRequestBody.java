package com.guidinglight.nexusquant.api.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * BacktestRunStartRequestBody 描述回测运行创建请求体。
 */
@Schema(name = "BacktestRunStartRequestBody", description = "回测运行创建请求体")
public record BacktestRunStartRequestBody(
        @NotBlank(message = "backtestConfigId must not be blank")
        @Schema(description = "回测配置 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        String backtestConfigId
) {
}
