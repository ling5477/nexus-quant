package com.guidinglight.nexusquant.research.domain.paper;

import java.math.BigDecimal;
import java.time.Instant;

public record EquityCurveSnapshot(
        String equitySnapshotId,
        String paperRunId,
        Instant snapshotTime,
        BigDecimal totalEquity,
        BigDecimal cashBalance,
        BigDecimal positionValue,
        BigDecimal unrealizedPnl,
        BigDecimal realizedPnl,
        BigDecimal drawdown,
        String source,
        Instant createdAt
) {}
