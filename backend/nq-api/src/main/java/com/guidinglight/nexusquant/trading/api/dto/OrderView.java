package com.guidinglight.nexusquant.trading.api.dto;

import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * OrderView 是统一交易契约的最小订单查询视图。
 * <p>
 * Why:
 * 最小订单读模型固定订单主键、定位字段、数量价格、状态与 trace 这些
 * 与执行闭环直接相关的字段，不承担完整报表视图职责。
 */
public record OrderView(
        String orderId,
        Long accountId,
        String venue,
        String symbol,
        String clientOrderId,
        String externalOrderId,
        String side,
        String type,
        BigDecimal price,
        BigDecimal quantity,
        OrderStatus status,
        String tradeEnv,
        Instant createdAt,
        Instant updatedAt,
        String traceId
) {

    public static OrderView from(OrderRecord order) {
        return new OrderView(
                order.orderId(),
                order.accountId(),
                order.venue(),
                order.symbol(),
                order.clientOrderId(),
                order.externalOrderId(),
                order.side(),
                order.type(),
                order.price(),
                order.qty(),
                order.status(),
                "SIM",
                null,
                null,
                order.traceId()
        );
    }
}



