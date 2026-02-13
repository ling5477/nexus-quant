package com.guidinglight.nexusquant.core.model;

import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import java.time.Instant;

/**
 * OrderAggregate 是订单领域聚合根的最小骨架。
 *
 * Why:
 * Gate A 需要先固定状态机驱动所需字段（orderId/clientOrderId/status/traceId），
 * 后续实现才能在不破坏契约的前提下扩展行为。
 */
public record OrderAggregate(
        String orderId,
        Long accountId,
        String symbol,
        String clientOrderId,
        OrderStatus status,
        String traceId,
        Instant createdAt
) {
}
