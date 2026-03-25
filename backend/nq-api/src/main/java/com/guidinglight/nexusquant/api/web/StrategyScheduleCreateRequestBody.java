package com.guidinglight.nexusquant.api.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * StrategyScheduleCreateRequestBody 描述策略调度创建请求体。
 * <p>
 * Why:
 * Step 2 要把调度接口收口到 `/api/strategy-schedules` 资源路径下，
 * 因此必须把 `strategyId` 从旧路径参数显式提升为请求体字段。
 */
@Schema(name = "StrategyScheduleCreateRequestBody", description = "策略调度创建请求体")
public record StrategyScheduleCreateRequestBody(
        @NotBlank(message = "strategyId must not be blank")
        @Schema(description = "策略 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        String strategyId,
        @NotBlank(message = "scheduleType must not be blank")
        @Schema(description = "调度类型", requiredMode = Schema.RequiredMode.REQUIRED)
        String scheduleType,
        @NotBlank(message = "cronExpr must not be blank")
        @Schema(description = "Cron 表达式", requiredMode = Schema.RequiredMode.REQUIRED)
        String cronExpr,
        @NotBlank(message = "timezone must not be blank")
        @Schema(description = "时区", requiredMode = Schema.RequiredMode.REQUIRED)
        String timezone,
        @NotNull(message = "enabled must not be null")
        @Schema(description = "是否启用", requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean enabled,
        @NotBlank(message = "windowConfig must not be blank")
        @Schema(description = "窗口配置", requiredMode = Schema.RequiredMode.REQUIRED)
        String windowConfig,
        @NotBlank(message = "dedupScope must not be blank")
        @Schema(description = "去重范围", requiredMode = Schema.RequiredMode.REQUIRED)
        String dedupScope
) {
}
