package com.guidinglight.nexusquant.research.domain.paper;

import java.math.BigDecimal;
import java.time.Instant;

public record PositionCurveSnapshot(
        String positionSnapshotId,
        String paperRunId,
        String symbol,
        Instant snapshotTime,
        BigDecimal quantity,
        BigDecimal avgPrice,
        BigDecimal markPrice,
        BigDecimal positionValue,
        BigDecimal unrealizedPnl,
        BigDecimal realizedPnl,
        String source,
        Instant createdAt
) {}
