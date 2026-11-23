package com.guidinglight.nexusquant.strategy.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * StrategyRunTradeSummary 表示运行视角下的最小成交摘要。
 */
public record StrategyRunTradeSummary(
        String tradeId,
        String exchangeTradeId,
        String exchangeOrderId,
        BigDecimal price,
        BigDecimal quantity,
        Instant tradeTs
) {
}

