package com.guidinglight.nexusquant.strategy.infra.artifact;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseArtifactBindingResolver.ArtifactBindingResolution;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseProductionService;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseProvenanceFacts;
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
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Server-controlled storage-key、manifest loading 与 link/TOCTOU 边界回归。 */
class ServerControlledStrategyArtifactBindingResolverTest {

    private static final String ARTIFACT_KEY = "artifact_release_01";
    private static final String MANIFEST_KEY = "manifest_release_01.json";

    @TempDir
    Path tempDir;

    @Test
    void shouldResolveDirectChildrenAndLoadManifestDeterministically() throws Exception {
        Layout layout = createLayout(tempDir.resolve("root"));
        ServerControlledStrategyArtifactBindingResolver resolver = resolver(layout.root());

        ArtifactBindingResolution first = resolver.resolve(ARTIFACT_KEY, MANIFEST_KEY);
        ArtifactBindingResolution second = resolver.resolve(ARTIFACT_KEY, MANIFEST_KEY);

        assertTrue(first.resolved());
        assertEquals(layout.artifactRoot().toRealPath(), first.artifactRoot());
        assertEquals(layout.manifest(), first.manifest());
        assertEquals(first, second);
    }

    @Test
    void shouldVerifyReleaseThroughPersistedLocatorAndServerControlledResolver() throws Exception {
        Layout layout = createLayout(tempDir.resolve("root"));
        StrategyReleaseProvenanceFacts facts = new StrategyReleaseProvenanceFacts(
                true,
                "publish-gatex-4c",
                "run-gatex-4c",
                "sv-gatex-4c",
                "sv-gatex-4c",
                UUID.fromString("22222222-2222-4222-8222-222222222222"),
                "eval-gatex-4c",
                "run-gatex-4c",
                "SUCCEEDED",
                "SUCCEEDED",
                true,
                true,
                Instant.parse("2026-08-10T00:00:00Z"),
                Instant.parse("2026-08-10T00:01:00Z"),
                ARTIFACT_KEY,
                MANIFEST_KEY
        );
        StrategyReleaseProductionService service = new StrategyReleaseProductionService(
                ignored -> facts,
                resolver(layout.root()),
                new TrustedRootStrategyArtifactVerifier(
                        new StrategyArtifactVerificationPolicy(8, 1024 * 1024, 4 * 1024 * 1024)
                )
        );

        StrategyRelease release = service.verify("publish-gatex-4c");

        assertEquals(StrategyReleaseStatus.VERIFIED, release.releaseStatus());
        assertEquals(layout.manifest().artifactDigest(), release.artifactDigest());
        assertEquals(1, release.verificationResult().verifiedFileCount());
    }

    @Test
    void shouldFailClosedForUnconfiguredBlankRelativeAndMissingRoot() {
        assertRejected(
                resolver((String) null).resolve(ARTIFACT_KEY, MANIFEST_KEY),
                FindingCode.ARTIFACT_ROOT_NOT_CONFIGURED
        );
        assertRejected(resolver("   ").resolve(ARTIFACT_KEY, MANIFEST_KEY), FindingCode.ARTIFACT_ROOT_NOT_CONFIGURED);
        assertRejected(resolver("relative-root").resolve(ARTIFACT_KEY, MANIFEST_KEY), FindingCode.ARTIFACT_ROOT_INVALID);
        assertRejected(
                resolver(tempDir.resolve("missing")).resolve(ARTIFACT_KEY, MANIFEST_KEY),
                FindingCode.ARTIFACT_ROOT_INVALID
        );
    }

    @Test
    void shouldRejectLegacyInvalidAndMissingLocations() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("root"));
        assertRejected(resolver(root).resolve(null, null), FindingCode.ARTIFACT_LOCATION_UNBOUND);
        List<String> unsafeKeys = List.of(
                ".",
                "../escape",
                "nested/path",
                "nested\\path",
                "C:escape",
                "file://artifact",
                "artifact%2Fescape",
                "\\\\server\\share",
                "artifact\u2215escape",
                "artifact\uFF0Fescape",
                " artifact",
                "artifact ",
                "artifact\nname",
                "a".repeat(129)
        );
        for (String unsafeKey : unsafeKeys) {
            assertRejected(
                    resolver(root).resolve(unsafeKey, MANIFEST_KEY),
                    FindingCode.ARTIFACT_LOCATION_UNSAFE
            );
        }
        assertRejected(resolver(root).resolve(null, MANIFEST_KEY), FindingCode.ARTIFACT_LOCATION_UNSAFE);
        assertRejected(resolver(root).resolve(ARTIFACT_KEY, MANIFEST_KEY), FindingCode.ARTIFACT_LOCATION_NOT_FOUND);

        Files.createDirectory(root.resolve(ARTIFACT_KEY));
        assertRejected(resolver(root).resolve(ARTIFACT_KEY, MANIFEST_KEY), FindingCode.ARTIFACT_MANIFEST_NOT_FOUND);
    }

    @Test
    void shouldRejectInvalidOversizedAndNonRegularManifestWithoutLeakingContent() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("root"));
        Files.createDirectory(root.resolve(ARTIFACT_KEY));
        Path manifest = root.resolve(MANIFEST_KEY);
        String raw = "{\"private_key\":\"synthetic-secret\"}";
        Files.writeString(manifest, raw, StandardCharsets.UTF_8);

        ArtifactBindingResolution invalid = resolver(root).resolve(ARTIFACT_KEY, MANIFEST_KEY);
        assertRejected(invalid, FindingCode.ARTIFACT_MANIFEST_INVALID);
        assertFalse(invalid.safeStorageIdentifier().contains(raw));
        assertFalse(invalid.safeStorageIdentifier().contains(root.toString()));

        Files.write(manifest, new byte[(int) ServerControlledStrategyArtifactBindingResolver.MAX_MANIFEST_BYTES + 1]);
        assertRejected(resolver(root).resolve(ARTIFACT_KEY, MANIFEST_KEY), FindingCode.ARTIFACT_MANIFEST_INVALID);

        Files.delete(manifest);
        Files.createDirectory(manifest);
        assertRejected(resolver(root).resolve(ARTIFACT_KEY, MANIFEST_KEY), FindingCode.ARTIFACT_LOCATION_UNSAFE);
    }

    @Test
    void shouldRejectDuplicateManifestIdentityKey() throws Exception {
        Layout layout = createLayout(tempDir.resolve("root"));
        String validJson = Files.readString(layout.root().resolve(MANIFEST_KEY), StandardCharsets.UTF_8);
        String identityField = "\"strategyVersionId\"";
        int identityOffset = validJson.indexOf(identityField);
        assertTrue(identityOffset > 0);
        String duplicateJson = validJson.substring(0, identityOffset)
                + "\"strategyVersionId\":\"sv-shadowed\","
                + validJson.substring(identityOffset);
        Files.writeString(
                layout.root().resolve(MANIFEST_KEY),
                duplicateJson,
                StandardCharsets.UTF_8
        );

        assertRejected(
                resolver(layout.root()).resolve(ARTIFACT_KEY, MANIFEST_KEY),
                FindingCode.ARTIFACT_MANIFEST_INVALID
        );
    }

    @Test
    void shouldRejectUnknownFieldTrailingTokenAndMalformedEncoding() throws Exception {
        Layout layout = createLayout(tempDir.resolve("root"));
        Path manifestPath = layout.root().resolve(MANIFEST_KEY);
        String validJson = Files.readString(manifestPath, StandardCharsets.UTF_8);

        String unknownField = validJson.substring(0, validJson.length() - 1)
                + ",\"unexpected\":true}";
        Files.writeString(manifestPath, unknownField, StandardCharsets.UTF_8);
        assertRejected(
                resolver(layout.root()).resolve(ARTIFACT_KEY, MANIFEST_KEY),
                FindingCode.ARTIFACT_MANIFEST_INVALID
        );

        Files.writeString(manifestPath, validJson + "{}", StandardCharsets.UTF_8);
        assertRejected(
                resolver(layout.root()).resolve(ARTIFACT_KEY, MANIFEST_KEY),
                FindingCode.ARTIFACT_MANIFEST_INVALID
        );

        byte[] malformedUtf8 = validJson.getBytes(StandardCharsets.UTF_8);
        malformedUtf8[malformedUtf8.length - 2] = (byte) 0xFF;
        Files.write(manifestPath, malformedUtf8);
        assertRejected(
                resolver(layout.root()).resolve(ARTIFACT_KEY, MANIFEST_KEY),
                FindingCode.ARTIFACT_MANIFEST_INVALID
        );
        assertTrue(objectMapper().getFactory().streamReadConstraints().getMaxNestingDepth() <= 1000);
    }

    @Test
    void shouldRejectCrossReleaseAndMixedLocatorsEvenWhenArtifactDigestMatches() throws Exception {
        Layout releaseA = createLayout(tempDir.resolve("root"));
        String artifactKeyB = "artifact_release_02";
        String manifestKeyB = "manifest_release_02.json";
        byte[] sharedContent = Files.readAllBytes(releaseA.artifactRoot().resolve("artifact.json"));
        StrategyArtifactManifest manifestB = writeLayout(
                releaseA.root(),
                artifactKeyB,
                manifestKeyB,
                "sv-other",
                UUID.fromString("33333333-3333-4333-8333-333333333333"),
                "eval-other",
                sharedContent
        );
        assertEquals(releaseA.manifest().artifactDigest(), manifestB.artifactDigest());

        StrategyRelease locatorB = releaseService(
                facts(artifactKeyB, manifestKeyB),
                releaseA.root()
        ).verify("publish-gatex-4c");
        assertEquals(StrategyReleaseStatus.REJECTED, locatorB.releaseStatus());
        assertEquals(
                FindingCode.ARTIFACT_RELEASE_IDENTITY_MISMATCH,
                locatorB.verificationResult().reasonCode()
        );

        StrategyRelease mixed = releaseService(
                facts(ARTIFACT_KEY, manifestKeyB),
                releaseA.root()
        ).verify("publish-gatex-4c");
        assertEquals(StrategyReleaseStatus.REJECTED, mixed.releaseStatus());
        assertEquals(
                FindingCode.ARTIFACT_RELEASE_IDENTITY_MISMATCH,
                mixed.verificationResult().reasonCode()
        );
    }

    @Test
    void shouldRejectSymlinkEscapeWhenPlatformAllowsCreation() throws Exception {
        Layout layout = createLayout(tempDir.resolve("root"));
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        Files.delete(layout.artifactRoot().resolve("artifact.json"));
        Files.delete(layout.artifactRoot());
        try {
            Files.createSymbolicLink(layout.artifactRoot(), outside);
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            assumeTrue(false, "SYMLINK_PRIVILEGE_UNAVAILABLE");
        }

        assertRejected(
                resolver(layout.root()).resolve(ARTIFACT_KEY, MANIFEST_KEY),
                FindingCode.ARTIFACT_LOCATION_UNSAFE
        );
    }

    @Test
    void shouldRejectWindowsJunctionEscapeWhenConstructable() throws Exception {
        assumeTrue(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows"), "WINDOWS_ONLY");
        Layout layout = createLayout(tempDir.resolve("root"));
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        Files.delete(layout.artifactRoot().resolve("artifact.json"));
        Files.delete(layout.artifactRoot());

        Process process = new ProcessBuilder(
                "cmd.exe",
                "/c",
                "mklink",
                "/J",
                layout.artifactRoot().toString(),
                outside.toString()
        ).redirectErrorStream(true).start();
        process.getInputStream().readAllBytes();
        assumeTrue(process.waitFor() == 0, "JUNCTION_CREATION_UNAVAILABLE");

        assertRejected(
                resolver(layout.root()).resolve(ARTIFACT_KEY, MANIFEST_KEY),
                FindingCode.ARTIFACT_LOCATION_UNSAFE
        );
    }

    @Test
    void shouldRejectRootReplacementAfterManifestRead() throws Exception {
        Layout layout = createLayout(tempDir.resolve("root"));
        Path replaced = tempDir.resolve("root-original");
        ServerControlledStrategyArtifactBindingResolver resolver =
                new ServerControlledStrategyArtifactBindingResolver(
                        layout.root().toString(),
                        objectMapper(),
                        () -> {
                            try {
                                Files.move(layout.root(), replaced);
                                Files.createDirectory(layout.root());
                            } catch (IOException exception) {
                                throw new IllegalStateException("test root replacement failed", exception);
                            }
                        }
                );

        assertRejected(
                resolver.resolve(ARTIFACT_KEY, MANIFEST_KEY),
                FindingCode.ARTIFACT_LOCATION_UNSAFE
        );
    }

    @Test
    void shouldRejectArtifactTargetReplacementAfterManifestRead() throws Exception {
        Layout layout = createLayout(tempDir.resolve("root"));
        Path replaced = tempDir.resolve("artifact-original");
        ServerControlledStrategyArtifactBindingResolver resolver =
                new ServerControlledStrategyArtifactBindingResolver(
                        layout.root().toString(),
                        objectMapper(),
                        () -> {
                            try {
                                Files.move(layout.artifactRoot(), replaced);
                                Path replacement = Files.createDirectory(layout.artifactRoot());
                                Files.writeString(
                                        replacement.resolve("replacement.json"),
                                        "{\"replacement\":true}",
                                        StandardCharsets.UTF_8
                                );
                            } catch (IOException exception) {
                                throw new IllegalStateException("test artifact replacement failed", exception);
                            }
                        }
                );

        assertRejected(
                resolver.resolve(ARTIFACT_KEY, MANIFEST_KEY),
                FindingCode.ARTIFACT_LOCATION_UNSAFE
        );
    }

    private Layout createLayout(Path root) throws Exception {
        Files.createDirectory(root);
        byte[] content = "{\"score\":1}".getBytes(StandardCharsets.UTF_8);
        StrategyArtifactManifest manifest = writeLayout(
                root,
                ARTIFACT_KEY,
                MANIFEST_KEY,
                "sv-gatex-4c",
                UUID.fromString("22222222-2222-4222-8222-222222222222"),
                "eval-gatex-4c",
                content
        );
        return new Layout(root, root.resolve(ARTIFACT_KEY), manifest);
    }

    private StrategyArtifactManifest writeLayout(
            Path root,
            String artifactKey,
            String manifestKey,
            String strategyVersionId,
            UUID datasetId,
            String evaluationId,
            byte[] content
    ) throws Exception {
        Path artifactRoot = Files.createDirectory(root.resolve(artifactKey));
        Files.write(artifactRoot.resolve("artifact.json"), content);
        List<ArtifactFile> artifacts = List.of(new ArtifactFile(
                "evaluation-artifact",
                "artifact.json",
                sha256(content),
                content.length,
                "application/json"
        ));
        StrategyArtifactManifest manifest = new StrategyArtifactManifest(
                StrategyArtifactManifest.SUPPORTED_SCHEMA_VERSION,
                strategyVersionId,
                datasetId,
                evaluationId,
                artifacts,
                TrustedRootStrategyArtifactVerifier.computeArtifactDigest(artifacts),
                Instant.parse("2026-08-10T00:00:00Z"),
                "nq-research/1.0"
        );
        Files.write(root.resolve(manifestKey), objectMapper().writeValueAsBytes(manifest));
        return manifest;
    }

    private StrategyReleaseProductionService releaseService(
            StrategyReleaseProvenanceFacts facts,
            Path root
    ) {
        return new StrategyReleaseProductionService(
                ignored -> facts,
                resolver(root),
                new TrustedRootStrategyArtifactVerifier(
                        new StrategyArtifactVerificationPolicy(8, 1024 * 1024, 4 * 1024 * 1024)
                )
        );
    }

    private StrategyReleaseProvenanceFacts facts(String artifactKey, String manifestKey) {
        return new StrategyReleaseProvenanceFacts(
                true,
                "publish-gatex-4c",
                "run-gatex-4c",
                "sv-gatex-4c",
                "sv-gatex-4c",
                UUID.fromString("22222222-2222-4222-8222-222222222222"),
                "eval-gatex-4c",
                "run-gatex-4c",
                "SUCCEEDED",
                "SUCCEEDED",
                true,
                true,
                Instant.parse("2026-08-10T00:00:00Z"),
                Instant.parse("2026-08-10T00:01:00Z"),
                artifactKey,
                manifestKey
        );
    }

    private ServerControlledStrategyArtifactBindingResolver resolver(Path root) {
        return resolver(root == null ? null : root.toString());
    }

    private ServerControlledStrategyArtifactBindingResolver resolver(String root) {
        return new ServerControlledStrategyArtifactBindingResolver(root, objectMapper());
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private void assertRejected(ArtifactBindingResolution result, FindingCode code) {
        assertFalse(result.resolved());
        assertEquals(code, result.reasonCode());
        assertFalse(result.safeStorageIdentifier() != null
                && result.safeStorageIdentifier().contains(tempDir.toString()));
    }

    private String sha256(byte[] value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(value)
            );
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private record Layout(Path root, Path artifactRoot, StrategyArtifactManifest manifest) {
    }
}
