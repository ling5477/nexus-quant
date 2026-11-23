package com.guidinglight.nexusquant.research.api.dto;

import com.guidinglight.nexusquant.research.domain.backtest.SimPosition;

import java.math.BigDecimal;
import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * SimPositionResponse 描述接口响应体。
 */
@Schema(name = "SimPositionResponse", description = "接口响应体")
public record SimPositionResponse(
        @Schema(description = "simPositionId")
        String simPositionId,
        @Schema(description = "backtestRunId")
        String backtestRunId,
        @Schema(description = "symbol")
        String symbol,
        @Schema(description = "quantity")
        BigDecimal quantity,
        @Schema(description = "averageEntryPrice")
        BigDecimal averageEntryPrice,
        @Schema(description = "realizedPnl")
        BigDecimal realizedPnl,
        @Schema(description = "createdAt")
        Instant createdAt,
        @Schema(description = "updatedAt")
        Instant updatedAt
) {
    public static SimPositionResponse from(SimPosition simPosition) {
        return new SimPositionResponse(
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


