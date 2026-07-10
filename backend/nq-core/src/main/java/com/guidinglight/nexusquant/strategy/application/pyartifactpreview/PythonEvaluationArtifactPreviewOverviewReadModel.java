package com.guidinglight.nexusquant.strategy.application.pyartifactpreview;

import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * PythonEvaluationArtifactPreviewOverviewReadModel 是 GateT-4 No-file baseline 的只读响应合同。
 *
 * <p>职责：表达 Python offline EvaluationArtifact binding preview 的安全空基线。该模型只说明当前没有
 * artifact source，并固定 diagnostic-only / no-side-effect / not-trading-authorization 边界；不表示 artifact
 * 已导入 Java 事实源、不表示 ML ready、不表示 live execution ready，也不表示交易授权。
 */
public record PythonEvaluationArtifactPreviewOverviewReadModel(
        Instant generatedAt,
        ReadModelEvidenceMetadata evidenceMetadata,
        boolean diagnosticOnly,
        boolean noSideEffect,
        boolean notTradingAuthorization,
        boolean liveDisabled,
        boolean realProviderImplemented,
        boolean privateTradingImplemented,
        boolean aiDhRuntimeIntegrated,
        boolean pythonMlReady,
        boolean pythonLiveExecutionReady,
        long totalArtifactPreviews,
        long validArtifactCount,
        long invalidArtifactCount,
        long staleArtifactCount,
        long checksumFailedCount,
        PythonEvaluationArtifactPreviewItem latestArtifactPreview,
        List<PythonEvaluationArtifactPreviewItem> artifactPreviews,
        Map<String, Long> schemaVersionSummary,
        Map<String, Long> checksumSummary,
        Map<String, Long> metricSummaryCoverage,
        List<BoundaryMessage> blockers,
        List<BoundaryMessage> warnings,
        List<NextStep> nextSteps,
        List<EvidenceAnchor> evidenceAnchors,
        String traceId
) {
    public PythonEvaluationArtifactPreviewOverviewReadModel {
        generatedAt = Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        evidenceMetadata = Objects.requireNonNull(evidenceMetadata, "evidenceMetadata must not be null");
        artifactPreviews = artifactPreviews == null ? List.of() : List.copyOf(artifactPreviews);
        schemaVersionSummary = schemaVersionSummary == null ? Map.of() : Map.copyOf(schemaVersionSummary);
        checksumSummary = checksumSummary == null ? Map.of() : Map.copyOf(checksumSummary);
        metricSummaryCoverage = metricSummaryCoverage == null ? Map.of() : Map.copyOf(metricSummaryCoverage);
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        nextSteps = nextSteps == null ? List.of() : List.copyOf(nextSteps);
        evidenceAnchors = evidenceAnchors == null ? List.of() : List.copyOf(evidenceAnchors);
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        if (!diagnosticOnly) {
            throw new IllegalArgumentException("diagnosticOnly must be true");
        }
        if (!noSideEffect) {
            throw new IllegalArgumentException("noSideEffect must be true");
        }
        if (!notTradingAuthorization) {
            throw new IllegalArgumentException("notTradingAuthorization must be true");
        }
        if (!liveDisabled) {
            throw new IllegalArgumentException("liveDisabled must be true");
        }
        if (realProviderImplemented) {
            throw new IllegalArgumentException("realProviderImplemented must be false");
        }
        if (privateTradingImplemented) {
            throw new IllegalArgumentException("privateTradingImplemented must be false");
        }
        if (aiDhRuntimeIntegrated) {
            throw new IllegalArgumentException("aiDhRuntimeIntegrated must be false");
        }
        if (pythonMlReady) {
            throw new IllegalArgumentException("pythonMlReady must be false");
        }
        if (pythonLiveExecutionReady) {
            throw new IllegalArgumentException("pythonLiveExecutionReady must be false");
        }
    }

    /**
     * PythonEvaluationArtifactPreviewItem 是后续 Manifest-only reader 才可能生成的 derived item。
     *
     * <p>GateT-4 No-file baseline 返回空列表；字段预留用于后续只读 manifest/source review，不是 DB entity、
     * artifact import record、strategy evaluation result、publish approval 或交易授权记录。
     */
    public record PythonEvaluationArtifactPreviewItem(
            String artifactPreviewId,
            String artifactId,
            String experimentId,
            String strategyId,
            String strategyVersion,
            String strategyVersionId,
            String datasetId,
            String datasetVersion,
            String parameterSetId,
            String schemaVersion,
            String source,
            PythonEvaluationArtifactChecksumStatus checksumStatus,
            PythonEvaluationArtifactFreshness artifactFreshness,
            PythonEvaluationArtifactMetricSummaryStatus metricSummaryStatus,
            String costAssumptionsStatus,
            String slippageAssumptionsStatus,
            List<String> validationWarnings,
            List<String> limitations,
            List<EvidenceAnchor> evidenceAnchors,
            String traceId,
            Instant generatedAt,
            boolean diagnosticOnly,
            boolean noSideEffect,
            boolean notTradingAuthorization,
            boolean liveExecutionReady,
            boolean pythonMlReady,
            boolean pythonLiveExecutionReady
    ) {
        public PythonEvaluationArtifactPreviewItem {
            artifactPreviewId = required(artifactPreviewId, "artifactPreviewId");
            schemaVersion = optional(schemaVersion);
            source = optional(source);
            checksumStatus = Objects.requireNonNull(checksumStatus, "checksumStatus must not be null");
            artifactFreshness = Objects.requireNonNull(artifactFreshness, "artifactFreshness must not be null");
            metricSummaryStatus = Objects.requireNonNull(metricSummaryStatus, "metricSummaryStatus must not be null");
            validationWarnings = validationWarnings == null ? List.of() : List.copyOf(validationWarnings);
            limitations = limitations == null ? List.of() : List.copyOf(limitations);
            evidenceAnchors = evidenceAnchors == null ? List.of() : List.copyOf(evidenceAnchors);
            traceId = required(traceId, "traceId");
            generatedAt = Objects.requireNonNull(generatedAt, "generatedAt must not be null");
            if (!diagnosticOnly) {
                throw new IllegalArgumentException("diagnosticOnly must be true");
            }
            if (!noSideEffect) {
                throw new IllegalArgumentException("noSideEffect must be true");
            }
            if (!notTradingAuthorization) {
                throw new IllegalArgumentException("notTradingAuthorization must be true");
            }
            if (liveExecutionReady) {
                throw new IllegalArgumentException("liveExecutionReady must be false");
            }
            if (pythonMlReady) {
                throw new IllegalArgumentException("pythonMlReady must be false");
            }
            if (pythonLiveExecutionReady) {
                throw new IllegalArgumentException("pythonLiveExecutionReady must be false");
            }
        }
    }

    /**
     * BoundaryMessage 描述 blocker / warning，不携带敏感材料或本地路径。
     */
    public record BoundaryMessage(
            String code,
            String severity,
            String message,
            String sourceType,
            String sourceId
    ) {
        public BoundaryMessage {
            code = required(code, "code");
            severity = required(severity, "severity");
            message = required(message, "message");
            sourceType = required(sourceType, "sourceType");
            sourceId = optional(sourceId);
        }
    }

    /**
     * NextStep 只描述后续人工复核或另起任务，不是上传、导入、执行、发布或交易动作。
     */
    public record NextStep(
            String code,
            String owner,
            String action,
            String completionCondition,
            boolean boundaryCritical
    ) {
        public NextStep {
            code = required(code, "code");
            owner = required(owner, "owner");
            action = required(action, "action");
            completionCondition = required(completionCondition, "completionCondition");
        }
    }

    /**
     * EvidenceAnchor 只定位只读事实来源，不暴露 artifact 文件路径、raw JSON、credential 或 private payload。
     */
    public record EvidenceAnchor(
            String sourceType,
            String sourceId,
            String sourceVersion,
            Instant sourceTimestamp,
            String traceId,
            String description
    ) {
        public EvidenceAnchor {
            sourceType = required(sourceType, "sourceType");
            sourceId = optional(sourceId);
            sourceVersion = optional(sourceVersion);
            traceId = optional(traceId);
            description = optional(description);
        }
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
