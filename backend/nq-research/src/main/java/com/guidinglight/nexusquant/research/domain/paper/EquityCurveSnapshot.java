package com.guidinglight.nexusquant.research.domain.paper;

import com.guidinglight.nexusquant.common.currency.ValuationMetadata;

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
        Instant createdAt,
        ValuationMetadata valuation) {
    public EquityCurveSnapshot {
        if (valuation == null) throw new IllegalArgumentException("VALUATION_METADATA_REQUIRED");
        if (valuation.at() != null && !valuation.at().equals(snapshotTime)) {
            throw new IllegalArgumentException("VALUATION_SNAPSHOT_TIME_MISMATCH");
        }
    }

    /** 旧记录和既有读取调用保持兼容；构造UNKNOWN不授予正式新写入资格。 */
    public EquityCurveSnapshot(
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
            Instant createdAt) {
        this(
                equitySnapshotId,
                paperRunId,
                snapshotTime,
                totalEquity,
                cashBalance,
                positionValue,
                unrealizedPnl,
                realizedPnl,
                drawdown,
                source,
                createdAt,
                ValuationMetadata.unknown());
    }
}
