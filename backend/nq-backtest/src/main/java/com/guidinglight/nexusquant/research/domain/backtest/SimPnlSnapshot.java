package com.guidinglight.nexusquant.research.domain.backtest;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * SimPnlSnapshot 表示回测运行中的最小权益与 PnL 快照。
 */
public record SimPnlSnapshot(
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
}

