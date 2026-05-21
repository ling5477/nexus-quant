package com.guidinglight.nexusquant.paper.api.dto;

import com.guidinglight.nexusquant.research.domain.paper.PositionCurveSnapshot;

import java.math.BigDecimal;
import java.time.Instant;

public record PositionCurveSnapshotResponse(
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
) {
    public static PositionCurveSnapshotResponse from(PositionCurveSnapshot s) {
        return new PositionCurveSnapshotResponse(
                s.positionSnapshotId(), s.paperRunId(), s.symbol(), s.snapshotTime(),
                s.quantity(), s.avgPrice(), s.markPrice(), s.positionValue(),
                s.unrealizedPnl(), s.realizedPnl(), s.source(), s.createdAt());
    }
}
