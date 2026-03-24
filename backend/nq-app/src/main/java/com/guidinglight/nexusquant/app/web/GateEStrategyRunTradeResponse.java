package com.guidinglight.nexusquant.app.web;

import com.guidinglight.nexusquant.core.model.StrategyRunTradeSummary;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * GateEStrategyRunTradeResponse 表示运行详情里的最小成交摘要返回。
 */
public record GateEStrategyRunTradeResponse(
        String tradeId,
        String exchangeTradeId,
        String exchangeOrderId,
        BigDecimal price,
        BigDecimal quantity,
        Instant tradeTs
) {
    public static GateEStrategyRunTradeResponse from(StrategyRunTradeSummary summary) {
        return new GateEStrategyRunTradeResponse(
                summary.tradeId(),
                summary.exchangeTradeId(),
                summary.exchangeOrderId(),
                summary.price(),
                summary.quantity(),
                summary.tradeTs()
        );
    }
}
