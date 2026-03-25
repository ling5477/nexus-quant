package com.guidinglight.nexusquant.backtest.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * SimTrade 表示 GateF-3 的模拟成交事实。
 */
public record SimTrade(
        String simTradeId,
        String simOrderId,
        String backtestRunId,
        String symbol,
        String side,
        BigDecimal quantity,
        BigDecimal tradePrice,
        BigDecimal feeAmount,
        BigDecimal slippageAmount,
        Instant tradedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
