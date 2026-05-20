package com.guidinglight.nexusquant.paper.api.dto;

import com.guidinglight.nexusquant.research.domain.paper.PaperTradingPosition;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "PaperTradingPositionResponse", description = "GateI-3 Paper Trading 持仓事实响应体")
public record PaperTradingPositionResponse(
        String paperPositionId,
        String paperRunId,
        String symbol,
        BigDecimal quantity,
        BigDecimal avgPrice,
        BigDecimal unrealizedPnl,
        BigDecimal realizedPnl,
        Instant updatedAt,
        Instant createdAt
) {
    public static PaperTradingPositionResponse from(PaperTradingPosition position) {
        return new PaperTradingPositionResponse(
                position.paperPositionId(),
                position.paperRunId(),
                position.symbol(),
                position.quantity(),
                position.avgPrice(),
                position.unrealizedPnl(),
                position.realizedPnl(),
                position.updatedAt(),
                position.createdAt()
        );
    }
}
