package com.guidinglight.nexusquant.paper.api.dto;

import com.guidinglight.nexusquant.research.domain.paper.PaperTradingOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(name = "PaperTradingOrderResponse", description = "GateI-3 Paper Trading 订单事实响应体")
public record PaperTradingOrderResponse(
        String paperOrderId,
        String paperRunId,
        String symbol,
        String side,
        String orderType,
        BigDecimal quantity,
        BigDecimal price,
        String status,
        String reason,
        String rawSignalJson,
        Instant createdAt,
        Instant updatedAt
) {
    public static PaperTradingOrderResponse from(PaperTradingOrder order) {
        return new PaperTradingOrderResponse(
                order.paperOrderId(),
                order.paperRunId(),
                order.symbol(),
                order.side(),
                order.orderType(),
                order.quantity(),
                order.price(),
                order.status().name(),
                order.reason(),
                order.rawSignalJson(),
                order.createdAt(),
                order.updatedAt()
        );
    }
}
