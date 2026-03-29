package com.guidinglight.nexusquant.research.domain.backtest;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * SimPosition 表示 run + symbol 维度的最小持仓事实。
 */
public record SimPosition(
        String simPositionId,
        String backtestRunId,
        String symbol,
        BigDecimal quantity,
        BigDecimal averageEntryPrice,
        BigDecimal realizedPnl,
        Instant createdAt,
        Instant updatedAt
) {
}

