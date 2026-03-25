package com.guidinglight.nexusquant.api.web;

import com.guidinglight.nexusquant.research.model.BacktestPublishRecord;

import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * BacktestPublishResponse 描述接口响应体。
 */
@Schema(name = "BacktestPublishResponse", description = "接口响应体")
public record BacktestPublishResponse(
        @Schema(description = "publishRecordId")
        String publishRecordId,
        @Schema(description = "backtestRunId")
        String backtestRunId,
        @Schema(description = "researchConfigId")
        String researchConfigId,
        @Schema(description = "backtestConfigId")
        String backtestConfigId,
        @Schema(description = "sourceStrategyId")
        String sourceStrategyId,
        @Schema(description = "targetStrategyDefinitionId")
        String targetStrategyDefinitionId,
        @Schema(description = "publishStatus")
        String publishStatus,
        @Schema(description = "publishName")
        String publishName,
        @Schema(description = "publishedAt")
        Instant publishedAt,
        @Schema(description = "evaluationSummaryJson")
        String evaluationSummaryJson,
        @Schema(description = "failureCode")
        String failureCode,
        @Schema(description = "failureMessage")
        String failureMessage,
        @Schema(description = "publishSnapshotJson")
        String publishSnapshotJson
) {
    public static BacktestPublishResponse from(BacktestPublishRecord record) {
        return new BacktestPublishResponse(
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
