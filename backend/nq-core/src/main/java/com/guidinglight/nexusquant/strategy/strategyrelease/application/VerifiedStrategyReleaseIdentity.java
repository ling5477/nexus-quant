package com.guidinglight.nexusquant.strategy.strategyrelease.application;

import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactManifest;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyReleaseManifestFingerprinter;
import com.guidinglight.nexusquant.strategy.strategyrelease.domain.StrategyRelease;
import com.guidinglight.nexusquant.strategy.strategyrelease.domain.StrategyReleaseStatus;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 只能从 production verifier 返回的 {@link StrategyReleaseStatus#VERIFIED} aggregate 构造的 binding command。
 * 不接受 path、trusted root、raw manifest、HTTP locator 或 client-supplied digest。
 */
public record VerifiedStrategyReleaseIdentity(
        String publishRecordId,
        String strategyVersionId,
        UUID datasetId,
        String evaluationId,
        String releaseArtifactDigest,
        String manifestFingerprint,
        String manifestSchemaVersion
) {
    private static final Pattern SHA_256 = Pattern.compile("^[0-9a-f]{64}$");

    public VerifiedStrategyReleaseIdentity {
        requireText(publishRecordId, "publishRecordId");
        requireText(strategyVersionId, "strategyVersionId");
        Objects.requireNonNull(datasetId, "datasetId must not be null");
        requireText(evaluationId, "evaluationId");
        if (!SHA_256.matcher(requireText(releaseArtifactDigest, "releaseArtifactDigest")).matches()
                || !SHA_256.matcher(requireText(manifestFingerprint, "manifestFingerprint")).matches()) {
            throw new IllegalArgumentException("release identity digests must be lowercase SHA-256");
        }
        if (!StrategyArtifactManifest.SUPPORTED_SCHEMA_VERSION.equals(manifestSchemaVersion)) {
            throw new IllegalArgumentException("unsupported manifest schema version");
        }
    }

    public static VerifiedStrategyReleaseIdentity fromVerifiedRelease(
            StrategyRelease release,
            StrategyReleaseManifestFingerprinter fingerprinter
    ) {
        Objects.requireNonNull(release, "release must not be null");
        Objects.requireNonNull(fingerprinter, "fingerprinter must not be null");
        if (release.releaseStatus() != StrategyReleaseStatus.VERIFIED
                || release.verificationResult() == null
                || release.verificationResult().status()
                != com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactVerificationResult.Status.VERIFIED) {
            throw new IllegalArgumentException("only a server-verified release can bind identity");
        }
        StrategyArtifactManifest manifest = Objects.requireNonNull(
                release.artifactManifest(),
                "manifest must not be null"
        );
        if (!Objects.equals(release.artifactDigest(), release.verificationResult().artifactDigest())
                || !Objects.equals(release.artifactDigest(), manifest.artifactDigest())) {
            throw new IllegalArgumentException("verified release digest facts disagree");
        }
        return new VerifiedStrategyReleaseIdentity(
                release.publishRecordId(),
                manifest.strategyVersionId(),
                manifest.datasetId(),
                manifest.evaluationId(),
                release.artifactDigest(),
                fingerprinter.fingerprint(manifest),
                manifest.schemaVersion()
        );
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
