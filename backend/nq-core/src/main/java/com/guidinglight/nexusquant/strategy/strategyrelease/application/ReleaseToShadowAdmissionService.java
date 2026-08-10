package com.guidinglight.nexusquant.strategy.strategyrelease.application;

import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyValidationDecision;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunAuthorizationBoundary;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunReleaseBindingMode;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.ReleaseToShadowAdmissionDecision.Decision;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.ReleaseToShadowAdmissionDecision.ReasonCode;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactManifest;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactVerificationResult;
import com.guidinglight.nexusquant.strategy.strategyrelease.domain.StrategyRelease;
import com.guidinglight.nexusquant.strategy.strategyrelease.domain.StrategyReleaseStatus;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

/**
 * Production Release-to-Shadow admission 纯决策服务。
 *
 * <p>服务只消费调用方已经读取的 immutable production facts，没有 repository、文件、网络、Clock、
 * runner、scheduler、交易、adapter 或 credential 依赖。ELIGIBLE 只生成未来创建所需的数据计划；
 * 不创建/启动 Shadow Run，也不表示交易或 LIVE 授权。
 */
@Service
public class ReleaseToShadowAdmissionService {

    /**
     * 对 release、artifact provenance、validation 与 Shadow 安全边界执行 fail-closed admission。
     *
     * @param request immutable production fact snapshot；null 或任何缺失/UNKNOWN 事实均返回 BLOCKED
     * @return deterministic decision；只有 ELIGIBLE 时 creationPlan 非空
     */
    public ReleaseToShadowAdmissionDecision admit(ReleaseToShadowAdmissionRequest request) {
        LinkedHashSet<ReasonCode> reasons = new LinkedHashSet<>();
        StrategyRelease release = request == null ? null : request.release();

        validateRelease(release, reasons);
        validateRequest(request, release, reasons);

        String releaseAnchorId = firstText(
                release == null ? null : release.releaseAnchorId(),
                request == null ? null : request.releaseAnchorId()
        );
        String artifactDigest = firstText(
                release == null ? null : release.artifactDigest(),
                request == null ? null : request.artifactDigest()
        );
        String strategyVersionId = firstText(
                release == null ? null : release.strategyVersionId(),
                request == null ? null : request.strategyVersionId()
        );
        UUID datasetId = release != null && release.datasetId() != null
                ? release.datasetId()
                : request == null ? null : request.datasetId();
        String evaluationId = firstText(
                release == null ? null : release.evaluationId(),
                request == null ? null : request.evaluationId()
        );
        ShadowRunCreationPlan.SideEffectPolicy sideEffectPolicy = request == null
                ? null
                : request.sideEffectPolicy();

        if (!reasons.isEmpty() || release == null || request == null) {
            return blocked(
                    reasons,
                    releaseAnchorId,
                    artifactDigest,
                    strategyVersionId,
                    datasetId,
                    evaluationId,
                    sideEffectPolicy
            );
        }

        ShadowRunCreationPlan creationPlan = createPlan(release, request);
        return new ReleaseToShadowAdmissionDecision(
                Decision.ELIGIBLE,
                List.of(ReasonCode.ELIGIBLE_FOR_CREATION_PLAN_ONLY),
                release.releaseAnchorId(),
                release.artifactDigest(),
                release.strategyVersionId(),
                release.datasetId(),
                release.evaluationId(),
                request.sideEffectPolicy(),
                creationPlan,
                false,
                false,
                false,
                false
        );
    }

    private static void validateRelease(StrategyRelease release, LinkedHashSet<ReasonCode> reasons) {
        if (release == null) {
            reasons.add(ReasonCode.PUBLISH_RECORD_MISSING);
            return;
        }
        if (!hasText(release.releaseAnchorId()) || !hasText(release.publishRecordId())) {
            reasons.add(ReasonCode.PUBLISH_RECORD_MISSING);
        } else if (!release.releaseAnchorId().equals(release.publishRecordId())) {
            reasons.add(ReasonCode.RELEASE_IDENTITY_MISMATCH);
        }
        if (!hasText(release.strategyVersionId())) {
            reasons.add(ReasonCode.STRATEGY_VERSION_MISSING);
        }
        if (release.datasetId() == null) {
            reasons.add(ReasonCode.DATASET_MISSING);
        }
        if (!hasText(release.evaluationId())) {
            reasons.add(ReasonCode.EVALUATION_MISSING);
        }

        if (release.releaseStatus() == StrategyReleaseStatus.UNVERIFIED) {
            reasons.add(ReasonCode.RELEASE_UNVERIFIED);
        } else if (release.releaseStatus() == StrategyReleaseStatus.REJECTED) {
            reasons.add(ReasonCode.RELEASE_REJECTED);
        }

        StrategyArtifactVerificationResult verification = release.verificationResult();
        if (verification.status() != StrategyArtifactVerificationResult.Status.VERIFIED) {
            reasons.add(ReasonCode.ARTIFACT_NOT_VERIFIED);
        }
        validateReleaseArtifactDigest(release, verification, reasons);
        validateManifestProvenance(release, reasons);
    }

    private static void validateReleaseArtifactDigest(
            StrategyRelease release,
            StrategyArtifactVerificationResult verification,
            LinkedHashSet<ReasonCode> reasons
    ) {
        if (!hasText(release.artifactDigest())) {
            reasons.add(ReasonCode.ARTIFACT_DIGEST_MISSING);
            return;
        }
        if (isNotReleaseBound(release.publishRecordId(), release.artifactDigest(), reasons)) {
            return;
        }
        if (verification.status() == StrategyArtifactVerificationResult.Status.VERIFIED) {
            if (isNotReleaseBound(release.publishRecordId(), verification.artifactDigest(), reasons)) {
                reasons.add(ReasonCode.ARTIFACT_NOT_VERIFIED);
            } else if (!release.artifactDigest().equals(verification.artifactDigest())) {
                reasons.add(ReasonCode.ARTIFACT_DIGEST_MISMATCH);
            }
        }
    }

    private static void validateManifestProvenance(
            StrategyRelease release,
            LinkedHashSet<ReasonCode> reasons
    ) {
        StrategyArtifactManifest manifest = release.artifactManifest();
        if (!StrategyArtifactManifest.SUPPORTED_SCHEMA_VERSION.equals(manifest.schemaVersion())) {
            reasons.add(ReasonCode.MANIFEST_SCHEMA_UNSUPPORTED);
        }
        if (textsDiffer(release.strategyVersionId(), manifest.strategyVersionId())
                || !java.util.Objects.equals(release.datasetId(), manifest.datasetId())
                || textsDiffer(release.evaluationId(), manifest.evaluationId())
                || textsDiffer(release.artifactDigest(), manifest.artifactDigest())) {
            reasons.add(ReasonCode.MANIFEST_PROVENANCE_MISMATCH);
        }
    }

    private static void validateRequest(
            ReleaseToShadowAdmissionRequest request,
            StrategyRelease release,
            LinkedHashSet<ReasonCode> reasons
    ) {
        if (request == null) {
            reasons.add(ReasonCode.RELEASE_ANCHOR_MISSING);
            reasons.add(ReasonCode.PUBLISH_ID_MISSING);
            reasons.add(ReasonCode.RELEASE_BINDING_REQUIRED);
            reasons.add(ReasonCode.VALIDATION_EVIDENCE_MISSING);
            reasons.add(ReasonCode.SHADOW_WINDOW_MISSING);
            reasons.add(ReasonCode.AUTHORIZATION_BOUNDARY_MISSING);
            reasons.add(ReasonCode.SIDE_EFFECT_POLICY_MISSING);
            reasons.add(ReasonCode.TRACE_REFERENCE_MISSING);
            return;
        }

        validateRequestedIdentity(request, release, reasons);
        validateRequestedBinding(request, reasons);
        validateValidation(request.validationDecision(), reasons);
        validateWindow(request.windowStart(), request.windowEnd(), reasons);
        validateAuthorizationBoundary(request.authorizationBoundary(), reasons);
        validateSideEffectPolicy(request.sideEffectPolicy(), reasons);
        validateTrace(request.traceId(), reasons);
    }

    private static void validateRequestedIdentity(
            ReleaseToShadowAdmissionRequest request,
            StrategyRelease release,
            LinkedHashSet<ReasonCode> reasons
    ) {
        if (!hasText(request.releaseAnchorId())) {
            reasons.add(ReasonCode.RELEASE_ANCHOR_MISSING);
        }
        if (!hasText(request.publishRecordId())) {
            reasons.add(ReasonCode.PUBLISH_ID_MISSING);
        }
        if (hasText(request.releaseAnchorId())
                && hasText(request.publishRecordId())
                && !request.releaseAnchorId().equals(request.publishRecordId())) {
            reasons.add(ReasonCode.RELEASE_IDENTITY_MISMATCH);
        }
        if (!hasText(request.strategyVersionId())) {
            reasons.add(ReasonCode.STRATEGY_VERSION_MISSING);
        }
        if (request.datasetId() == null) {
            reasons.add(ReasonCode.DATASET_MISSING);
        }
        if (!hasText(request.evaluationId())) {
            reasons.add(ReasonCode.EVALUATION_MISSING);
        }
        if (release == null) {
            return;
        }
        if (hasText(request.releaseAnchorId()) && !release.releaseAnchorId().equals(request.releaseAnchorId())) {
            reasons.add(ReasonCode.RELEASE_IDENTITY_MISMATCH);
        }
        if (hasText(request.publishRecordId()) && !release.publishRecordId().equals(request.publishRecordId())) {
            reasons.add(ReasonCode.PUBLISH_ID_MISMATCH);
        }
        if (hasText(request.strategyVersionId()) && textsDiffer(release.strategyVersionId(), request.strategyVersionId())) {
            reasons.add(ReasonCode.STRATEGY_VERSION_MISMATCH);
        }
        if (request.datasetId() != null && !java.util.Objects.equals(release.datasetId(), request.datasetId())) {
            reasons.add(ReasonCode.DATASET_MISMATCH);
        }
        if (hasText(request.evaluationId()) && textsDiffer(release.evaluationId(), request.evaluationId())) {
            reasons.add(ReasonCode.EVALUATION_MISMATCH);
        }
    }

    private static void validateRequestedBinding(
            ReleaseToShadowAdmissionRequest request,
            LinkedHashSet<ReasonCode> reasons
    ) {
        if (!hasText(request.artifactDigest())) {
            reasons.add(ReasonCode.ARTIFACT_DIGEST_MISSING);
        }
        if (isNotReleaseBound(request.publishRecordId(), request.artifactDigest(), reasons)) {
            reasons.add(ReasonCode.RELEASE_BINDING_REQUIRED);
            return;
        }
        if (request.release() != null
                && !java.util.Objects.equals(request.release().artifactDigest(), request.artifactDigest())) {
            reasons.add(ReasonCode.ARTIFACT_DIGEST_MISMATCH);
        }
    }

    private static void validateValidation(
            StrategyValidationDecision validationDecision,
            LinkedHashSet<ReasonCode> reasons
    ) {
        if (validationDecision == null || validationDecision == StrategyValidationDecision.NO_EVIDENCE) {
            reasons.add(ReasonCode.VALIDATION_EVIDENCE_MISSING);
        } else if (validationDecision == StrategyValidationDecision.STALE_EVIDENCE) {
            reasons.add(ReasonCode.VALIDATION_EVIDENCE_STALE);
        } else if (validationDecision != StrategyValidationDecision.APPROVED) {
            reasons.add(ReasonCode.VALIDATION_NOT_APPROVED);
        }
    }

    private static void validateWindow(
            Instant windowStart,
            Instant windowEnd,
            LinkedHashSet<ReasonCode> reasons
    ) {
        if (windowStart == null || windowEnd == null) {
            reasons.add(ReasonCode.SHADOW_WINDOW_MISSING);
        } else if (!windowEnd.isAfter(windowStart)) {
            reasons.add(ReasonCode.SHADOW_WINDOW_INVALID);
        }
    }

    private static void validateAuthorizationBoundary(
            ShadowRunAuthorizationBoundary boundary,
            LinkedHashSet<ReasonCode> reasons
    ) {
        if (boundary == null) {
            reasons.add(ReasonCode.AUTHORIZATION_BOUNDARY_MISSING);
        } else if (boundary != ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY
                && boundary != ShadowRunAuthorizationBoundary.REVIEW_ONLY) {
            reasons.add(ReasonCode.AUTHORIZATION_BOUNDARY_INVALID);
        }
    }

    private static void validateSideEffectPolicy(
            ShadowRunCreationPlan.SideEffectPolicy policy,
            LinkedHashSet<ReasonCode> reasons
    ) {
        if (policy == null) {
            reasons.add(ReasonCode.SIDE_EFFECT_POLICY_MISSING);
            return;
        }
        if (!policy.noOrderSubmission()) {
            reasons.add(ReasonCode.NO_ORDER_SUBMISSION_REQUIRED);
        }
        if (!policy.noCredentialAccess()) {
            reasons.add(ReasonCode.NO_CREDENTIAL_ACCESS_REQUIRED);
        }
        if (!policy.noPrivateEndpoint()) {
            reasons.add(ReasonCode.NO_PRIVATE_ENDPOINT_REQUIRED);
        }
        if (!policy.noLedgerMutation()) {
            reasons.add(ReasonCode.NO_LEDGER_MUTATION_REQUIRED);
        }
        if (!policy.noAccountMutation()) {
            reasons.add(ReasonCode.NO_ACCOUNT_MUTATION_REQUIRED);
        }
        if (!policy.noExternalPrivateIo()) {
            reasons.add(ReasonCode.NO_EXTERNAL_PRIVATE_IO_REQUIRED);
        }
    }

    private static void validateTrace(String traceId, LinkedHashSet<ReasonCode> reasons) {
        if (!hasText(traceId)) {
            reasons.add(ReasonCode.TRACE_REFERENCE_MISSING);
        } else if (traceId.length() > 128 || traceId.chars().anyMatch(Character::isISOControl)) {
            reasons.add(ReasonCode.TRACE_REFERENCE_INVALID);
        }
    }

    private static boolean isNotReleaseBound(
            String publishRecordId,
            String artifactDigest,
            LinkedHashSet<ReasonCode> reasons
    ) {
        if (!hasText(publishRecordId)) {
            return true;
        }
        try {
            return ShadowRunReleaseBindingMode.derive(publishRecordId.trim(), trimToNull(artifactDigest))
                    != ShadowRunReleaseBindingMode.RELEASE_BOUND;
        } catch (IllegalArgumentException exception) {
            if (hasText(artifactDigest)) {
                reasons.add(ReasonCode.ARTIFACT_DIGEST_INVALID);
            }
            return false;
        }
    }

    private static ShadowRunCreationPlan createPlan(
            StrategyRelease release,
            ReleaseToShadowAdmissionRequest request
    ) {
        List<String> canonicalFields = List.of(
                "shadow-run-release-admission.v1",
                release.releaseAnchorId(),
                release.publishRecordId(),
                release.artifactDigest(),
                release.strategyVersionId(),
                release.datasetId().toString(),
                release.evaluationId(),
                request.windowStart().toString(),
                request.windowEnd().toString(),
                request.authorizationBoundary().name(),
                Boolean.toString(request.sideEffectPolicy().noOrderSubmission()),
                Boolean.toString(request.sideEffectPolicy().noCredentialAccess()),
                Boolean.toString(request.sideEffectPolicy().noPrivateEndpoint()),
                Boolean.toString(request.sideEffectPolicy().noLedgerMutation()),
                Boolean.toString(request.sideEffectPolicy().noAccountMutation()),
                Boolean.toString(request.sideEffectPolicy().noExternalPrivateIo()),
                release.artifactManifest().schemaVersion()
        );
        return new ShadowRunCreationPlan(
                release.releaseAnchorId(),
                release.publishRecordId(),
                release.artifactDigest(),
                release.strategyVersionId(),
                release.datasetId(),
                release.evaluationId(),
                request.windowStart(),
                request.windowEnd(),
                "dataset:" + release.datasetId(),
                request.authorizationBoundary(),
                request.sideEffectPolicy(),
                release.artifactManifest().schemaVersion(),
                "publish:" + release.publishRecordId(),
                request.traceId().trim(),
                deterministicSha256(canonicalFields)
        );
    }

    private static ReleaseToShadowAdmissionDecision blocked(
            LinkedHashSet<ReasonCode> reasons,
            String releaseAnchorId,
            String artifactDigest,
            String strategyVersionId,
            UUID datasetId,
            String evaluationId,
            ShadowRunCreationPlan.SideEffectPolicy sideEffectPolicy
    ) {
        return new ReleaseToShadowAdmissionDecision(
                Decision.BLOCKED,
                List.copyOf(reasons),
                releaseAnchorId,
                artifactDigest,
                strategyVersionId,
                datasetId,
                evaluationId,
                sideEffectPolicy,
                null,
                false,
                false,
                false,
                false
        );
    }

    private static String deterministicSha256(List<String> canonicalFields) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : canonicalFields) {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean textsDiffer(String left, String right) {
        return !hasText(left) || !left.equals(right);
    }

    private static String firstText(String preferred, String fallback) {
        return hasText(preferred) ? preferred.trim() : hasText(fallback) ? fallback.trim() : null;
    }

    private static String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }
}
