package com.guidinglight.nexusquant.api.model;

import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * OrderView 是 API 层订单查询视图占位。
 */
public record OrderView(
        String orderId,
        Long accountId,
        String symbol,
        BigDecimal price,
        BigDecimal qty,
        OrderStatus status,
        Instant updatedAt
) {
}
