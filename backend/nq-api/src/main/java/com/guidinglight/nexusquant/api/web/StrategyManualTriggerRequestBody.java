package com.guidinglight.nexusquant.api.web;

import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * StrategyManualTriggerRequestBody 描述策略手动触发请求体。
 */
@Schema(name = "StrategyManualTriggerRequestBody", description = "策略手动触发请求体")
public record StrategyManualTriggerRequestBody(
        @NotBlank(message = "requestId must not be blank")
        @Schema(description = "请求 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        String requestId,
        @NotBlank(message = "symbol must not be blank")
        @Schema(description = "交易对", requiredMode = Schema.RequiredMode.REQUIRED)
        String symbol,
        @NotNull(message = "side must not be null")
        @Schema(description = "买卖方向", requiredMode = Schema.RequiredMode.REQUIRED)
        OrderSide side,
        @NotNull(message = "orderType must not be null")
        @Schema(description = "订单类型", requiredMode = Schema.RequiredMode.REQUIRED)
        OrderType orderType,
        @NotNull(message = "quantity must not be null")
        @Positive(message = "quantity must be positive")
        @Schema(description = "委托数量", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal quantity,
        @Schema(description = "委托价格；市价单可为空")
        BigDecimal price
) {
}
