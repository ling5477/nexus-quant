package com.guidinglight.nexusquant.livecontrol.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Session-bound immutable pilot scope；不拥有 account、release、risk 或交易事实。
 */
public record PilotScopeBinding(
        UUID id,
        UUID sessionId,
        String instrumentMetadataDigest,
        String instrumentSourceIdentity,
        String instrumentSourceSchemaVersion,
        long instrumentMaximumAgeMs,
        String feeScheduleDigest,
        String feeTier,
        FeeEvidenceClass feeEvidenceClass,
        String feeSourceIdentity,
        String feeSourceSchemaVersion,
        long feeMaximumAgeMs,
        String balanceSourceIdentity,
        String balanceSourceSchemaVersion,
        long balanceMaximumAgeMs,
        String clockSourceIdentity,
        String clockSourceSchemaVersion,
        long clockMaximumAgeMs,
        String signedTimestampSource,
        long maximumToleratedSkewMs,
        String endpointPolicyVersion,
        String endpointPolicyDigest,
        String providerContractIdentity,
        String providerArtifactDigest,
        String workerIdentity,
        String workerReleaseDigest,
        String pilotScopeHash,
        long createdBy,
        Instant createdAt
) {
    public static final String SCHEMA_VERSION = "pilot-scope.v1";
    public static final String OPERATOR_PILOT_SCHEMA_VERSION = "pilot-scope.operator-pilot.v1";
    public static final String SIGNED_TIMESTAMP_SOURCE = "NTP_DISCIPLINED_SYSTEM_CLOCK";

    public PilotScopeBinding {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        requireDigest(instrumentMetadataDigest, "instrumentMetadataDigest");
        requireText(instrumentSourceIdentity, 128, "instrumentSourceIdentity");
        requireText(instrumentSourceSchemaVersion, 64, "instrumentSourceSchemaVersion");
        require(instrumentMaximumAgeMs >= 1 && instrumentMaximumAgeMs <= 300_000,
                "instrumentMaximumAgeMs is outside the hard bound");
        requireDigest(feeScheduleDigest, "feeScheduleDigest");
        requireText(feeTier, 64, "feeTier");
        Objects.requireNonNull(feeEvidenceClass, "feeEvidenceClass must not be null");
        requireText(feeSourceIdentity, 128, "feeSourceIdentity");
        requireText(feeSourceSchemaVersion, 64, "feeSourceSchemaVersion");
        require(feeMaximumAgeMs >= 1 && feeMaximumAgeMs <= 3_600_000,
                "feeMaximumAgeMs is outside the hard bound");
        requireText(balanceSourceIdentity, 128, "balanceSourceIdentity");
        requireText(balanceSourceSchemaVersion, 64, "balanceSourceSchemaVersion");
        require(balanceMaximumAgeMs >= 1 && balanceMaximumAgeMs <= 10_000,
                "balanceMaximumAgeMs is outside the hard bound");
        requireText(clockSourceIdentity, 128, "clockSourceIdentity");
        requireText(clockSourceSchemaVersion, 64, "clockSourceSchemaVersion");
        require(clockMaximumAgeMs >= 1 && clockMaximumAgeMs <= 60_000,
                "clockMaximumAgeMs is outside the hard bound");
        require(SIGNED_TIMESTAMP_SOURCE.equals(signedTimestampSource), "signedTimestampSource is unsupported");
        require(maximumToleratedSkewMs >= 0 && maximumToleratedSkewMs <= 1_000,
                "maximumToleratedSkewMs is outside the hard bound");
        requireText(endpointPolicyVersion, 64, "endpointPolicyVersion");
        requireDigest(endpointPolicyDigest, "endpointPolicyDigest");
        requireText(providerContractIdentity, 128, "providerContractIdentity");
        requireDigest(providerArtifactDigest, "providerArtifactDigest");
        requireText(workerIdentity, 128, "workerIdentity");
        requireDigest(workerReleaseDigest, "workerReleaseDigest");
        requireDigest(pilotScopeHash, "pilotScopeHash");
        require(createdBy > 0, "createdBy must be positive");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public boolean hasCanonicalHash(LiveSession session) {
        return session.id().equals(sessionId) && pilotScopeHash.equals(PilotScopeCanonicalEncoder.digest(session, this));
    }

    public PilotScopeBinding withCanonicalHash(LiveSession session) {
        require(session.id().equals(sessionId), "session identity mismatch");
        return new PilotScopeBinding(
                id, sessionId, instrumentMetadataDigest, instrumentSourceIdentity,
                instrumentSourceSchemaVersion, instrumentMaximumAgeMs, feeScheduleDigest, feeTier,
                feeEvidenceClass, feeSourceIdentity, feeSourceSchemaVersion, feeMaximumAgeMs,
                balanceSourceIdentity, balanceSourceSchemaVersion, balanceMaximumAgeMs,
                clockSourceIdentity, clockSourceSchemaVersion, clockMaximumAgeMs, signedTimestampSource,
                maximumToleratedSkewMs, endpointPolicyVersion, endpointPolicyDigest,
                providerContractIdentity, providerArtifactDigest, workerIdentity, workerReleaseDigest,
                PilotScopeCanonicalEncoder.digest(session, this), createdBy, createdAt
        );
    }

    static void requireDigest(String value, String name) {
        require(value != null && value.matches("[0-9a-f]{64}"), name + " must be lowercase SHA-256");
    }

    static void requireText(String value, int maximumLength, String name) {
        require(value != null && !value.isBlank() && value.length() <= maximumLength, name + " is invalid");
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    public enum FeeEvidenceClass {
        OBSERVED_PRIVATE,
        ESTIMATED_PUBLIC
    }
}
