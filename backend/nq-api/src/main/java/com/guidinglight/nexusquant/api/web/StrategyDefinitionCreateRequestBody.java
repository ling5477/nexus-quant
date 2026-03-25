package com.guidinglight.nexusquant.api.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * StrategyDefinitionCreateRequestBody 描述策略定义创建请求体。
 */
@Schema(name = "StrategyDefinitionCreateRequestBody", description = "策略定义创建请求体")
public record StrategyDefinitionCreateRequestBody(
        @NotBlank(message = "strategyCode must not be blank")
        @Schema(description = "策略编码", requiredMode = Schema.RequiredMode.REQUIRED)
        String strategyCode,
        @NotBlank(message = "strategyName must not be blank")
        @Schema(description = "策略名称", requiredMode = Schema.RequiredMode.REQUIRED)
        String strategyName,
        @NotBlank(message = "strategyType must not be blank")
        @Schema(description = "策略类型", requiredMode = Schema.RequiredMode.REQUIRED)
        String strategyType,
        @NotBlank(message = "exchangeCode must not be blank")
        @Schema(description = "交易所编码", requiredMode = Schema.RequiredMode.REQUIRED)
        String exchangeCode,
        @NotNull(message = "accountId must not be null")
        @Positive(message = "accountId must be positive")
        @Schema(description = "账户 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        Long accountId,
        @NotBlank(message = "tradeEnv must not be blank")
        @Schema(description = "交易环境", requiredMode = Schema.RequiredMode.REQUIRED)
        String tradeEnv,
        @NotBlank(message = "configSnapshot must not be blank")
        @Schema(description = "配置快照", requiredMode = Schema.RequiredMode.REQUIRED)
        String configSnapshot
) {
}
