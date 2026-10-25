package com.guidinglight.nexusquant.research.domain.backtest;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * SimOrder 表示研究与回测契约的模拟订单事实。
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

