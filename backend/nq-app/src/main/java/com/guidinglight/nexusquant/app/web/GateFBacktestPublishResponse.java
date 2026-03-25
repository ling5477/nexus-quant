package com.guidinglight.nexusquant.app.web;

import com.guidinglight.nexusquant.research.model.BacktestPublishRecord;

import java.time.Instant;

/**
 * GateFBacktestPublishResponse 是发布结果响应体。
 */
public record GateFBacktestPublishResponse(
        String publishRecordId,
        String backtestRunId,
        String researchConfigId,
        String backtestConfigId,
        String sourceStrategyId,
        String targetStrategyDefinitionId,
        String publishStatus,
        String publishName,
        Instant publishedAt,
        String evaluationSummaryJson,
        String failureCode,
        String failureMessage,
        String publishSnapshotJson
) {
    public static GateFBacktestPublishResponse from(BacktestPublishRecord record) {
        return new GateFBacktestPublishResponse(
                record.publishRecordId(),
                record.backtestRunId(),
                record.researchConfigId(),
                record.backtestConfigId(),
                record.sourceStrategyId(),
                record.targetStrategyDefinitionId(),
                record.publishStatus().name(),
                record.publishName(),
                record.publishedAt(),
                record.evaluationSummaryJson(),
                record.failureCode(),
                record.failureMessage(),
                record.publishSnapshotJson()
        );
    }
}
