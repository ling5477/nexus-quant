package com.guidinglight.nexusquant.strategy.api.dto;

import com.guidinglight.nexusquant.strategy.domain.StrategyRunOrderItem;

import java.time.Instant;

/** 十进制以普通字符串传输，避免浏览器在收到事实时已发生double舍入。 */
public record StrategyRunOrderItemResponse(
        String orderId,
        String clientOrderId,
        String exchangeOrderId,
        String orderStatus,
        String symbol,
        String side,
        String orderType,
        String price,
        String quantity,
        Instant createdAt,
        Instant updatedAt) {
    public static StrategyRunOrderItemResponse from(StrategyRunOrderItem item) {
        return new StrategyRunOrderItemResponse(
                item.orderId(),
                item.clientOrderId(),
                item.exchangeOrderId(),
                item.orderStatus(),
                item.symbol(),
                item.side(),
                item.orderType(),
                item.price() == null ? null : item.price().toPlainString(),
                item.quantity().toPlainString(),
                item.createdAt(),
                item.updatedAt());
    }
}
