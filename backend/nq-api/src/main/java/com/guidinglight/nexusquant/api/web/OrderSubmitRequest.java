package com.guidinglight.nexusquant.api.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * OrderSubmitRequest 描述正式下单接口的输入边界。
 * <p>
 * Why:
 * 下单链路会进入幂等、状态机与风控，因此必须先在 HTTP 边界阻断空值与明显非法数值，
 * 避免 service 继续承担重复的基础参数校验。
 */
@Schema(name = "OrderSubmitRequest", description = "交易下单请求体")
public record OrderSubmitRequest(
        @NotNull(message = "accountId must not be null")
        @Positive(message = "accountId must be positive")
        @Schema(description = "账户 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        Long accountId,
        @Schema(description = "策略运行 ID；人工触发时可为空")
        String strategyRunId,
        @NotBlank(message = "venue must not be blank")
        @Schema(description = "交易 venue", requiredMode = Schema.RequiredMode.REQUIRED)
        String venue,
        @NotBlank(message = "clientOrderId must not be blank")
        @Schema(description = "客户端订单 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        String clientOrderId,
        @NotBlank(message = "symbol must not be blank")
        @Schema(description = "交易对", requiredMode = Schema.RequiredMode.REQUIRED)
        String symbol,
        @NotNull(message = "side must not be null")
        @Schema(description = "买卖方向", requiredMode = Schema.RequiredMode.REQUIRED)
        OrderSide side,
        @JsonProperty("orderType")
        @NotNull(message = "orderType must not be null")
        @Schema(description = "订单类型", requiredMode = Schema.RequiredMode.REQUIRED)
        OrderType orderType,
        @Schema(description = "委托价格；LIMIT 单必须为正数")
        BigDecimal price,
        @JsonProperty("quantity")
        @NotNull(message = "quantity must not be null")
        @Positive(message = "quantity must be positive")
        @Schema(description = "委托数量", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal quantity
) {

    /**
     * Why:
     * `LIMIT` 单必须提供正价格，而 `MARKET` 单允许省略价格。
     * 这条约束跨字段，不能仅靠单字段注解表达，因此在 DTO 上集中声明。
     */
    @AssertTrue(message = "price must be positive for LIMIT order")
    @Schema(hidden = true)
    public boolean isPriceValidForLimitOrder() {
        if (orderType != OrderType.LIMIT) {
            return true;
        }
        return price != null && price.compareTo(BigDecimal.ZERO) > 0;
    }
}
