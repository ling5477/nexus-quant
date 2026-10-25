package com.guidinglight.nexusquant.research.domain.paper;

import java.math.BigDecimal;
import java.time.Instant;

public record PaperTradingOrder(
        String paperOrderId,
        String paperRunId,
        String symbol,
        String side,
        String orderType,
        BigDecimal quantity,
        BigDecimal price,
        PaperOrderStatus status,
        String reason,
        String rawSignalJson,
        Instant createdAt,
        Instant updatedAt
) {}
