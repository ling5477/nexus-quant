package com.guidinglight.nexusquant.api.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * StrategyScheduleStatusUpdateRequestBody 描述调度启停状态更新请求。
 * <p>
 * Why:
 * 正式 API 只保留资源化的状态更新入口，不再暴露 `enable/disable` 两套动作路由。
 */
@Schema(name = "StrategyScheduleStatusUpdateRequestBody", description = "策略调度状态更新请求体")
public record StrategyScheduleStatusUpdateRequestBody(
        @NotNull(message = "enabled must not be null")
        @Schema(description = "是否启用调度", requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean enabled
) {
}
