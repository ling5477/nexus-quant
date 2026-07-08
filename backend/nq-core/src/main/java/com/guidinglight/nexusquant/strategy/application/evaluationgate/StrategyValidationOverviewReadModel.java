package com.guidinglight.nexusquant.strategy.application.evaluationgate;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * StrategyValidationOverviewReadModel 是 GateS-3 runtime baseline 的只读响应合同。
 *
 * <p>该模型只表达 validation 层面的证据状态和安全边界；即使 decision 为 APPROVED，也必须保留
 * notTradingAuthorization=true，不能被解释成交易授权、LIVE ready 或真实 provider ready。
 */
public record StrategyValidationOverviewReadModel(
        Instant generatedAt,
        boolean diagnosticOnly,
        boolean noSideEffect,
        boolean notTradingAuthorization,
        boolean liveDisabled,
        boolean realProviderImplemented,
        boolean privateTradingImplemented,
        boolean aiDhRuntimeIntegrated,
        long totalStrategyVersions,
        long evaluatedStrategyVersions,
        long approvedForValidation,
        long rejectedForValidation,
        long needsReview,
        long blocked,
        LatestDecision latestDecision,
        List<BoundaryMessage> blockers,
        List<BoundaryMessage> warnings,
        List<NextStep> nextSteps,
        List<EvidenceAnchor> evidenceAnchors,
        String traceId
) {
    public StrategyValidationOverviewReadModel {
        generatedAt = Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        nextSteps = nextSteps == null ? List.of() : List.copyOf(nextSteps);
        evidenceAnchors = evidenceAnchors == null ? List.of() : List.copyOf(evidenceAnchors);
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
    }

    /**
     * 最新验证决策锚点；decision 只代表 validation evidence，不代表交易准入。
     */
    public record LatestDecision(
            String strategyVersionId,
            UUID datasetId,
            String evaluationReportId,
            String publishId,
            String paperRunId,
            UUID shadowRunId,
            StrategyValidationDecision decision,
            List<String> decisionReasons,
            List<String> limitations,
            Instant generatedAt,
            String traceId
    ) {
        public LatestDecision {
            decision = Objects.requireNonNull(decision, "decision must not be null");
            decisionReasons = decisionReasons == null ? List.of() : List.copyOf(decisionReasons);
            limitations = limitations == null ? List.of() : List.copyOf(limitations);
            generatedAt = Objects.requireNonNull(generatedAt, "generatedAt must not be null");
            if (traceId == null || traceId.isBlank()) {
                throw new IllegalArgumentException("traceId must not be blank");
            }
        }
    }

    /**
     * BoundaryMessage 描述 blocker/warning；不承载 credential、raw provider payload 或交易指令。
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
        }
    }

    /**
     * NextStep 仅描述后续审查或补证动作，不包含启动 runner、下单或真实 provider 操作。
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
     * EvidenceAnchor 只用于定位本地事实来源，不保存敏感字段或真实交易状态。
     */
    public record EvidenceAnchor(
            String sourceType,
            String sourceId,
            String sourceVersion,
            Instant sourceTimestamp,
            String checksum
    ) {
        public EvidenceAnchor {
            sourceType = required(sourceType, "sourceType");
            sourceId = sourceId == null || sourceId.isBlank() ? null : sourceId.trim();
            sourceVersion = sourceVersion == null || sourceVersion.isBlank() ? null : sourceVersion.trim();
            checksum = checksum == null || checksum.isBlank() ? null : checksum.trim();
        }
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
