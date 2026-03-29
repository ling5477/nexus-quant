package com.guidinglight.nexusquant.research.api.dto;

import com.guidinglight.nexusquant.research.domain.backtest.SimTrade;

import java.math.BigDecimal;
import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * SimTradeResponse 描述接口响应体。
 */
@Schema(name = "SimTradeResponse", description = "接口响应体")
public record SimTradeResponse(
        @Schema(description = "simTradeId")
        String simTradeId,
        @Schema(description = "simOrderId")
        String simOrderId,
        @Schema(description = "backtestRunId")
        String backtestRunId,
        @Schema(description = "symbol")
        String symbol,
        @Schema(description = "side")
        String side,
        @Schema(description = "quantity")
        BigDecimal quantity,
        @Schema(description = "tradePrice")
        BigDecimal tradePrice,
        @Schema(description = "feeAmount")
        BigDecimal feeAmount,
        @Schema(description = "slippageAmount")
        BigDecimal slippageAmount,
        @Schema(description = "tradedAt")
        Instant tradedAt,
        @Schema(description = "createdAt")
        Instant createdAt,
        @Schema(description = "updatedAt")
        Instant updatedAt
) {
    public static SimTradeResponse from(SimTrade simTrade) {
        return new SimTradeResponse(
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


