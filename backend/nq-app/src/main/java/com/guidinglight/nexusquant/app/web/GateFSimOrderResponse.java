package com.guidinglight.nexusquant.app.web;

import com.guidinglight.nexusquant.backtest.model.SimOrder;
import com.guidinglight.nexusquant.backtest.model.SimOrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * GateFSimOrderResponse 是模拟订单响应体。
 */
public record GateFSimOrderResponse(
        String simOrderId,
        String backtestRunId,
        String symbol,
        String side,
        String orderType,
        BigDecimal requestedQuantity,
        BigDecimal requestedPrice,
        SimOrderStatus status,
        Instant createdAt,
        Instant filledAt,
        String rejectReason,
        Instant updatedAt
) {
    public static GateFSimOrderResponse from(SimOrder simOrder) {
        return new GateFSimOrderResponse(
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
