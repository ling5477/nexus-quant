package com.guidinglight.nexusquant.strategy.strategyrelease.application;

import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunReleaseBindingMode;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyValidationDecision;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactVerificationResult;
import com.guidinglight.nexusquant.strategy.strategyrelease.domain.StrategyReleaseStatus;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Strategy Release-to-Shadow admission 的安全只读预览模型。
 *
 * <p>该模型不暴露 trusted root、path、storage key、raw manifest、creation plan、异常或 credential。
 * ELIGIBLE 仅表示 GateX-3 纯决策生成了内存 creation plan，不表示 Shadow Run 已创建或交易已授权。
 */
public record StrategyReleaseAdmissionPreview(
        String publishRecordId,
        String releaseAnchorId,
        String strategyVersionId,
        UUID datasetId,
        String evaluationId,
        ShadowRunReleaseBindingMode bindingMode,
        StrategyReleaseStatus releaseStatus,
        StrategyArtifactVerificationResult.Status artifactVerificationStatus,
        StrategyValidationDecision validationDecision,
        ReleaseToShadowAdmissionDecision.Decision admissionDecision,
        List<String> reasonCodes,
        String artifactDigest
) {
    public StrategyReleaseAdmissionPreview {
        publishRecordId = requireText(publishRecordId, "publishRecordId");
        releaseAnchorId = requireText(releaseAnchorId, "releaseAnchorId");
        Objects.requireNonNull(bindingMode, "bindingMode must not be null");
        Objects.requireNonNull(releaseStatus, "releaseStatus must not be null");
        Objects.requireNonNull(artifactVerificationStatus, "artifactVerificationStatus must not be null");
        Objects.requireNonNull(validationDecision, "validationDecision must not be null");
        Objects.requireNonNull(admissionDecision, "admissionDecision must not be null");
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
        if (reasonCodes.isEmpty()) {
            throw new IllegalArgumentException("reasonCodes must not be empty");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
