package com.guidinglight.nexusquant.backtest.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * SimOrder 表示 GateF-3 的模拟订单事实。
 */
public record SimOrder(
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
}
