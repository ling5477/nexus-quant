package com.guidinglight.nexusquant.core.model;

import java.math.BigDecimal;

/**
 * StrategyRunOrderSummary 表示运行视角下的最小订单摘要。
 */
public record StrategyRunOrderSummary(
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
}
