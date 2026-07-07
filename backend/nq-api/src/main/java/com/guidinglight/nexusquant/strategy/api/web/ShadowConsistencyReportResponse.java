package com.guidinglight.nexusquant.strategy.api.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowConsistencyReport;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunSensitiveDataGuard;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

/**
 * ShadowConsistencyReportResponse 是最新 Paper vs Shadow consistency report 的只读 DTO。
 *
 * <p>该 DTO 只返回 metricDelta、divergenceReasons 和 limitations 的脱敏诊断事实；comparisonStatus
 * 不是 approval、trading authorization 或 LIVE readiness。
 */
@Schema(name = "ShadowConsistencyReportResponse", description = "GateR-6 read-only latest consistency report")
public record ShadowConsistencyReportResponse(
        UUID id,
        UUID shadowRunId,
        String paperRunId,
        String comparisonStatus,
        JsonNode metricDelta,
        JsonNode divergenceReasons,
        JsonNode limitations,
        Instant generatedAt,
        String traceId
) {
    public static ShadowConsistencyReportResponse from(ShadowConsistencyReport report) {
        ShadowRunSensitiveDataGuard.validateJson("metricDelta", report.metricDelta());
        ShadowRunSensitiveDataGuard.validateJson("divergenceReasons", report.divergenceReasons());
        ShadowRunSensitiveDataGuard.validateJson("limitations", report.limitations());
        return new ShadowConsistencyReportResponse(
                report.id(),
                report.shadowRunId(),
                report.paperRunId(),
                report.comparisonStatus().name(),
                report.metricDelta(),
                report.divergenceReasons(),
                report.limitations(),
                report.generatedAt(),
                report.traceId()
        );
    }
}
