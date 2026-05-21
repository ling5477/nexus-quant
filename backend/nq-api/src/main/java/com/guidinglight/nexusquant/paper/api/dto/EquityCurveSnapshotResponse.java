package com.guidinglight.nexusquant.paper.api.dto;

import com.guidinglight.nexusquant.research.domain.paper.EquityCurveSnapshot;

import java.math.BigDecimal;
import java.time.Instant;

public record EquityCurveSnapshotResponse(
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
) {
    public static EquityCurveSnapshotResponse from(EquityCurveSnapshot s) {
        return new EquityCurveSnapshotResponse(
                s.equitySnapshotId(), s.paperRunId(), s.snapshotTime(),
                s.totalEquity(), s.cashBalance(), s.positionValue(),
                s.unrealizedPnl(), s.realizedPnl(), s.drawdown(),
                s.source(), s.createdAt());
    }
}
