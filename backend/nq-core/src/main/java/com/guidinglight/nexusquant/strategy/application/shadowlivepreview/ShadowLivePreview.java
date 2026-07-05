package com.guidinglight.nexusquant.strategy.application.shadowlivepreview;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * ShadowLivePreview 是 GateQ-3 Shadow Live no-side-effect preview API 的 core read model。
 *
 * <p>Why: 该模型只返回 validation、readiness、trace preview、blocked reason、side-effect policy
 * 和 next steps。它不包含 tradingReady、liveReady、authorizedForTrading 字段，不保存敏感材料、
 * raw provider payload、真实执行建议或任何交易状态 mutation。
 */
public record ShadowLivePreview(
        ShadowLivePreviewScope scope,
        String strategyId,
        String strategyVersionId,
        UUID datasetId,
        String evaluationId,
        String publishId,
        String paperRunId,
        String shadowRunId,
        String runnerStatus,
        ShadowLivePreviewStatus previewStatus,
        String evaluationGateStatus,
        String paperShadowComparisonStatus,
        List<ShadowLivePreviewSideEffectPolicy> sideEffectPolicy,
        String inputFactStatus,
        String traceStatus,
        String orderIntentPreviewStatus,
        String riskPreflightPreviewStatus,
        List<ShadowLivePreviewEvidence> requiredEvidence,
        List<ShadowLivePreviewEvidence> missingEvidence,
        List<ShadowLivePreviewReason> blockers,
        List<ShadowLivePreviewReason> warnings,
        List<String> nextSteps,
        Instant generatedAt
) {
    public ShadowLivePreview {
        scope = Objects.requireNonNull(scope, "scope must not be null");
        runnerStatus = normalizeStatus(runnerStatus);
        previewStatus = Objects.requireNonNull(previewStatus, "previewStatus must not be null");
        evaluationGateStatus = normalizeStatus(evaluationGateStatus);
        paperShadowComparisonStatus = normalizeStatus(paperShadowComparisonStatus);
        sideEffectPolicy = sideEffectPolicy == null ? List.of() : List.copyOf(sideEffectPolicy);
        inputFactStatus = normalizeStatus(inputFactStatus);
        traceStatus = normalizeStatus(traceStatus);
        orderIntentPreviewStatus = normalizeStatus(orderIntentPreviewStatus);
        riskPreflightPreviewStatus = normalizeStatus(riskPreflightPreviewStatus);
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
