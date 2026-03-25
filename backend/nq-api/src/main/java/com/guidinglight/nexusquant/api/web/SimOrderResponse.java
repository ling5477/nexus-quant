package com.guidinglight.nexusquant.api.web;

import com.guidinglight.nexusquant.backtest.model.SimOrder;
import com.guidinglight.nexusquant.backtest.model.SimOrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * SimOrderResponse 描述接口响应体。
 */
@Schema(name = "SimOrderResponse", description = "接口响应体")
public record SimOrderResponse(
        @Schema(description = "simOrderId")
        String simOrderId,
        @Schema(description = "backtestRunId")
        String backtestRunId,
        @Schema(description = "symbol")
        String symbol,
        @Schema(description = "side")
        String side,
        @Schema(description = "orderType")
        String orderType,
        @Schema(description = "requestedQuantity")
        BigDecimal requestedQuantity,
        @Schema(description = "requestedPrice")
        BigDecimal requestedPrice,
        @Schema(description = "status")
        SimOrderStatus status,
        @Schema(description = "createdAt")
        Instant createdAt,
        @Schema(description = "filledAt")
        Instant filledAt,
        @Schema(description = "rejectReason")
        String rejectReason,
        @Schema(description = "updatedAt")
        Instant updatedAt
) {
    public static SimOrderResponse from(SimOrder simOrder) {
        return new SimOrderResponse(
                simOrder.simOrderId(),
                simOrder.backtestRunId(),
                simOrder.symbol(),
                simOrder.side(),
                simOrder.orderType(),
                simOrder.requestedQuantity(),
                simOrder.requestedPrice(),
                simOrder.status(),
                simOrder.createdAt(),
                simOrder.filledAt(),
                simOrder.rejectReason(),
                simOrder.updatedAt()
        );
    }
}
