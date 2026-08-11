package com.guidinglight.nexusquant.strategy.strategyrelease.application;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Release-to-Shadow admission 的纯决策结果。
 *
 * <p>{@link Decision#ELIGIBLE} 只允许生成 {@link ShadowRunCreationPlan}，不表示 Shadow Run 已创建，
 * 更不表示交易、LIVE 或订单授权。BLOCKED 结果必须携带稳定 reason code 且不得包含 creation plan。
 */
public record ReleaseToShadowAdmissionDecision(
        Decision decision,
        List<ReasonCode> reasonCodes,
        String releaseAnchorId,
        String artifactDigest,
        String strategyVersionId,
        UUID datasetId,
        String evaluationId,
        ShadowRunCreationPlan.SideEffectPolicy sideEffectPolicy,
        ShadowRunCreationPlan creationPlan,
        boolean shadowRunCreated,
        boolean shadowRunStarted,
        boolean tradingAuthorized,
        boolean orderSubmitted
) {

    public ReleaseToShadowAdmissionDecision {
        Objects.requireNonNull(decision, "decision must not be null");
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
        if (shadowRunCreated || shadowRunStarted || tradingAuthorized || orderSubmitted) {
            throw new IllegalArgumentException("admission must not report runtime or trading side effects");
        }
        if (decision == Decision.ELIGIBLE) {
            if (creationPlan == null || !reasonCodes.equals(List.of(ReasonCode.ELIGIBLE_FOR_CREATION_PLAN_ONLY))) {
                throw new IllegalArgumentException("eligible decision requires only the creation-plan boundary reason");
            }
        } else if (creationPlan != null || reasonCodes.isEmpty()) {
            throw new IllegalArgumentException("blocked decision requires reasons and no creation plan");
        }
    }

    public enum Decision {
        ELIGIBLE,
        BLOCKED
    }

    /**
     * 稳定、可测试且不携带路径、异常或敏感信息的 admission reason code。
     */
    public enum ReasonCode {
        ELIGIBLE_FOR_CREATION_PLAN_ONLY,
        PUBLISH_RECORD_MISSING,
        RELEASE_ANCHOR_MISSING,
        PUBLISH_ID_MISSING,
        RELEASE_IDENTITY_MISMATCH,
        PUBLISH_ID_MISMATCH,
        STRATEGY_VERSION_MISSING,
        STRATEGY_VERSION_MISMATCH,
        DATASET_MISSING,
        DATASET_MISMATCH,
        EVALUATION_MISSING,
        EVALUATION_MISMATCH,
        RELEASE_UNVERIFIED,
        RELEASE_REJECTED,
        ARTIFACT_NOT_VERIFIED,
        ARTIFACT_DIGEST_MISSING,
        ARTIFACT_DIGEST_INVALID,
        ARTIFACT_DIGEST_MISMATCH,
        RELEASE_BINDING_REQUIRED,
        MANIFEST_PROVENANCE_MISMATCH,
        MANIFEST_SCHEMA_UNSUPPORTED,
        ADMISSION_FACTS_INCOMPLETE,
        VALIDATION_EVIDENCE_MISSING,
        VALIDATION_EVIDENCE_STALE,
        VALIDATION_NOT_APPROVED,
        SHADOW_WINDOW_MISSING,
        SHADOW_WINDOW_INVALID,
        AUTHORIZATION_BOUNDARY_MISSING,
        AUTHORIZATION_BOUNDARY_INVALID,
        SIDE_EFFECT_POLICY_MISSING,
        NO_ORDER_SUBMISSION_REQUIRED,
        NO_CREDENTIAL_ACCESS_REQUIRED,
        NO_PRIVATE_ENDPOINT_REQUIRED,
        NO_LEDGER_MUTATION_REQUIRED,
        NO_ACCOUNT_MUTATION_REQUIRED,
        NO_EXTERNAL_PRIVATE_IO_REQUIRED,
        TRACE_REFERENCE_MISSING,
        TRACE_REFERENCE_INVALID
    }
}
