package com.guidinglight.nexusquant.research.domain.paper;

import java.math.BigDecimal;
import java.time.Instant;

public record PaperTradingPosition(
        String paperPositionId,
        String paperRunId,
        String symbol,
        BigDecimal quantity,
        BigDecimal avgPrice,
        BigDecimal unrealizedPnl,
        BigDecimal realizedPnl,
        Instant updatedAt,
        Instant createdAt
) {}
