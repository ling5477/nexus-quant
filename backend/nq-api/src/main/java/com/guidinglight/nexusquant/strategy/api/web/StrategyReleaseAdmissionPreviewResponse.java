package com.guidinglight.nexusquant.strategy.api.web;

import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseAdmissionPreview;

import java.util.List;
import java.util.UUID;

/**
 * Strategy Release-to-Shadow admission preview 的最小安全响应。
 *
 * <p>只暴露业务 provenance、状态、reason code 与 artifact digest；不包含 trusted root、绝对路径、
 * storage key、manifest、artifact 内容、creation plan、内部异常或任何 credential。
 */
public record StrategyReleaseAdmissionPreviewResponse(
        String publishRecordId,
        String releaseAnchorId,
        String strategyVersionId,
        UUID datasetId,
        String evaluationId,
        String bindingMode,
        String releaseStatus,
        String artifactVerificationStatus,
        String validationDecision,
        String admissionDecision,
        List<String> reasonCodes,
        String artifactDigest
) {

    /** 将 core safe model 映射为稳定 HTTP DTO，不新增或重算任何业务判断。 */
    public static StrategyReleaseAdmissionPreviewResponse from(StrategyReleaseAdmissionPreview preview) {
        return new StrategyReleaseAdmissionPreviewResponse(
                preview.publishRecordId(),
                preview.releaseAnchorId(),
                preview.strategyVersionId(),
                preview.datasetId(),
                preview.evaluationId(),
                preview.bindingMode().name(),
                preview.releaseStatus().name(),
                preview.artifactVerificationStatus().name(),
                preview.validationDecision().name(),
                preview.admissionDecision().name(),
                preview.reasonCodes(),
                preview.artifactDigest()
        );
    }
}
