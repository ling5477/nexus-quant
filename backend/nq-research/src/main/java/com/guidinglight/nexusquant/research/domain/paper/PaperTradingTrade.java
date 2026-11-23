package com.guidinglight.nexusquant.research.domain.paper;

import java.math.BigDecimal;
import java.time.Instant;

public record PaperTradingTrade(
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
) {}
