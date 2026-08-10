package com.guidinglight.nexusquant.strategy.strategyrelease.application;

import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactManifest;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactManifest.ArtifactFile;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactVerificationPolicy;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactVerificationResult.FindingCode;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.TrustedRootStrategyArtifactVerifier;
import com.guidinglight.nexusquant.strategy.strategyrelease.domain.StrategyRelease;
import com.guidinglight.nexusquant.strategy.strategyrelease.domain.StrategyReleaseStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Strategy Release production aggregate、provenance 与 artifact verification 回归测试。 */
class StrategyReleaseProductionServiceTest {

    private static final String PUBLISH_ID = "pub-gatex-1";
    private static final String RUN_ID = "run-gatex-1";
    private static final String STRATEGY_VERSION_ID = "sv-gatex-1";
    private static final UUID DATASET_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final String EVALUATION_ID = "eval-gatex-1";
    private static final Instant CREATED_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant PUBLISHED_AT = Instant.parse("2026-08-01T00:01:00Z");

    @TempDir
    Path tempDir;

    @Test
    void shouldVerifyCanonicalPublishAnchoredRelease() throws Exception {
        StrategyArtifactManifest manifest = manifest(tempDir, "artifacts/evaluation.json", "{\"score\":1}");

        StrategyRelease result = service(facts()).verify(command(tempDir, manifest));

        assertEquals(
                StrategyReleaseStatus.VERIFIED,
                result.releaseStatus(),
                () -> "verification=" + result.verificationResult()
        );
        assertEquals(PUBLISH_ID, result.releaseAnchorId());
        assertEquals(PUBLISH_ID, result.publishRecordId());
        assertEquals(STRATEGY_VERSION_ID, result.strategyVersionId());
        assertEquals(DATASET_ID, result.datasetId());
        assertEquals(EVALUATION_ID, result.evaluationId());
        assertEquals(manifest.artifactDigest(), result.artifactDigest());
        assertEquals(1, result.verificationResult().verifiedFileCount());
        assertEquals(CREATED_AT, result.createdAt());
        assertEquals(PUBLISHED_AT, result.publishedAt());
        assertNull(result.verificationResult().reasonCode());
    }

    @Test
    void shouldRejectPublishIdentityMismatch() throws Exception {
        StrategyArtifactManifest manifest = manifest(tempDir, "artifact.json", "{\"score\":1}");
        StrategyReleaseProvenanceFacts mismatched = copyFacts(
                "pub-other",
                STRATEGY_VERSION_ID,
                STRATEGY_VERSION_ID,
                DATASET_ID,
                EVALUATION_ID
        );

        assertRejected(service(mismatched).verify(command(tempDir, manifest)), FindingCode.PUBLISH_IDENTITY_MISMATCH);
    }

    @Test
    void shouldRejectStrategyVersionMismatch() throws Exception {
        StrategyArtifactManifest manifest = copyManifest(
                manifest(tempDir, "artifact.json", "{\"score\":1}"),
                StrategyArtifactManifest.SUPPORTED_SCHEMA_VERSION,
                "sv-other",
                DATASET_ID,
                EVALUATION_ID,
                null
        );

        assertRejected(service(facts()).verify(command(tempDir, manifest)), FindingCode.STRATEGY_VERSION_MISMATCH);
    }

    @Test
    void shouldRejectDatasetMismatch() throws Exception {
        StrategyArtifactManifest manifest = copyManifest(
                manifest(tempDir, "artifact.json", "{\"score\":1}"),
                StrategyArtifactManifest.SUPPORTED_SCHEMA_VERSION,
                STRATEGY_VERSION_ID,
                UUID.fromString("33333333-3333-4333-8333-333333333333"),
                EVALUATION_ID,
                null
        );

        assertRejected(service(facts()).verify(command(tempDir, manifest)), FindingCode.DATASET_MISMATCH);
    }

    @Test
    void shouldRejectEvaluationMismatch() throws Exception {
        StrategyArtifactManifest manifest = copyManifest(
                manifest(tempDir, "artifact.json", "{\"score\":1}"),
                StrategyArtifactManifest.SUPPORTED_SCHEMA_VERSION,
                STRATEGY_VERSION_ID,
                DATASET_ID,
                "eval-other",
                null
        );

        assertRejected(service(facts()).verify(command(tempDir, manifest)), FindingCode.EVALUATION_MISMATCH);
    }

    @Test
    void shouldRejectUnknownSchemaVersion() throws Exception {
        StrategyArtifactManifest manifest = copyManifest(
                manifest(tempDir, "artifact.json", "{\"score\":1}"),
                "strategy-release-manifest.v999",
                STRATEGY_VERSION_ID,
                DATASET_ID,
                EVALUATION_ID,
                null
        );

        assertRejected(service(facts()).verify(command(tempDir, manifest)), FindingCode.UNSUPPORTED_SCHEMA_VERSION);
    }

    @Test
    void shouldRejectMissingManifestField() throws Exception {
        StrategyArtifactManifest valid = manifest(tempDir, "artifact.json", "{\"score\":1}");
        StrategyArtifactManifest manifest = new StrategyArtifactManifest(
                valid.schemaVersion(),
                null,
                valid.datasetId(),
                valid.evaluationId(),
                valid.artifactFiles(),
                valid.artifactDigest(),
                valid.generatedAt(),
                valid.generatorVersion()
        );

        assertRejected(service(facts()).verify(command(tempDir, manifest)), FindingCode.MANIFEST_FIELD_MISSING);
    }

    @Test
    void shouldRejectInvalidAggregateDigest() throws Exception {
        StrategyArtifactManifest manifest = copyManifest(
                manifest(tempDir, "artifact.json", "{\"score\":1}"),
                StrategyArtifactManifest.SUPPORTED_SCHEMA_VERSION,
                STRATEGY_VERSION_ID,
                DATASET_ID,
                EVALUATION_ID,
                "INVALID"
        );

        assertRejected(service(facts()).verify(command(tempDir, manifest)), FindingCode.INVALID_DIGEST);
    }

    @Test
    void shouldRejectMissingArtifact() throws Exception {
        StrategyArtifactManifest manifest = manifest(tempDir, "artifact.json", "{\"score\":1}");
        Files.delete(tempDir.resolve("artifact.json"));

        assertRejected(service(facts()).verify(command(tempDir, manifest)), FindingCode.ARTIFACT_NOT_FOUND);
    }

    @Test
    void shouldRejectExtraUndeclaredArtifact() throws Exception {
        StrategyArtifactManifest manifest = manifest(tempDir, "artifact.json", "{\"score\":1}");
        Files.writeString(tempDir.resolve("extra.json"), "{\"extra\":true}", StandardCharsets.UTF_8);

        assertRejected(service(facts()).verify(command(tempDir, manifest)), FindingCode.UNDECLARED_ARTIFACT);
    }

    @Test
    void shouldRejectPathTraversal() throws Exception {
        StrategyArtifactManifest valid = manifest(tempDir, "artifact.json", "{\"score\":1}");
        ArtifactFile unsafe = copyArtifact(valid.artifactFiles().getFirst(), "../outside.json");
        StrategyArtifactManifest manifest = withArtifacts(valid, List.of(unsafe));

        assertRejected(service(facts()).verify(command(tempDir, manifest)), FindingCode.PATH_ESCAPES_TRUSTED_ROOT);
    }

    @Test
    void shouldRejectAbsolutePath() throws Exception {
        StrategyArtifactManifest valid = manifest(tempDir, "artifact.json", "{\"score\":1}");
        ArtifactFile unsafe = copyArtifact(valid.artifactFiles().getFirst(), "C:/outside.json");
        StrategyArtifactManifest manifest = withArtifacts(valid, List.of(unsafe));

        assertRejected(service(facts()).verify(command(tempDir, manifest)), FindingCode.INVALID_RELATIVE_PATH);
    }

    @Test
    void shouldRejectSensitiveArtifactValueWithoutReturningContent() throws Exception {
        StrategyArtifactManifest manifest = manifest(
                tempDir,
                "artifact.json",
                "{\"apiKey\":\"synthetic-sensitive-value\"}"
        );

        StrategyRelease result = service(facts()).verify(command(tempDir, manifest));

        assertRejected(result, FindingCode.SENSITIVE_ARTIFACT_VALUE);
        assertEquals("artifact.json", result.verificationResult().safeRelativeIdentifier());
        assertNull(result.verificationResult().artifactDigest());
    }

    @Test
    void shouldRejectPerFileSizeLimit() throws Exception {
        StrategyArtifactManifest manifest = manifest(tempDir, "artifact.json", "12345");
        StrategyReleaseProductionService service = service(
                facts(),
                new StrategyArtifactVerificationPolicy(4, 4, 16)
        );

        assertRejected(service.verify(command(tempDir, manifest)), FindingCode.ARTIFACT_TOO_LARGE);
    }

    @Test
    void shouldRejectFileCountLimit() throws Exception {
        StrategyArtifactManifest manifest = manifest(
                tempDir,
                List.of(
                        new TestArtifact("one.json", "{\"one\":1}"),
                        new TestArtifact("two.json", "{\"two\":2}")
                )
        );
        StrategyReleaseProductionService service = service(
                facts(),
                new StrategyArtifactVerificationPolicy(1, 1024, 2048)
        );

        assertRejected(service.verify(command(tempDir, manifest)), FindingCode.ARTIFACT_COUNT_LIMIT_EXCEEDED);
    }

    @Test
    void shouldReturnDeterministicResultForStableInputs() throws Exception {
        StrategyArtifactManifest manifest = manifest(tempDir, "artifact.json", "{\"score\":1}");
        StrategyReleaseProductionService service = service(facts());

        StrategyRelease first = service.verify(command(tempDir, manifest));
        StrategyRelease second = service.verify(command(tempDir, manifest));

        assertEquals(first, second);
        assertNotNull(first.verificationResult().artifactDigest());
    }

    private StrategyReleaseProductionService service(StrategyReleaseProvenanceFacts facts) {
        return service(facts, new StrategyArtifactVerificationPolicy(8, 1024 * 1024, 4 * 1024 * 1024));
    }

    private StrategyReleaseProductionService service(
            StrategyReleaseProvenanceFacts facts,
            StrategyArtifactVerificationPolicy policy
    ) {
        return new StrategyReleaseProductionService(
                ignored -> facts,
                new TrustedRootStrategyArtifactVerifier(policy)
        );
    }

    private StrategyReleaseProductionService.VerificationCommand command(
            Path root,
            StrategyArtifactManifest manifest
    ) {
        return new StrategyReleaseProductionService.VerificationCommand(PUBLISH_ID, root, manifest);
    }

    private StrategyArtifactManifest manifest(Path root, String relativePath, String content) throws IOException {
        return manifest(root, List.of(new TestArtifact(relativePath, content)));
    }

    private StrategyArtifactManifest manifest(Path root, List<TestArtifact> testArtifacts) throws IOException {
        List<ArtifactFile> files = new ArrayList<>();
        for (TestArtifact testArtifact : testArtifacts) {
            Path target = root.resolve(testArtifact.relativePath());
            Files.createDirectories(target.getParent() == null ? root : target.getParent());
            byte[] content = testArtifact.content().getBytes(StandardCharsets.UTF_8);
            Files.write(target, content);
            files.add(new ArtifactFile(
                    target.getFileName().toString().replace(".json", "-artifact"),
                    testArtifact.relativePath(),
                    sha256(content),
                    content.length,
                    "application/json"
            ));
        }
        return new StrategyArtifactManifest(
                StrategyArtifactManifest.SUPPORTED_SCHEMA_VERSION,
                STRATEGY_VERSION_ID,
                DATASET_ID,
                EVALUATION_ID,
                files,
                TrustedRootStrategyArtifactVerifier.computeArtifactDigest(files),
                Instant.parse("2026-08-01T00:00:30Z"),
                "nq-research/1.0"
        );
    }

    private StrategyArtifactManifest copyManifest(
            StrategyArtifactManifest source,
            String schemaVersion,
            String strategyVersionId,
            UUID datasetId,
            String evaluationId,
            String artifactDigest
    ) {
        return new StrategyArtifactManifest(
                schemaVersion,
                strategyVersionId,
                datasetId,
                evaluationId,
                source.artifactFiles(),
                artifactDigest == null ? source.artifactDigest() : artifactDigest,
                source.generatedAt(),
                source.generatorVersion()
        );
    }

    private StrategyArtifactManifest withArtifacts(StrategyArtifactManifest source, List<ArtifactFile> files) {
        return new StrategyArtifactManifest(
                source.schemaVersion(),
                source.strategyVersionId(),
                source.datasetId(),
                source.evaluationId(),
                files,
                TrustedRootStrategyArtifactVerifier.computeArtifactDigest(files),
                source.generatedAt(),
                source.generatorVersion()
        );
    }

    private ArtifactFile copyArtifact(ArtifactFile source, String relativePath) {
        return new ArtifactFile(
                source.logicalName(),
                relativePath,
                source.sha256(),
                source.sizeBytes(),
                source.mediaType()
        );
    }

    private StrategyReleaseProvenanceFacts facts() {
        return copyFacts(
                PUBLISH_ID,
                STRATEGY_VERSION_ID,
                STRATEGY_VERSION_ID,
                DATASET_ID,
                EVALUATION_ID
        );
    }

    private StrategyReleaseProvenanceFacts copyFacts(
            String publishId,
            String publishStrategyVersionId,
            String runStrategyVersionId,
            UUID datasetId,
            String evaluationId
    ) {
        return new StrategyReleaseProvenanceFacts(
                true,
                publishId,
                RUN_ID,
                publishStrategyVersionId,
                runStrategyVersionId,
                datasetId,
                evaluationId,
                RUN_ID,
                "SUCCEEDED",
                "SUCCEEDED",
                true,
                true,
                CREATED_AT,
                PUBLISHED_AT
        );
    }

    private void assertRejected(StrategyRelease result, FindingCode findingCode) {
        assertEquals(StrategyReleaseStatus.REJECTED, result.releaseStatus());
        assertEquals(findingCode, result.verificationResult().reasonCode());
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private record TestArtifact(String relativePath, String content) {
    }
}
