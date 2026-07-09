package com.guidinglight.nexusquant.monitoring.application.incidentreview;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * IncidentReplayReviewOverviewReadModel 是 GateT-3 Incident / Replay Review Workflow 的只读响应合同。
 *
 * <p>职责：从 GateS-6 Incident / Replay facts、GateT-1 operator anchor 和 GateT-2 consistency
 * evidence anchor 派生 review item。该模型不持久化 review item，不创建 incident / alert / replay，
 * 不修改 Paper / Shadow / account / order / ledger 状态，不表示自动处置、真实 incident closeout 或交易授权。
 */
public record IncidentReplayReviewOverviewReadModel(
        Instant generatedAt,
        boolean diagnosticOnly,
        boolean noSideEffect,
        boolean notTradingAuthorization,
        boolean liveDisabled,
        boolean realProviderImplemented,
        boolean privateTradingImplemented,
        boolean aiDhRuntimeIntegrated,
        long totalReviewItems,
        long intakeCount,
        long evidenceReviewCount,
        long needsOperatorReviewCount,
        long acknowledgedRecommendationCount,
        long escalatedRecommendationCount,
        long closedRecommendationCount,
        long blockedCount,
        IncidentReplayReviewItem latestReviewItem,
        List<IncidentReplayReviewItem> reviewItems,
        Map<String, Long> severityBuckets,
        Map<String, Long> freshnessSummary,
        List<BoundaryMessage> blockers,
        List<BoundaryMessage> warnings,
        List<NextStep> nextSteps,
        List<EvidenceAnchor> evidenceAnchors,
        String traceId
) {
    public IncidentReplayReviewOverviewReadModel {
        generatedAt = Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        reviewItems = reviewItems == null ? List.of() : List.copyOf(reviewItems);
        severityBuckets = unmodifiableLinkedMap(severityBuckets);
        freshnessSummary = unmodifiableLinkedMap(freshnessSummary);
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        nextSteps = nextSteps == null ? List.of() : List.copyOf(nextSteps);
        evidenceAnchors = evidenceAnchors == null ? List.of() : List.copyOf(evidenceAnchors);
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
    }

    /**
     * IncidentReplayReviewItem 是从本地 facts 派生的 deterministic review item。
     *
     * <p>ACKNOWLEDGED_RECOMMENDATION、ESCALATED_RECOMMENDATION 和 CLOSED_RECOMMENDATION
     * 都只是人工复核建议，不是写侧状态；HIGH / CRITICAL 也只表示诊断优先级。
     */
    public record IncidentReplayReviewItem(
            String reviewItemId,
            String sourceType,
            String sourceId,
            String incidentEvidenceId,
            String replayRecordId,
            String shadowRunId,
            String paperRunId,
            String consistencyReportId,
            String operatorItemId,
            IncidentReplayReviewState reviewState,
            IncidentReplayReviewDecision reviewDecision,
            IncidentReplayReviewSeverity severity,
            IncidentReplayReviewFreshness evidenceFreshness,
            String summary,
            List<String> limitations,
            List<BoundaryMessage> blockers,
            List<BoundaryMessage> warnings,
            List<NextStep> nextSteps,
            List<EvidenceAnchor> evidenceAnchors,
            String traceId,
            Instant generatedAt,
            boolean diagnosticOnly,
            boolean noSideEffect,
            boolean notTradingAuthorization,
            boolean liveDisabled,
            boolean realProviderImplemented,
            boolean privateTradingImplemented,
            boolean aiDhRuntimeIntegrated
    ) {
        public IncidentReplayReviewItem {
            reviewItemId = required(reviewItemId, "reviewItemId");
            sourceType = required(sourceType, "sourceType");
            sourceId = optional(sourceId);
            incidentEvidenceId = optional(incidentEvidenceId);
            replayRecordId = optional(replayRecordId);
            shadowRunId = optional(shadowRunId);
            paperRunId = optional(paperRunId);
            consistencyReportId = optional(consistencyReportId);
            operatorItemId = optional(operatorItemId);
            reviewState = Objects.requireNonNull(reviewState, "reviewState must not be null");
            reviewDecision = Objects.requireNonNull(reviewDecision, "reviewDecision must not be null");
            severity = Objects.requireNonNull(severity, "severity must not be null");
            evidenceFreshness = Objects.requireNonNull(evidenceFreshness, "evidenceFreshness must not be null");
            summary = optional(summary);
            limitations = limitations == null ? List.of() : List.copyOf(limitations);
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
            nextSteps = nextSteps == null ? List.of() : List.copyOf(nextSteps);
            evidenceAnchors = evidenceAnchors == null ? List.of() : List.copyOf(evidenceAnchors);
            traceId = required(traceId, "traceId");
            generatedAt = Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        }
    }

    /** BoundaryMessage 描述 blocker / warning，不携带 raw payload、credential 或交易指令。 */
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

    /** NextStep 只描述人工复核、补证或保持阻断，不是执行、交易或处置命令。 */
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

    /** EvidenceAnchor 只定位本地 read-only fact source，不复制 JSONB 原文或 private payload。 */
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

    private static Map<String, Long> unmodifiableLinkedMap(Map<String, Long> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
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
