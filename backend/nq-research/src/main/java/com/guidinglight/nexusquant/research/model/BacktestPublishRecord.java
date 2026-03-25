package com.guidinglight.nexusquant.research.model;

import java.time.Instant;

/**
 * BacktestPublishRecord 表示 GateF-5 的发布事实。
 */
public record BacktestPublishRecord(
        String publishRecordId,
        String backtestRunId,
        String researchConfigId,
        String backtestConfigId,
        String sourceStrategyId,
        String evalReportId,
        String targetStrategyDefinitionId,
        PublishStatus publishStatus,
        String publishName,
        String publishSnapshotJson,
        String evaluationSummaryJson,
        String failureCode,
        String failureMessage,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public PublishSummary toSummary() {
        return new PublishSummary(
                publishStatus,
                publishedAt,
                targetStrategyDefinitionId,
                publishName,
                backtestRunId
        );
    }
}
