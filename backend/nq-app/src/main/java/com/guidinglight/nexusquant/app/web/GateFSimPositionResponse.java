package com.guidinglight.nexusquant.app.web;

import com.guidinglight.nexusquant.backtest.model.SimPosition;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * GateFSimPositionResponse 是模拟持仓响应体。
 */
public record GateFSimPositionResponse(
        String simPositionId,
        String backtestRunId,
        String symbol,
        BigDecimal quantity,
        BigDecimal averageEntryPrice,
        BigDecimal realizedPnl,
        Instant createdAt,
        Instant updatedAt
) {
    public static GateFSimPositionResponse from(SimPosition simPosition) {
        return new GateFSimPositionResponse(
                simPosition.simPositionId(),
                simPosition.backtestRunId(),
                simPosition.symbol(),
                simPosition.quantity(),
                simPosition.averageEntryPrice(),
                simPosition.realizedPnl(),
                simPosition.createdAt(),
                simPosition.updatedAt()
        );
    }
}
