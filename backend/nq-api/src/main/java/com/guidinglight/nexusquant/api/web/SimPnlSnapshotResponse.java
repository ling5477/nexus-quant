package com.guidinglight.nexusquant.api.web;

import com.guidinglight.nexusquant.backtest.model.SimPnlSnapshot;

import java.math.BigDecimal;
import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * SimPnlSnapshotResponse 描述接口响应体。
 */
@Schema(name = "SimPnlSnapshotResponse", description = "接口响应体")
public record SimPnlSnapshotResponse(
        @Schema(description = "simPnlSnapshotId")
        String simPnlSnapshotId,
        @Schema(description = "backtestRunId")
        String backtestRunId,
        @Schema(description = "snapshotTime")
        Instant snapshotTime,
        @Schema(description = "cashBalance")
        BigDecimal cashBalance,
        @Schema(description = "positionMarketValue")
        BigDecimal positionMarketValue,
        @Schema(description = "realizedPnl")
        BigDecimal realizedPnl,
        @Schema(description = "unrealizedPnl")
        BigDecimal unrealizedPnl,
        @Schema(description = "totalFee")
        BigDecimal totalFee,
        @Schema(description = "totalSlippage")
        BigDecimal totalSlippage,
        @Schema(description = "equity")
        BigDecimal equity,
        @Schema(description = "netPnl")
        BigDecimal netPnl,
        @Schema(description = "createdAt")
        Instant createdAt
) {
    public static SimPnlSnapshotResponse from(SimPnlSnapshot simPnlSnapshot) {
        return new SimPnlSnapshotResponse(
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
