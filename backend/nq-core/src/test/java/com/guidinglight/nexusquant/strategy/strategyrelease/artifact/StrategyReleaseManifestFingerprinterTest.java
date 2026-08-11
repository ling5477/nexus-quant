package com.guidinglight.nexusquant.strategy.strategyrelease.artifact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guidinglight.nexusquant.strategy.strategyrelease.application.VerifiedStrategyReleaseIdentity;
import com.guidinglight.nexusquant.strategy.strategyrelease.domain.StrategyRelease;
import com.guidinglight.nexusquant.strategy.strategyrelease.domain.StrategyReleaseStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class StrategyReleaseManifestFingerprinterTest {

    private static final String DIGEST_A = "a".repeat(64);
    private static final String DIGEST_B = "b".repeat(64);
    private static final UUID DATASET_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final Instant GENERATED_AT = Instant.parse("2026-08-11T10:11:12.123456789Z");
    private final StrategyReleaseManifestFingerprinter fingerprinter = new StrategyReleaseManifestFingerprinter();

    @Test
    void descriptorOrderAndLocaleIndependentCanonicalEncodingShouldBeStable() {
        StrategyArtifactManifest first = manifest(List.of(
                artifact("model", "model.bin", DIGEST_A, 20, "application/octet-stream"),
                artifact("config", "config.json", DIGEST_B, 10, "application/json")
        ));
        StrategyArtifactManifest reversed = manifest(List.of(
                artifact("config", "config.json", DIGEST_B, 10, "application/json"),
                artifact("model", "model.bin", DIGEST_A, 20, "application/octet-stream")
        ));

        assertEquals(fingerprinter.fingerprint(first), fingerprinter.fingerprint(reversed));
        assertEquals(64, fingerprinter.fingerprint(first).length());
    }

    @Test
    void everyFrozenIdentityFieldShouldAffectFingerprint() {
        StrategyArtifactManifest baseline = manifest(List.of(
                artifact("config", "config.json", DIGEST_B, 10, "application/json")
        ));
        String expected = fingerprinter.fingerprint(baseline);

        assertNotEquals(expected, fingerprinter.fingerprint(new StrategyArtifactManifest(
                baseline.schemaVersion(), "strategy-version-2", baseline.datasetId(), baseline.evaluationId(),
                baseline.artifactFiles(), baseline.artifactDigest(), baseline.generatedAt(), baseline.generatorVersion()
        )));
        assertNotEquals(expected, fingerprinter.fingerprint(new StrategyArtifactManifest(
                baseline.schemaVersion(), baseline.strategyVersionId(), UUID.randomUUID(), baseline.evaluationId(),
                baseline.artifactFiles(), baseline.artifactDigest(), baseline.generatedAt(), baseline.generatorVersion()
        )));
        assertNotEquals(expected, fingerprinter.fingerprint(new StrategyArtifactManifest(
                baseline.schemaVersion(), baseline.strategyVersionId(), baseline.datasetId(), "evaluation-2",
                baseline.artifactFiles(), baseline.artifactDigest(), baseline.generatedAt(), baseline.generatorVersion()
        )));
        assertNotEquals(expected, fingerprinter.fingerprint(new StrategyArtifactManifest(
                baseline.schemaVersion(), baseline.strategyVersionId(), baseline.datasetId(), baseline.evaluationId(),
                baseline.artifactFiles(), DIGEST_B, baseline.generatedAt(), baseline.generatorVersion()
        )));
        assertNotEquals(expected, fingerprinter.fingerprint(new StrategyArtifactManifest(
                baseline.schemaVersion(), baseline.strategyVersionId(), baseline.datasetId(), baseline.evaluationId(),
                baseline.artifactFiles(), baseline.artifactDigest(), baseline.generatedAt().plusNanos(1), baseline.generatorVersion()
        )));
        assertNotEquals(expected, fingerprinter.fingerprint(new StrategyArtifactManifest(
                baseline.schemaVersion(), baseline.strategyVersionId(), baseline.datasetId(), baseline.evaluationId(),
                List.of(artifact("config", "config.json", DIGEST_B, 11, "application/json")),
                baseline.artifactDigest(), baseline.generatedAt(), baseline.generatorVersion()
        )));
    }

    @Test
    void verifiedIdentityCanOnlyBeDerivedFromVerifiedServerAggregate() {
        StrategyArtifactManifest manifest = manifest(List.of(
                artifact("config", "config.json", DIGEST_B, 10, "application/json")
        ));
        StrategyArtifactVerificationResult verified = StrategyArtifactVerificationResult.verified(DIGEST_A, 1, 10);
        StrategyRelease release = new StrategyRelease(
                "publish-1",
                "publish-1",
                manifest.strategyVersionId(),
                manifest.datasetId(),
                manifest.evaluationId(),
                manifest,
                DIGEST_A,
                StrategyReleaseStatus.VERIFIED,
                verified,
                GENERATED_AT,
                GENERATED_AT
        );

        VerifiedStrategyReleaseIdentity identity = VerifiedStrategyReleaseIdentity.fromVerifiedRelease(
                release,
                fingerprinter
        );
        assertEquals(DIGEST_A, identity.releaseArtifactDigest());
        assertEquals(fingerprinter.fingerprint(manifest), identity.manifestFingerprint());

        StrategyRelease rejected = new StrategyRelease(
                "publish-1",
                "publish-1",
                manifest.strategyVersionId(),
                manifest.datasetId(),
                manifest.evaluationId(),
                manifest,
                DIGEST_A,
                StrategyReleaseStatus.REJECTED,
                StrategyArtifactVerificationResult.rejected(
                        StrategyArtifactVerificationResult.FindingCode.DIGEST_MISMATCH,
                        "config.json"
                ),
                GENERATED_AT,
                GENERATED_AT
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> VerifiedStrategyReleaseIdentity.fromVerifiedRelease(rejected, fingerprinter)
        );
    }

    private StrategyArtifactManifest manifest(List<StrategyArtifactManifest.ArtifactFile> artifacts) {
        return new StrategyArtifactManifest(
                StrategyArtifactManifest.SUPPORTED_SCHEMA_VERSION,
                "strategy-version-1",
                DATASET_ID,
                "evaluation-1",
                artifacts,
                DIGEST_A,
                GENERATED_AT,
                "nq-test-generator-1"
        );
    }

    private StrategyArtifactManifest.ArtifactFile artifact(
            String logicalName,
            String relativePath,
            String digest,
            long size,
            String mediaType
    ) {
        return new StrategyArtifactManifest.ArtifactFile(logicalName, relativePath, digest, size, mediaType);
    }
}
