package com.guidinglight.nexusquant.strategy.domain;

import java.math.BigDecimal;
import java.time.Instant;

/** 只读既有Order事实，金额保留十进制；不把运行状态换算成订单状态。 */
public record StrategyRunOrderItem(
        String orderId,
        String clientOrderId,
        String exchangeOrderId,
        String orderStatus,
        String symbol,
        String side,
        String orderType,
        BigDecimal price,
        BigDecimal quantity,
        Instant createdAt,
        Instant updatedAt) {}
