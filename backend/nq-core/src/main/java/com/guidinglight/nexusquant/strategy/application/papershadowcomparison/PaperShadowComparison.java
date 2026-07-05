package com.guidinglight.nexusquant.strategy.application.papershadowcomparison;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * PaperShadowComparison 是 GateQ-2 Paper vs Shadow 只读 API read model。
 *
 * <p>Why: 该模型只聚合现有 strategy version、dataset、evaluation、publish、Paper run 事实，
 * 并显式表达 Shadow runner / Shadow run 当前缺失或未实现。它不包含 tradingReady、liveReady、
 * authorizedForTrading，也不保存敏感材料、raw provider payload 或交易执行材料。
 */
public record PaperShadowComparison(
        PaperShadowComparisonScope scope,
        String strategyId,
        String strategyVersionId,
        UUID datasetId,
        String evaluationId,
        String publishId,
        String paperRunId,
        String shadowRunId,
        String paperRunStatus,
        String shadowRunStatus,
        PaperShadowComparisonStatus comparisonStatus,
        String evaluationGateStatus,
        String paperEvidenceStatus,
        String shadowEvidenceStatus,
        String dataQualityStatus,
        boolean comparable,
        List<PaperShadowComparisonEvidence> requiredEvidence,
        List<PaperShadowComparisonEvidence> missingEvidence,
        List<PaperShadowComparisonReason> blockers,
        List<PaperShadowComparisonReason> warnings,
        List<String> nextSteps,
        Instant generatedAt
) {
    public PaperShadowComparison {
        scope = Objects.requireNonNull(scope, "scope must not be null");
        paperRunStatus = normalizeStatus(paperRunStatus);
        shadowRunStatus = normalizeStatus(shadowRunStatus);
        comparisonStatus = Objects.requireNonNull(comparisonStatus, "comparisonStatus must not be null");
        evaluationGateStatus = normalizeStatus(evaluationGateStatus);
        paperEvidenceStatus = normalizeStatus(paperEvidenceStatus);
        shadowEvidenceStatus = normalizeStatus(shadowEvidenceStatus);
        dataQualityStatus = normalizeStatus(dataQualityStatus);
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
