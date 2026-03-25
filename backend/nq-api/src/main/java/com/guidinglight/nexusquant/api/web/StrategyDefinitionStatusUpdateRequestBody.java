package com.guidinglight.nexusquant.api.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * StrategyDefinitionStatusUpdateRequestBody 描述策略定义启停状态更新请求。
 * <p>
 * Why:
 * Step 2 要把旧的 `enable/disable` 动作路由收口为统一的资源状态更新接口，
 * 因此需要显式声明目标启停状态，避免路径继续暴露阶段性动作命名。
 */
@Schema(name = "StrategyDefinitionStatusUpdateRequestBody", description = "策略定义状态更新请求体")
public record StrategyDefinitionStatusUpdateRequestBody(
        @NotNull(message = "enabled must not be null")
        @Schema(description = "是否启用策略定义", requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean enabled
) {
}
