package com.guidinglight.nexusquant.strategy.api.web;

import com.guidinglight.nexusquant.strategy.domain.StrategyRunTradeSummary;

import java.math.BigDecimal;
import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * StrategyRunTradeResponse 描述接口响应体。
 */
@Schema(name = "StrategyRunTradeResponse", description = "接口响应体")
public record StrategyRunTradeResponse(
        @Schema(description = "tradeId")
        String tradeId,
        @Schema(description = "exchangeTradeId")
        String exchangeTradeId,
        @Schema(description = "exchangeOrderId")
        String exchangeOrderId,
        @Schema(description = "price")
        BigDecimal price,
        @Schema(description = "quantity")
        BigDecimal quantity,
        @Schema(description = "tradeTs")
        Instant tradeTs
) {
    public static StrategyRunTradeResponse from(StrategyRunTradeSummary summary) {
        return new StrategyRunTradeResponse(
                summary.tradeId(),
                summary.exchangeTradeId(),
                summary.exchangeOrderId(),
                summary.price(),
                summary.quantity(),
                summary.tradeTs()
        );
    }
}



