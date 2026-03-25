package com.guidinglight.nexusquant.api.web;

import com.guidinglight.nexusquant.core.model.StrategyRunOrderSummary;

import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * StrategyRunOrderResponse 描述接口响应体。
 */
@Schema(name = "StrategyRunOrderResponse", description = "接口响应体")
public record StrategyRunOrderResponse(
        @Schema(description = "orderId")
        String orderId,
        @Schema(description = "clientOrderId")
        String clientOrderId,
        @Schema(description = "exchangeOrderId")
        String exchangeOrderId,
        @Schema(description = "orderStatus")
        String orderStatus,
        @Schema(description = "symbol")
        String symbol,
        @Schema(description = "side")
        String side,
        @Schema(description = "orderType")
        String orderType,
        @Schema(description = "price")
        BigDecimal price,
        @Schema(description = "quantity")
        BigDecimal quantity
) {
    public static StrategyRunOrderResponse from(StrategyRunOrderSummary summary) {
        return new StrategyRunOrderResponse(
                summary.orderId(),
                summary.clientOrderId(),
                summary.exchangeOrderId(),
                summary.orderStatus(),
                summary.symbol(),
                summary.side(),
                summary.orderType(),
                summary.price(),
                summary.quantity()
        );
    }
}
