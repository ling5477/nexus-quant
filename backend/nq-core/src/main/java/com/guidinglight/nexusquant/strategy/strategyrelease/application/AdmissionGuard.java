package com.guidinglight.nexusquant.strategy.strategyrelease.application;

import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunAuthorizationBoundary;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 一次 ELIGIBLE admission 所依据的 immutable fact-generation proof。
 *
 * <p>Guard 不是 creation plan，也不是 command identity；不携带 filesystem path、trusted root、raw
 * manifest 或原始 Idempotency-Key。writer 只能在同一 PostgreSQL generation 上消费它一次或幂等重放。
 */
public record AdmissionGuard(
        int guardSchemaVersion,
        String publishRecordId,
        long admissionRevision,
        String releaseArtifactDigest,
        String manifestFingerprint,
        String manifestSchemaVersion,
        String backtestRunId,
        String strategyVersionId,
        UUID datasetId,
        String evaluationId,
        Instant windowStart,
        Instant windowEnd,
        String strategyVersionStatus,
        String evaluationStatus,
        String publishStatus,
        StrategyReleaseAdmissionPreviewFacts.PaperEvidenceIdentity latestPaperIdentity,
        StrategyReleaseAdmissionPreviewFacts.ShadowEvidenceIdentity latestShadowEvidenceIdentity,
        StrategyReleaseAdmissionPreviewFacts.ConsistencyEvidenceIdentity latestConsistencyIdentity,
        ShadowRunAuthorizationBoundary authorizationBoundary,
        String sideEffectPolicyVersion,
        ShadowRunCreationPlan.SideEffectPolicy sideEffectPolicy,
        String admissionFingerprint,
        Instant evaluatedAt
) {
    public static final int SUPPORTED_GUARD_SCHEMA_VERSION = 1;
    public static final String FINGERPRINT_SCHEMA_VERSION = "strategy-release-admission-guard.v1";
    public static final String SIDE_EFFECT_POLICY_VERSION = "gate-x5-release-materialization.v1";
    private static final Pattern SHA_256 = Pattern.compile("^[0-9a-f]{64}$");

    public AdmissionGuard {
        if (guardSchemaVersion < 1 || admissionRevision < 0) {
            throw new IllegalArgumentException("guard versions must be positive");
        }
        publishRecordId = requireText(publishRecordId, "publishRecordId");
        releaseArtifactDigest = requireSha256(releaseArtifactDigest, "releaseArtifactDigest");
        manifestFingerprint = requireSha256(manifestFingerprint, "manifestFingerprint");
        manifestSchemaVersion = requireText(manifestSchemaVersion, "manifestSchemaVersion");
        backtestRunId = requireText(backtestRunId, "backtestRunId");
        strategyVersionId = requireText(strategyVersionId, "strategyVersionId");
        Objects.requireNonNull(datasetId, "datasetId must not be null");
        evaluationId = requireText(evaluationId, "evaluationId");
        Objects.requireNonNull(windowStart, "windowStart must not be null");
        Objects.requireNonNull(windowEnd, "windowEnd must not be null");
        strategyVersionStatus = requireText(strategyVersionStatus, "strategyVersionStatus");
        evaluationStatus = requireText(evaluationStatus, "evaluationStatus");
        publishStatus = requireText(publishStatus, "publishStatus");
        Objects.requireNonNull(authorizationBoundary, "authorizationBoundary must not be null");
        sideEffectPolicyVersion = requireText(sideEffectPolicyVersion, "sideEffectPolicyVersion");
        Objects.requireNonNull(sideEffectPolicy, "sideEffectPolicy must not be null");
        admissionFingerprint = requireSha256(admissionFingerprint, "admissionFingerprint");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt must not be null");
    }

    public boolean hasSupportedSchema() {
        return guardSchemaVersion == SUPPORTED_GUARD_SCHEMA_VERSION;
    }

    public boolean matchesState(StrategyReleaseAdmissionState state) {
        return state != null
                && guardSchemaVersion == state.guardSchemaVersion()
                && admissionRevision == state.admissionRevision()
                && Objects.equals(releaseArtifactDigest, state.releaseArtifactDigest())
                && Objects.equals(manifestFingerprint, state.manifestFingerprint())
                && Objects.equals(manifestSchemaVersion, state.manifestSchemaVersion());
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static String requireSha256(String value, String name) {
        String normalized = requireText(value, name);
        if (!SHA_256.matcher(normalized).matches()) {
            throw new IllegalArgumentException(name + " must be lowercase SHA-256");
        }
        return normalized;
    }
}
