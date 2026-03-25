package com.guidinglight.nexusquant.app.web;

import com.guidinglight.nexusquant.backtest.model.SimTrade;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * GateFSimTradeResponse 是模拟成交响应体。
 */
public record GateFSimTradeResponse(
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
    public static GateFSimTradeResponse from(SimTrade simTrade) {
        return new GateFSimTradeResponse(
                simTrade.simTradeId(),
                simTrade.simOrderId(),
                simTrade.backtestRunId(),
                simTrade.symbol(),
                simTrade.side(),
                simTrade.quantity(),
                simTrade.tradePrice(),
                simTrade.feeAmount(),
                simTrade.slippageAmount(),
                simTrade.tradedAt(),
                simTrade.createdAt(),
                simTrade.updatedAt()
        );
    }
}
