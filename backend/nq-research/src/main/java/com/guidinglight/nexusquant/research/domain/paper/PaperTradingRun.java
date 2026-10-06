package com.guidinglight.nexusquant.research.domain.paper;

import java.time.Instant;

public record PaperTradingRun(
        String paperRunId,
        String publishId,
        String strategyVersionId,
        PaperTradingRunStatus status,
        String tradeEnv,
        String exchangeCode,
        String marketType,
        String symbol,
        String intervalCode,
        Instant startedAt,
        Instant stoppedAt,
        String publishSnapshotJson,
        String strategyVersionSnapshotJson,
        String datasetSnapshotJson,
        String paramSnapshotJson,
        String configSnapshotJson,
        String createdBy,
        Instant createdAt,
        Instant updatedAt,
        Long canonicalAccountId
) {
    /** 兼容原创建入口；canonical 绑定只由现有 SIM writer 完成。 */
    public PaperTradingRun(
        String paperRunId,
        String publishId,
        String strategyVersionId,
        PaperTradingRunStatus status,
        String tradeEnv,
        String exchangeCode,
        String marketType,
        String symbol,
        String intervalCode,
        Instant startedAt,
        Instant stoppedAt,
        String publishSnapshotJson,
        String strategyVersionSnapshotJson,
        String datasetSnapshotJson,
        String paramSnapshotJson,
        String configSnapshotJson,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
    ) {
        this(paperRunId, publishId, strategyVersionId, status, tradeEnv, exchangeCode, marketType, symbol, intervalCode, startedAt, stoppedAt, publishSnapshotJson, strategyVersionSnapshotJson, datasetSnapshotJson, paramSnapshotJson, configSnapshotJson, createdBy, createdAt, updatedAt, null);
    }
}
