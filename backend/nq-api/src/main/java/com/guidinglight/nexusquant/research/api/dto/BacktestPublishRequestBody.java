package com.guidinglight.nexusquant.research.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * BacktestPublishRequestBody 描述回测发布请求体。
 */
@Schema(name = "BacktestPublishRequestBody", description = "回测发布请求体")
public record BacktestPublishRequestBody(
        @Size(max = 128, message = "displayName length must be less than or equal to 128")
        @Schema(description = "发布展示名称；为空时沿用默认命名")
        String displayName,
        @Size(max = 128, message = "strategyVersionId length must be less than or equal to 128")
        @Schema(description = "可选策略版本 ID；传入时发布记录会绑定并固化 version snapshot")
        String strategyVersionId
) {
}

