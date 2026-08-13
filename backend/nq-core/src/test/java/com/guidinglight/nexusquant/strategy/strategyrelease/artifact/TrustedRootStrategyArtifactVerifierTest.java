package com.guidinglight.nexusquant.strategy.strategyrelease.artifact;

import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactManifest.ArtifactFile;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactVerificationResult.FindingCode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Trusted-root link/reparse 与 TOCTOU production guard 回归测试。 */
class TrustedRootStrategyArtifactVerifierTest {

    private static final StrategyArtifactVerificationPolicy POLICY =
            new StrategyArtifactVerificationPolicy(8, 1024 * 1024, 4 * 1024 * 1024);

    @TempDir
    Path tempDir;

    @Test
    void shouldRejectSymlinkEscapeWhenPlatformAllowsSymlinkCreation() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("root"));
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        byte[] content = "{\"score\":1}".getBytes(StandardCharsets.UTF_8);
        Files.write(outside.resolve("artifact.json"), content);
        Path link = root.resolve("linked");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            assumeTrue(false, "SYMLINK_PRIVILEGE_UNAVAILABLE");
        }
        StrategyArtifactManifest manifest = manifest("linked/artifact.json", content);

        StrategyArtifactVerificationResult result =
                new TrustedRootStrategyArtifactVerifier(POLICY).verify(root, manifest);

        assertEquals(StrategyArtifactVerificationResult.Status.REJECTED, result.status());
        assertEquals(FindingCode.SYMLINK_OR_REPARSE_NOT_ALLOWED, result.reasonCode());
    }

    @Test
    void shouldRejectArtifactReplacedAfterPreReadAttributes() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("root"));
        byte[] content = "{\"score\":1}".getBytes(StandardCharsets.UTF_8);
        Files.write(root.resolve("artifact.json"), content);
        StrategyArtifactManifest manifest = manifest("artifact.json", content);
        TrustedRootStrategyArtifactVerifier verifier = new TrustedRootStrategyArtifactVerifier(
                POLICY,
                target -> Files.writeString(target, "{\"score\":12345}", StandardCharsets.UTF_8)
        );

        StrategyArtifactVerificationResult result = verifier.verify(root, manifest);

        assertEquals(StrategyArtifactVerificationResult.Status.REJECTED, result.status());
        assertEquals(FindingCode.ARTIFACT_CHANGED_DURING_VERIFICATION, result.reasonCode());
    }

    @Test
    void shouldRejectCaseCollidingManifestPathsAcrossPlatforms() {
        byte[] content = "{\"score\":1}".getBytes(StandardCharsets.UTF_8);
        ArtifactFile lower = new ArtifactFile(
                "lower", "model/data.json", sha256(content), content.length, "application/json");
        ArtifactFile upper = new ArtifactFile(
                "upper", "MODEL/data.json", sha256(content), content.length, "application/json");
        List<ArtifactFile> files = List.of(lower, upper);
        StrategyArtifactManifest manifest = new StrategyArtifactManifest(
                StrategyArtifactManifest.SUPPORTED_SCHEMA_VERSION,
                "sv-case-collision",
                UUID.fromString("22222222-2222-4222-8222-222222222222"),
                "eval-case-collision",
                files,
                TrustedRootStrategyArtifactVerifier.computeArtifactDigest(files),
                Instant.parse("2026-08-01T00:00:00Z"),
                "nq-research/1.0"
        );

        StrategyArtifactVerificationResult result =
                new TrustedRootStrategyArtifactVerifier(POLICY).validateManifest(manifest).orElseThrow();

        assertEquals(FindingCode.CASE_COLLISION, result.reasonCode());
        assertTrue(result.safeRelativeIdentifier().endsWith("data.json"));
    }

    private StrategyArtifactManifest manifest(String relativePath, byte[] content) {
        List<ArtifactFile> files = List.of(new ArtifactFile(
                "evaluation-artifact",
                relativePath,
                sha256(content),
                content.length,
                "application/json"
        ));
        return new StrategyArtifactManifest(
                StrategyArtifactManifest.SUPPORTED_SCHEMA_VERSION,
                "sv-gatex-1",
                UUID.fromString("22222222-2222-4222-8222-222222222222"),
                "eval-gatex-1",
                files,
                TrustedRootStrategyArtifactVerifier.computeArtifactDigest(files),
                Instant.parse("2026-08-01T00:00:00Z"),
                "nq-research/1.0"
        );
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
