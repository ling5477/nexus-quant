package com.guidinglight.nexusquant.strategy.application.evaluationgate;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * StrategyEvaluationGate 是 GateQ-1 后端只读 baseline 的 API read model。
 *
 * <p>Why: 该模型只聚合既有研究、评估、发布和 Paper 证据，回答“是否可进入后续 Shadow review”。
 * 它不包含 tradingReady / liveReady / authorizedForTrading 字段，也不保存 credential、secret、token、
 * passphrase、private key、raw provider payload 或任何交易执行材料。
 */
public record StrategyEvaluationGate(
        StrategyEvaluationGateScope scope,
        String strategyId,
        String strategyVersionId,
        UUID datasetId,
        String evaluationId,
        String publishId,
        String paperRunId,
        StrategyEvaluationGateStatus gateStatus,
        StrategyEvaluationGateDecision gateDecision,
        String evaluationStatus,
        String datasetQualityStatus,
        String paperEvidenceStatus,
        String publishTraceStatus,
        List<StrategyEvaluationGateEvidence> requiredEvidence,
        List<StrategyEvaluationGateEvidence> missingEvidence,
        List<StrategyEvaluationGateReason> blockers,
        List<StrategyEvaluationGateReason> warnings,
        List<String> nextSteps,
        Instant generatedAt
) {
    public StrategyEvaluationGate {
        scope = Objects.requireNonNull(scope, "scope must not be null");
        gateStatus = Objects.requireNonNull(gateStatus, "gateStatus must not be null");
        gateDecision = Objects.requireNonNull(gateDecision, "gateDecision must not be null");
        evaluationStatus = normalizeStatus(evaluationStatus);
        datasetQualityStatus = normalizeStatus(datasetQualityStatus);
        paperEvidenceStatus = normalizeStatus(paperEvidenceStatus);
        publishTraceStatus = normalizeStatus(publishTraceStatus);
        requiredEvidence = requiredEvidence == null ? List.of() : List.copyOf(requiredEvidence);
        missingEvidence = missingEvidence == null ? List.of() : List.copyOf(missingEvidence);
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        nextSteps = nextSteps == null ? List.of() : List.copyOf(nextSteps);
        generatedAt = Objects.requireNonNull(generatedAt, "generatedAt must not be null");
    }

    private static String normalizeStatus(String status) {
        return status == null || status.isBlank() ? "NOT_AVAILABLE" : status.trim();
    }
}
