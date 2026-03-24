package com.guidinglight.nexusquant.app.web;

import com.guidinglight.nexusquant.core.model.StrategyRunOrderSummary;

import java.math.BigDecimal;

/**
 * GateEStrategyRunOrderResponse 表示运行详情里的最小订单摘要返回。
 */
public record GateEStrategyRunOrderResponse(
        String orderId,
        String clientOrderId,
        String exchangeOrderId,
        String orderStatus,
        String symbol,
        String side,
        String orderType,
        BigDecimal price,
        BigDecimal quantity
) {
    public static GateEStrategyRunOrderResponse from(StrategyRunOrderSummary summary) {
        return new GateEStrategyRunOrderResponse(
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
