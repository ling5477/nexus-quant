package com.guidinglight.nexusquant.monitoring.application.incident;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * IncidentReplayOverviewReadModel 是 GateS-6 Monitoring / Incident / Replay 只读响应合同。
 *
 * <p>该模型聚合本地 Shadow、Paper alert/recovery 和 replay 事实，只表达 incident-like
 * diagnostic priority。它不是 incident 创建系统，不生成 replay，不表示交易授权或 LIVE readiness。
 */
public record IncidentReplayOverviewReadModel(
        Instant generatedAt,
        boolean diagnosticOnly,
        boolean noSideEffect,
        boolean notTradingAuthorization,
        boolean liveDisabled,
        boolean realProviderImplemented,
        boolean privateTradingImplemented,
        boolean aiDhRuntimeIntegrated,
        long totalEvidenceItems,
        long shadowEventCount,
        long consistencyDivergenceCount,
        long paperAlertCount,
        long recoveryEventCount,
        long replayEventCount,
        List<LatestEvidence> latestEvidence,
        IncidentReplaySeverity incidentSeverity,
        List<BoundaryMessage> blockers,
        List<BoundaryMessage> warnings,
        List<NextStep> nextSteps,
        List<EvidenceAnchor> evidenceAnchors,
        String traceId
) {
    public IncidentReplayOverviewReadModel {
        generatedAt = Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        latestEvidence = latestEvidence == null ? List.of() : List.copyOf(latestEvidence);
        incidentSeverity = Objects.requireNonNull(incidentSeverity, "incidentSeverity must not be null");
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        nextSteps = nextSteps == null ? List.of() : List.copyOf(nextSteps);
        evidenceAnchors = evidenceAnchors == null ? List.of() : List.copyOf(evidenceAnchors);
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
    }

    /** LatestEvidence 是脱敏本地事实摘要，不包含 raw JSON、credential 或真实交易字段。 */
    public record LatestEvidence(
            String evidenceType,
            String sourceId,
            String sourceStatus,
            String summary,
            Instant occurredAt,
            String traceId
    ) {
        public LatestEvidence {
            evidenceType = required(evidenceType, "evidenceType");
            sourceId = optional(sourceId);
            sourceStatus = optional(sourceStatus);
            summary = optional(summary);
            traceId = optional(traceId);
        }
    }

    /** BoundaryMessage 描述只读边界或数据源警告，不承载敏感材料。 */
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

    /** NextStep 只描述后续补证或复核动作，不是创建 incident、启动 runner 或交易指令。 */
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

    /** EvidenceAnchor 只定位本地事实来源，不暴露内部 payload。 */
    public record EvidenceAnchor(
            String sourceType,
            String sourceId,
            String sourceVersion,
            Instant sourceTimestamp,
            String checksum
    ) {
        public EvidenceAnchor {
            sourceType = required(sourceType, "sourceType");
            sourceId = optional(sourceId);
            sourceVersion = optional(sourceVersion);
            checksum = optional(checksum);
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
