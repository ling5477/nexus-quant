package com.guidinglight.nexusquant.strategy.application.shadowvalidation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * ShadowValidationWorkflowOverviewReadModel 是 GateT-1 Shadow Validation Workflow 的只读响应合同。
 *
 * <p>该模型只表达 operator 可复核的本地诊断 evidence。所有 operator item 均为 derived / deterministic，
 * 不持久化，不触发 runner / scheduler / adapter，也不代表交易授权。overview 与 item 级 safety flags 必须
 * 固定保持 fail-closed 语义。
 */
public record ShadowValidationWorkflowOverviewReadModel(
        Instant generatedAt,
        boolean diagnosticOnly,
        boolean noSideEffect,
        boolean notTradingAuthorization,
        boolean liveDisabled,
        boolean realProviderImplemented,
        boolean privateTradingImplemented,
        boolean aiDhRuntimeIntegrated,
        long totalOperatorItems,
        long intakeCount,
        long evidenceReviewCount,
        long needsEvidenceCount,
        long readyForOperatorReviewCount,
        long blockedCount,
        long closedRecommendationCount,
        OperatorItem latestOperatorItem,
        List<OperatorItem> operatorItems,
        List<BoundaryMessage> blockers,
        List<BoundaryMessage> warnings,
        List<NextStep> nextSteps,
        List<EvidenceAnchor> evidenceAnchors,
        String traceId
) {
    public ShadowValidationWorkflowOverviewReadModel {
        generatedAt = Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        operatorItems = operatorItems == null ? List.of() : List.copyOf(operatorItems);
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        nextSteps = nextSteps == null ? List.of() : List.copyOf(nextSteps);
        evidenceAnchors = evidenceAnchors == null ? List.of() : List.copyOf(evidenceAnchors);
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
    }

    /**
     * OperatorItem 是从本地 fact source 派生的人工复核条目。
     *
     * <p>字段仅用于定位 evidence、解释当前 workflowState / validationDecision 和给出下一步诊断动作。
     * 即使 validationDecision=VALIDATION_READY，也必须固定 notTradingAuthorization=true。
     */
    public record OperatorItem(
            String operatorItemId,
            String sourceType,
            String sourceId,
            String strategyVersionId,
            UUID datasetId,
            String evaluationReportId,
            String paperRunId,
            UUID shadowRunId,
            UUID consistencyReportId,
            String incidentEvidenceId,
            ShadowValidationWorkflowState workflowState,
            ShadowValidationWorkflowValidationDecision validationDecision,
            ShadowValidationWorkflowSeverity severity,
            ShadowValidationWorkflowEvidenceFreshness evidenceFreshness,
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
        public OperatorItem {
            operatorItemId = required(operatorItemId, "operatorItemId");
            sourceType = required(sourceType, "sourceType");
            sourceId = required(sourceId, "sourceId");
            workflowState = Objects.requireNonNull(workflowState, "workflowState must not be null");
            validationDecision = Objects.requireNonNull(validationDecision, "validationDecision must not be null");
            severity = Objects.requireNonNull(severity, "severity must not be null");
            evidenceFreshness = Objects.requireNonNull(evidenceFreshness, "evidenceFreshness must not be null");
            blockers = blockers == null ? List.of() : List.copyOf(blockers);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
            nextSteps = nextSteps == null ? List.of() : List.copyOf(nextSteps);
            evidenceAnchors = evidenceAnchors == null ? List.of() : List.copyOf(evidenceAnchors);
            traceId = required(traceId, "traceId");
            generatedAt = Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        }
    }

    /**
     * BoundaryMessage 描述 blocker / warning，不携带 raw payload、credential 或真实交易字段。
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
     * NextStep 只描述人工复核、补证或工程检查动作，不是执行、交易、下单或自动处置指令。
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
     * EvidenceAnchor 定位本地事实来源；不暴露 JSONB 原文或 provider payload。
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
