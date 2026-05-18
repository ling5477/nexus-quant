package com.guidinglight.nexusquant.strategy.api.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * StrategyVersionCreateRequestBody 描述 GateI-1 创建策略版本的 HTTP 请求体。
 */
@Schema(name = "StrategyVersionCreateRequestBody", description = "策略版本创建请求")
public record StrategyVersionCreateRequestBody(
        @Size(max = 128, message = "versionName length must be less than or equal to 128")
        @Schema(description = "版本展示名称")
        String versionName,
        @Size(max = 32, message = "status length must be less than or equal to 32")
        @Schema(description = "版本状态：DRAFT / ACTIVE / ARCHIVED；为空时默认 DRAFT")
        String status,
        @Schema(description = "策略参数快照 JSON；为空时默认 {}")
        String paramSnapshotJson,
        @Schema(description = "策略配置快照 JSON；为空时默认当前 strategy definition 配置")
        String configSnapshotJson,
        @Schema(description = "来源快照 JSON；为空时默认 {}")
        String sourceSnapshotJson
) {
}
