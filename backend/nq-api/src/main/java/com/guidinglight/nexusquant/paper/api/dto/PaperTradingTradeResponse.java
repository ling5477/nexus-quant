package com.guidinglight.nexusquant.paper.api.dto;

import com.guidinglight.nexusquant.research.domain.paper.PaperTradingTrade;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "PaperTradingTradeResponse", description = "GateI-3 Paper Trading 成交事实响应体")
public record PaperTradingTradeResponse(
        String paperTradeId,
        String paperOrderId,
        String paperRunId,
        String symbol,
        String side,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal fee,
        Instant tradedAt,
        Instant createdAt
) {
    public static PaperTradingTradeResponse from(PaperTradingTrade trade) {
        return new PaperTradingTradeResponse(
                trade.paperTradeId(),
                trade.paperOrderId(),
                trade.paperRunId(),
                trade.symbol(),
                trade.side(),
                trade.quantity(),
                trade.price(),
                trade.fee(),
                trade.tradedAt(),
                trade.createdAt()
        );
    }
}
