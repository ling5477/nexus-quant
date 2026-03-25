package com.guidinglight.nexusquant.app.web;

import com.guidinglight.nexusquant.backtest.model.SimPnlSnapshot;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * GateFSimPnlSnapshotResponse 是模拟 PnL 快照响应体。
 */
public record GateFSimPnlSnapshotResponse(
        String simPnlSnapshotId,
        String backtestRunId,
        Instant snapshotTime,
        BigDecimal cashBalance,
        BigDecimal positionMarketValue,
        BigDecimal realizedPnl,
        BigDecimal unrealizedPnl,
        BigDecimal totalFee,
        BigDecimal totalSlippage,
        BigDecimal equity,
        BigDecimal netPnl,
        Instant createdAt
) {
    public static GateFSimPnlSnapshotResponse from(SimPnlSnapshot simPnlSnapshot) {
        return new GateFSimPnlSnapshotResponse(
                simPnlSnapshot.simPnlSnapshotId(),
                simPnlSnapshot.backtestRunId(),
                simPnlSnapshot.snapshotTime(),
                simPnlSnapshot.cashBalance(),
                simPnlSnapshot.positionMarketValue(),
                simPnlSnapshot.realizedPnl(),
                simPnlSnapshot.unrealizedPnl(),
                simPnlSnapshot.totalFee(),
                simPnlSnapshot.totalSlippage(),
                simPnlSnapshot.equity(),
                simPnlSnapshot.netPnl(),
                simPnlSnapshot.createdAt()
        );
    }
}
