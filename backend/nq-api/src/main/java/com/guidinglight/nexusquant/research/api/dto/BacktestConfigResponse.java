package com.guidinglight.nexusquant.research.api.dto;

import com.guidinglight.nexusquant.research.domain.BacktestConfig;

import java.math.BigDecimal;
import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * BacktestConfigResponse 描述接口响应体。
 */
@Schema(name = "BacktestConfigResponse", description = "接口响应体")
public record BacktestConfigResponse(
        @Schema(description = "backtestConfigId")
        String backtestConfigId,
        @Schema(description = "researchConfigId")
        String researchConfigId,
        @Schema(description = "name")
        String name,
        @Schema(description = "description")
        String description,
        @Schema(description = "startTime")
        Instant startTime,
        @Schema(description = "endTime")
        Instant endTime,
        @Schema(description = "initialCapital")
        BigDecimal initialCapital,
        @Schema(description = "executionSpec")
        String executionSpec,
        @Schema(description = "evaluationSpec")
        String evaluationSpec,
        @Schema(description = "strategyVersionId")
        String strategyVersionId,
        @Schema(description = "strategyVersionSnapshotJson")
        String strategyVersionSnapshotJson,
        @Schema(description = "paramSnapshotJson")
        String paramSnapshotJson,
        @Schema(description = "configSnapshotJson")
        String configSnapshotJson,
        @Schema(description = "datasetId")
        String datasetId,
        @Schema(description = "datasetSnapshotJson")
        String datasetSnapshotJson,
        @Schema(description = "configSnapshot")
        String configSnapshot,
        @Schema(description = "createdAt")
        Instant createdAt,
        @Schema(description = "updatedAt")
        Instant updatedAt
) {
    public static BacktestConfigResponse from(BacktestConfig backtestConfig) {
        return new BacktestConfigResponse(
                backtestConfig.backtestConfigId(),
                backtestConfig.researchConfigId(),
                backtestConfig.name(),
                backtestConfig.description(),
                backtestConfig.startTime(),
                backtestConfig.endTime(),
                backtestConfig.initialCapital(),
                backtestConfig.executionSpec(),
                backtestConfig.evaluationSpec(),
                backtestConfig.strategyVersionId(),
                backtestConfig.strategyVersionSnapshotJson(),
                backtestConfig.paramSnapshotJson(),
                backtestConfig.configSnapshotJson(),
                backtestConfig.datasetId(),
                backtestConfig.datasetSnapshotJson(),
                backtestConfig.configSnapshot(),
                backtestConfig.createdAt(),
                backtestConfig.updatedAt()
        );
    }
}


