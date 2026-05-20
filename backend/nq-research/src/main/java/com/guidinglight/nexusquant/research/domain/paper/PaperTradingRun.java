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
        Instant updatedAt
) {}
