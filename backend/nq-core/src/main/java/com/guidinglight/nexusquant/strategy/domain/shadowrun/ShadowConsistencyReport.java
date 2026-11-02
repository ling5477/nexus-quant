package com.guidinglight.nexusquant.strategy.domain.shadowrun;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Paper vs Shadow 一致性复盘报告。
 *
 * <p>该 report 只表达脱敏差异分析、不可比原因和限制条件，不表达 approval、
 * trading authorization 或 live-ready。JSONB 字段在构造时做敏感字段禁入和结构检查。
 */
public record ShadowConsistencyReport(
        UUID id,
        UUID shadowRunId,
        String paperRunId,
        ShadowConsistencyComparisonStatus comparisonStatus,
        JsonNode metricDelta,
        JsonNode divergenceReasons,
        JsonNode limitations,
        Instant generatedAt,
        String traceId,
        Instant createdAt
) {

    public ShadowConsistencyReport {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(shadowRunId, "shadowRunId must not be null");
        Objects.requireNonNull(comparisonStatus, "comparisonStatus must not be null");
        ShadowRunSensitiveDataGuard.validateJson("metricDelta", metricDelta);
        ShadowRunSensitiveDataGuard.validateJson("divergenceReasons", divergenceReasons);
        ShadowRunSensitiveDataGuard.validateJson("limitations", limitations);
        ShadowRunJsonRules.requireObject(metricDelta, "metricDelta");
        ShadowRunJsonRules.requireArray(divergenceReasons, "divergenceReasons");
        ShadowRunJsonRules.requireArray(limitations, "limitations");
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        ShadowRun.requireText(traceId, "traceId");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
