package com.guidinglight.nexusquant.strategy.strategyrelease.application;

import java.time.Instant;
import java.util.Objects;

/** 持久化 admission state 的稳定 typed view。 */
public record StrategyReleaseAdmissionState(
        String publishRecordId,
        long admissionRevision,
        int guardSchemaVersion,
        String releaseArtifactDigest,
        String manifestFingerprint,
        String manifestSchemaVersion,
        Instant identityBoundAt,
        Instant createdAt,
        Instant updatedAt
) {
    public StrategyReleaseAdmissionState {
        Objects.requireNonNull(publishRecordId, "publishRecordId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (admissionRevision < 0 || guardSchemaVersion < 1) {
            throw new IllegalArgumentException("invalid admission state version");
        }
        boolean unbound = releaseArtifactDigest == null
                && manifestFingerprint == null
                && manifestSchemaVersion == null
                && identityBoundAt == null;
        boolean bound = releaseArtifactDigest != null
                && manifestFingerprint != null
                && manifestSchemaVersion != null
                && identityBoundAt != null;
        if (!unbound && !bound) {
            throw new IllegalArgumentException("release identity quartet must be complete or absent");
        }
    }

    public boolean identityBound() {
        return releaseArtifactDigest != null;
    }
}
