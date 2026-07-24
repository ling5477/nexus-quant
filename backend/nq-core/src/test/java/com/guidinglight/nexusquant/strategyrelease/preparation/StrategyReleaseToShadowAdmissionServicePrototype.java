package com.guidinglight.nexusquant.strategyrelease.preparation;

import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyValidationDecision;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunAuthorizationBoundary;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * PRE-GATEX Release-to-Shadow 的纯 admission service。
 *
 * <p>它只组合既有 release、verification、validation 与 requested binding 快照。没有 repository、
 * 文件、网络、Clock、runner 或 scheduler 依赖；调用不会创建 Shadow Run、事件、快照或报告。
 */
final class StrategyReleaseToShadowAdmissionServicePrototype {

    static final String SUPPORTED_MANIFEST_SCHEMA = StrategyReleaseServicePrototype.SUPPORTED_MANIFEST_SCHEMA;

    private static final Pattern SHA_256 = Pattern.compile("^[0-9a-f]{64}$");

    ShadowRunAdmissionPrototype admit(
            StrategyReleaseAggregatePrototype release,
            ArtifactVerificationResultPrototype artifactVerification,
            StrategyValidationDecision validationDecision,
            ShadowRunReleaseBindingPrototype releaseBinding,
            RequestedShadowBindingPrototype requestedBinding
    ) {
        LinkedHashSet<ShadowRunAdmissionFindingCode> blockers = new LinkedHashSet<>();
        LinkedHashSet<ShadowRunAdmissionFindingCode> unknowns = new LinkedHashSet<>();

        validateRelease(release, blockers, unknowns);
        validateBinding(release, releaseBinding, blockers, unknowns);
        validateArtifactVerification(release, releaseBinding, artifactVerification, blockers, unknowns);
        validateValidationEvidence(validationDecision, blockers, unknowns);
        validateRequestedBinding(release, releaseBinding, requestedBinding, blockers, unknowns);

        if (!blockers.isEmpty()) {
            return ShadowRunAdmissionPrototype.blocked(List.copyOf(blockers), List.copyOf(unknowns));
        }
        if (!unknowns.isEmpty()) {
            return ShadowRunAdmissionPrototype.unknown(List.copyOf(unknowns));
        }

        ShadowRunCreationPlanPrototype creationPlan = new ShadowRunCreationPlanPrototype(
                release.publishId(),
                release.artifactDigest(),
                release.strategyVersionId(),
                requestedBinding.datasetId(),
                release.evaluationId(),
                requestedBinding.windowStart(),
                requestedBinding.windowEnd(),
                requestedBinding.authorizationBoundary(),
                requestedBinding.sideEffectPolicy(),
                deterministicIdempotencyKey(ShadowRunIdempotencyMaterialPrototype.from(release, requestedBinding)),
                requestedBinding.traceId()
        );
        return ShadowRunAdmissionPrototype.admitted(creationPlan);
    }

    /** UTF-8、固定字段顺序和四字节长度前缀的 deterministic SHA-256 creation key。 */
    static String deterministicIdempotencyKey(ShadowRunIdempotencyMaterialPrototype material) {
        Objects.requireNonNull(material, "material must not be null");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : material.canonicalFields()) {
                byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 must be available", ex);
        }
    }

    private static void validateRelease(
            StrategyReleaseAggregatePrototype release,
            LinkedHashSet<ShadowRunAdmissionFindingCode> blockers,
            LinkedHashSet<ShadowRunAdmissionFindingCode> unknowns
    ) {
        if (release == null) {
            unknowns.add(ShadowRunAdmissionFindingCode.REQUIRED_FACT_MISSING);
            return;
        }
        if (release.state() != StrategyReleaseState.PUBLISHED) {
            blockers.add(ShadowRunAdmissionFindingCode.RELEASE_NOT_PUBLISHED);
        }
        if (!hasText(release.releaseId())
                || !hasText(release.publishId())
                || !hasText(release.strategyVersionId())
                || !hasText(release.datasetId())
                || !hasText(release.evaluationId())
                || !hasText(release.manifestSchemaVersion())
                || !hasValidDigest(release.artifactDigest())) {
            unknowns.add(ShadowRunAdmissionFindingCode.REQUIRED_FACT_MISSING);
            return;
        }
        // Existing test aggregate keeps releaseId only as a compatibility alias; admission fails closed if it diverges.
        if (!release.releaseId().equals(release.publishId())) {
            blockers.add(ShadowRunAdmissionFindingCode.PUBLISH_ANCHOR_MISMATCH);
        }
        if (!SUPPORTED_MANIFEST_SCHEMA.equals(release.manifestSchemaVersion())) {
            blockers.add(ShadowRunAdmissionFindingCode.SCHEMA_VERSION_UNSUPPORTED);
        }
    }

    private static void validateBinding(
            StrategyReleaseAggregatePrototype release,
            ShadowRunReleaseBindingPrototype releaseBinding,
            LinkedHashSet<ShadowRunAdmissionFindingCode> blockers,
            LinkedHashSet<ShadowRunAdmissionFindingCode> unknowns
    ) {
        if (releaseBinding == null) {
            unknowns.add(ShadowRunAdmissionFindingCode.REQUIRED_FACT_MISSING);
            return;
        }
        if (releaseBinding.bindingMode() != ShadowRunReleaseBindingMode.RELEASE_BOUND) {
            blockers.add(ShadowRunAdmissionFindingCode.RELEASE_BINDING_NOT_COMPLETE);
            return;
        }
        if (!hasText(releaseBinding.publishRecordId()) || !hasValidDigest(releaseBinding.artifactDigest())) {
            unknowns.add(ShadowRunAdmissionFindingCode.REQUIRED_FACT_MISSING);
            return;
        }
        if (release != null && hasText(release.publishId())
                && !release.publishId().equals(releaseBinding.publishRecordId())) {
            blockers.add(ShadowRunAdmissionFindingCode.PUBLISH_ANCHOR_MISMATCH);
        }
        if (release != null && hasValidDigest(release.artifactDigest())
                && !release.artifactDigest().equals(releaseBinding.artifactDigest())) {
            blockers.add(ShadowRunAdmissionFindingCode.ARTIFACT_DIGEST_MISMATCH);
        }
    }

    private static void validateArtifactVerification(
            StrategyReleaseAggregatePrototype release,
            ShadowRunReleaseBindingPrototype releaseBinding,
            ArtifactVerificationResultPrototype artifactVerification,
            LinkedHashSet<ShadowRunAdmissionFindingCode> blockers,
            LinkedHashSet<ShadowRunAdmissionFindingCode> unknowns
    ) {
        if (artifactVerification == null) {
            unknowns.add(ShadowRunAdmissionFindingCode.ARTIFACT_VERIFICATION_UNKNOWN);
            return;
        }
        switch (artifactVerification.status()) {
            case UNKNOWN -> unknowns.add(ShadowRunAdmissionFindingCode.ARTIFACT_VERIFICATION_UNKNOWN);
            case REJECTED -> blockers.add(ShadowRunAdmissionFindingCode.ARTIFACT_VERIFICATION_REJECTED);
            case VERIFIED -> {
                if (!hasValidDigest(artifactVerification.artifactDigest())) {
                    unknowns.add(ShadowRunAdmissionFindingCode.ARTIFACT_VERIFICATION_UNKNOWN);
                    return;
                }
                if (artifactVerification.verifiedSizeBytes() <= 0 || !artifactVerification.findingCodes().isEmpty()) {
                    blockers.add(ShadowRunAdmissionFindingCode.ARTIFACT_VERIFICATION_REJECTED);
                }
                if (release != null && hasValidDigest(release.artifactDigest())
                        && !release.artifactDigest().equals(artifactVerification.artifactDigest())) {
                    blockers.add(ShadowRunAdmissionFindingCode.ARTIFACT_DIGEST_MISMATCH);
                }
                if (releaseBinding != null && hasValidDigest(releaseBinding.artifactDigest())
                        && !releaseBinding.artifactDigest().equals(artifactVerification.artifactDigest())) {
                    blockers.add(ShadowRunAdmissionFindingCode.ARTIFACT_DIGEST_MISMATCH);
                }
            }
        }
    }

    private static void validateValidationEvidence(
            StrategyValidationDecision validationDecision,
            LinkedHashSet<ShadowRunAdmissionFindingCode> blockers,
            LinkedHashSet<ShadowRunAdmissionFindingCode> unknowns
    ) {
        if (validationDecision == null) {
            unknowns.add(ShadowRunAdmissionFindingCode.REQUIRED_FACT_MISSING);
            return;
        }
        switch (validationDecision) {
            case APPROVED -> {
                // Only APPROVED can continue to plan generation.
            }
            case NO_EVIDENCE -> unknowns.add(ShadowRunAdmissionFindingCode.VALIDATION_EVIDENCE_MISSING);
            case STALE_EVIDENCE -> unknowns.add(ShadowRunAdmissionFindingCode.VALIDATION_EVIDENCE_STALE);
            case REJECTED, NEEDS_REVIEW, BLOCKED -> blockers.add(ShadowRunAdmissionFindingCode.VALIDATION_NOT_APPROVED);
        }
    }

    private static void validateRequestedBinding(
            StrategyReleaseAggregatePrototype release,
            ShadowRunReleaseBindingPrototype releaseBinding,
            RequestedShadowBindingPrototype requestedBinding,
            LinkedHashSet<ShadowRunAdmissionFindingCode> blockers,
            LinkedHashSet<ShadowRunAdmissionFindingCode> unknowns
    ) {
        if (requestedBinding == null) {
            unknowns.add(ShadowRunAdmissionFindingCode.REQUIRED_FACT_MISSING);
            return;
        }
        if (!hasText(requestedBinding.actionId())
                || !hasText(requestedBinding.traceId())
                || !hasText(requestedBinding.publishRecordId())
                || !hasText(requestedBinding.strategyVersionId())
                || requestedBinding.datasetId() == null
                || !hasText(requestedBinding.evaluationId())) {
            unknowns.add(ShadowRunAdmissionFindingCode.REQUIRED_FACT_MISSING);
        }
        if (requestedBinding.windowStart() == null || requestedBinding.windowEnd() == null) {
            unknowns.add(ShadowRunAdmissionFindingCode.REQUIRED_FACT_MISSING);
        } else if (!requestedBinding.windowEnd().isAfter(requestedBinding.windowStart())) {
            blockers.add(ShadowRunAdmissionFindingCode.INVALID_SHADOW_WINDOW);
        }
        if (requestedBinding.authorizationBoundary() == null) {
            unknowns.add(ShadowRunAdmissionFindingCode.REQUIRED_FACT_MISSING);
        } else if (requestedBinding.authorizationBoundary() != ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY
                && requestedBinding.authorizationBoundary() != ShadowRunAuthorizationBoundary.REVIEW_ONLY) {
            blockers.add(ShadowRunAdmissionFindingCode.AUTHORIZATION_BOUNDARY_INVALID);
        }
        if (requestedBinding.sideEffectPolicy() == null) {
            unknowns.add(ShadowRunAdmissionFindingCode.REQUIRED_FACT_MISSING);
        } else if (!requestedBinding.sideEffectPolicy().allNoSideEffects()) {
            blockers.add(ShadowRunAdmissionFindingCode.SIDE_EFFECT_POLICY_VIOLATION);
        }
        if (release == null || releaseBinding == null) {
            return;
        }
        if (hasText(release.publishId()) && hasText(requestedBinding.publishRecordId())
                && !release.publishId().equals(requestedBinding.publishRecordId())) {
            blockers.add(ShadowRunAdmissionFindingCode.PUBLISH_ANCHOR_MISMATCH);
        }
        if (hasText(releaseBinding.publishRecordId()) && hasText(requestedBinding.publishRecordId())
                && !releaseBinding.publishRecordId().equals(requestedBinding.publishRecordId())) {
            blockers.add(ShadowRunAdmissionFindingCode.PUBLISH_ANCHOR_MISMATCH);
        }
        if (hasText(release.strategyVersionId()) && hasText(requestedBinding.strategyVersionId())
                && !release.strategyVersionId().equals(requestedBinding.strategyVersionId())) {
            blockers.add(ShadowRunAdmissionFindingCode.STRATEGY_VERSION_MISMATCH);
        }
        if (hasText(release.datasetId()) && requestedBinding.datasetId() != null
                && !release.datasetId().equals(requestedBinding.datasetId().toString())) {
            blockers.add(ShadowRunAdmissionFindingCode.DATASET_MISMATCH);
        }
        if (hasText(release.evaluationId()) && hasText(requestedBinding.evaluationId())
                && !release.evaluationId().equals(requestedBinding.evaluationId())) {
            blockers.add(ShadowRunAdmissionFindingCode.EVALUATION_MISMATCH);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean hasValidDigest(String value) {
        return hasText(value) && SHA_256.matcher(value).matches();
    }
}

/** 幂等键的完整业务材料；不保存 actionId、traceId、credential 或路径。 */
record ShadowRunIdempotencyMaterialPrototype(
        String publishRecordId,
        String artifactDigest,
        String strategyVersionId,
        String datasetId,
        String evaluationId,
        Instant windowStart,
        Instant windowEnd,
        ShadowRunAuthorizationBoundary authorizationBoundary,
        ShadowRunSideEffectPolicyPrototype sideEffectPolicy,
        String manifestSchemaVersion
) {

    ShadowRunIdempotencyMaterialPrototype {
        Objects.requireNonNull(publishRecordId, "publishRecordId must not be null");
        Objects.requireNonNull(artifactDigest, "artifactDigest must not be null");
        Objects.requireNonNull(strategyVersionId, "strategyVersionId must not be null");
        Objects.requireNonNull(datasetId, "datasetId must not be null");
        Objects.requireNonNull(evaluationId, "evaluationId must not be null");
        Objects.requireNonNull(windowStart, "windowStart must not be null");
        Objects.requireNonNull(windowEnd, "windowEnd must not be null");
        Objects.requireNonNull(authorizationBoundary, "authorizationBoundary must not be null");
        Objects.requireNonNull(sideEffectPolicy, "sideEffectPolicy must not be null");
        Objects.requireNonNull(manifestSchemaVersion, "manifestSchemaVersion must not be null");
    }

    static ShadowRunIdempotencyMaterialPrototype from(
            StrategyReleaseAggregatePrototype release,
            RequestedShadowBindingPrototype requestedBinding
    ) {
        return new ShadowRunIdempotencyMaterialPrototype(
                release.publishId(),
                release.artifactDigest(),
                release.strategyVersionId(),
                requestedBinding.datasetId().toString(),
                release.evaluationId(),
                requestedBinding.windowStart(),
                requestedBinding.windowEnd(),
                requestedBinding.authorizationBoundary(),
                requestedBinding.sideEffectPolicy(),
                release.manifestSchemaVersion()
        );
    }

    List<String> canonicalFields() {
        return List.of(
                "shadow-run-release-admission.v1",
                publishRecordId,
                artifactDigest,
                strategyVersionId,
                datasetId,
                evaluationId,
                windowStart.toString(),
                windowEnd.toString(),
                authorizationBoundary.name(),
                Boolean.toString(sideEffectPolicy.noOrderSubmission()),
                Boolean.toString(sideEffectPolicy.noCredentialAccess()),
                Boolean.toString(sideEffectPolicy.noPrivateEndpoint()),
                Boolean.toString(sideEffectPolicy.noLedgerMutation()),
                Boolean.toString(sideEffectPolicy.noAccountMutation()),
                Boolean.toString(sideEffectPolicy.noExternalPrivateIo()),
                manifestSchemaVersion
        );
    }
}
