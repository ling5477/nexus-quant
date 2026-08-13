package com.guidinglight.nexusquant.strategy.strategyrelease.artifact;

import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactManifest.ArtifactFile;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactVerificationResult.FindingCode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Verified-open stable handle、最终 closure 与竞态回归。 */
class VerifiedOpenStrategyArtifactReaderTest {

    private static final StrategyArtifactVerificationPolicy POLICY =
            new StrategyArtifactVerificationPolicy(8, 1024 * 1024, 4 * 1024 * 1024);
    private static final byte[] CONTENT = "{\"score\":1}".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path tempDir;

    @Test
    void shouldExposeRequiredStableHandlePrerequisitesOnSupportedLinuxFilesystem() throws Exception {
        assumeLinux();
        Path root = Files.createDirectory(tempDir.resolve("runtime-prerequisites"));
        Path artifact = Files.write(root.resolve("artifact.json"), CONTENT);

        BasicFileAttributes attributes = Files.readAttributes(
                artifact, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            assertTrue(stream instanceof SecureDirectoryStream<?>,
                    "authorized Linux runtime requires SecureDirectoryStream");
        }
        assertTrue(attributes.isRegularFile());
        assertNotNull(attributes.fileKey(), "authorized Linux runtime requires stable fileKey");
    }

    @Test
    void shouldReturnSnapshotOnlyAfterClosureAndConsumeItOnce() throws Exception {
        Fixture fixture = fixture("artifact.json");
        ByteArrayOutputStream consumed = new ByteArrayOutputStream();
        StableArtifactSnapshotResult result = reader().verifyAndSnapshot(fixture.root, fixture.manifest);

        if (isLinux()) {
            assertEquals(StableArtifactSnapshotResult.Status.SUPPORTED_RUNTIME_CLOSED, result.status());
            result.consumeVerified((descriptor, input) -> readAll(input, consumed));
            assertArrayEquals(CONTENT, consumed.toByteArray());
            assertThrows(IllegalStateException.class,
                    () -> result.consumeVerified(VerifiedOpenStrategyArtifactReaderTest::drain));
        } else {
            assertEquals(StableArtifactSnapshotResult.Status.REJECTED, result.status());
            assertEquals(FindingCode.PLATFORM_STABLE_HANDLE_UNAVAILABLE, result.reasonCode());
        }
    }

    @Test
    void shouldRejectSamePathDifferentIdentityAfterSetVerification() throws Exception {
        assumeLinux();
        Fixture fixture = fixture("artifact.json");
        VerifiedOpenStrategyArtifactReader reader = new VerifiedOpenStrategyArtifactReader(
                verifier(),
                (root, ignored) -> {
                    Files.move(root.resolve("artifact.json"), root.resolve("original.json"));
                    Files.write(root.resolve("artifact.json"), CONTENT);
                },
                (root, artifact) -> { }
        );

        StableArtifactSnapshotResult result = reader.verifyAndSnapshot(fixture.root, fixture.manifest);

        assertEquals(StableArtifactSnapshotResult.Status.REJECTED, result.status());
        assertEquals(FindingCode.ARTIFACT_CHANGED_DURING_STABLE_READ, result.reasonCode());
    }

    @Test
    void shouldRejectRenameOrParentDirectorySwapAfterSetVerification() throws Exception {
        assumeLinux();
        Fixture fixture = fixture("nested/artifact.json");
        VerifiedOpenStrategyArtifactReader reader = new VerifiedOpenStrategyArtifactReader(
                verifier(),
                (root, ignored) -> {
                    Path nested = root.resolve("nested");
                    Files.move(nested, root.resolve("nested-old"), StandardCopyOption.ATOMIC_MOVE);
                    Files.createDirectory(nested);
                },
                (root, artifact) -> { }
        );

        StableArtifactSnapshotResult result = reader.verifyAndSnapshot(fixture.root, fixture.manifest);

        assertEquals(StableArtifactSnapshotResult.Status.REJECTED, result.status());
    }

    @Test
    void shouldRejectFileToDirectoryAndDirectoryToFileSwaps() throws Exception {
        assumeLinux();
        Fixture flat = fixture("artifact.json");
        VerifiedOpenStrategyArtifactReader fileToDirectory = new VerifiedOpenStrategyArtifactReader(
                verifier(),
                (root, ignored) -> {
                    Files.move(root.resolve("artifact.json"), root.resolve("artifact-old.json"));
                    Files.createDirectory(root.resolve("artifact.json"));
                },
                (root, artifact) -> { }
        );
        assertEquals(StableArtifactSnapshotResult.Status.REJECTED,
                fileToDirectory.verifyAndSnapshot(flat.root, flat.manifest).status());

        Fixture nested = fixture("nested/artifact.json");
        VerifiedOpenStrategyArtifactReader directoryToFile = new VerifiedOpenStrategyArtifactReader(
                verifier(),
                (root, ignored) -> {
                    Files.move(root.resolve("nested"), root.resolve("nested-old"));
                    Files.writeString(root.resolve("nested"), "not-a-directory", StandardCharsets.UTF_8);
                },
                (root, artifact) -> { }
        );
        assertEquals(StableArtifactSnapshotResult.Status.REJECTED,
                directoryToFile.verifyAndSnapshot(nested.root, nested.manifest).status());
    }

    @Test
    void shouldRejectSymlinkSwapAfterSetVerification() throws Exception {
        assumeLinux();
        Fixture fixture = fixture("nested/artifact.json");
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        Files.write(outside.resolve("artifact.json"), CONTENT);
        VerifiedOpenStrategyArtifactReader reader = new VerifiedOpenStrategyArtifactReader(
                verifier(),
                (root, ignored) -> {
                    Files.move(root.resolve("nested"), root.resolve("nested-old"));
                    Files.createSymbolicLink(root.resolve("nested"), outside);
                },
                (root, artifact) -> { }
        );

        StableArtifactSnapshotResult result = reader.verifyAndSnapshot(fixture.root, fixture.manifest);

        assertEquals(StableArtifactSnapshotResult.Status.REJECTED, result.status());
    }

    @Test
    void shouldRejectTruncateBeforeFinalClosureWithoutExposingCallback() throws Exception {
        assumeLinux();
        Fixture fixture = fixture("artifact.json");
        AtomicBoolean externalSideEffect = new AtomicBoolean();
        VerifiedOpenStrategyArtifactReader reader = new VerifiedOpenStrategyArtifactReader(
                verifier(),
                (root, ignored) -> { },
                (root, artifact) -> Files.write(root.resolve(artifact.relativePath()), new byte[0])
        );

        StableArtifactSnapshotResult result = reader.verifyAndSnapshot(fixture.root, fixture.manifest);

        assertEquals(StableArtifactSnapshotResult.Status.REJECTED, result.status());
        assertEquals(FindingCode.ARTIFACT_CHANGED_DURING_STABLE_READ, result.reasonCode());
        assertThrows(IllegalStateException.class,
                () -> result.consumeVerified((descriptor, input) -> externalSideEffect.set(true)));
        assertFalse(externalSideEffect.get(), "callback must be structurally unavailable before final closure");
    }

    @Test
    void shouldEraseAndInvalidateSnapshotsAfterPartialConsumer() throws Exception {
        assumeLinux();
        Fixture fixture = fixture("artifact.json");
        AtomicReference<VerifiedOpenStrategyArtifactReader.VerifiedArtifactInput> captured = new AtomicReference<>();
        StableArtifactSnapshotResult result = reader().verifyAndSnapshot(fixture.root, fixture.manifest);

        assertThrows(IOException.class, () -> result.consumeVerified((descriptor, input) -> {
            captured.set(input);
            input.read(ByteBuffer.allocate(1));
        }));

        assertNotNull(captured.get());
        assertThrows(IllegalStateException.class, () -> captured.get().read(ByteBuffer.allocate(1)));
        assertThrows(IllegalStateException.class,
                () -> result.consumeVerified(VerifiedOpenStrategyArtifactReaderTest::drain));
    }

    @Test
    void shouldEraseAndInvalidateSnapshotsAfterConsumerFailure() throws Exception {
        assumeLinux();
        Fixture fixture = fixture("artifact.json");
        AtomicReference<VerifiedOpenStrategyArtifactReader.VerifiedArtifactInput> captured = new AtomicReference<>();
        StableArtifactSnapshotResult result = reader().verifyAndSnapshot(fixture.root, fixture.manifest);

        assertThrows(IOException.class, () -> result.consumeVerified((descriptor, input) -> {
            captured.set(input);
            throw new IOException("expected test failure");
        }));

        assertThrows(IllegalStateException.class, () -> captured.get().size());
        assertThrows(IllegalStateException.class,
                () -> result.consumeVerified(VerifiedOpenStrategyArtifactReaderTest::drain));
    }

    @Test
    void shouldFailClosedWhenTotalImmutableSnapshotLimitIsExceeded() throws Exception {
        assumeLinux();
        Fixture fixture = fixture("artifact.json");
        VerifiedOpenStrategyArtifactReader bounded =
                new VerifiedOpenStrategyArtifactReader(verifier(), CONTENT.length - 1L);

        StableArtifactSnapshotResult result = bounded.verifyAndSnapshot(fixture.root, fixture.manifest);

        assertEquals(FindingCode.IMMUTABLE_SNAPSHOT_LIMIT_EXCEEDED, result.reasonCode());
    }

    @Test
    void shouldRejectTraversalManifestAndDigestMismatchBeforeSnapshotExposure() throws Exception {
        Path root = Files.createDirectory(tempDir.resolve("invalid-manifest-root"));
        Files.write(root.resolve("artifact.json"), CONTENT);
        ArtifactFile traversal = new ArtifactFile(
                "traversal", "../artifact.json", sha256(CONTENT), CONTENT.length, "application/json");
        StrategyArtifactManifest traversalManifest = manifest(List.of(traversal),
                TrustedRootStrategyArtifactVerifier.computeArtifactDigest(List.of(traversal)));
        assertEquals(FindingCode.PATH_ESCAPES_TRUSTED_ROOT,
                reader().verifyAndSnapshot(root, traversalManifest).reasonCode());

        ArtifactFile wrongDigest = new ArtifactFile(
                "evaluation-artifact", "artifact.json", "0".repeat(64), CONTENT.length, "application/json");
        StrategyArtifactManifest digestManifest = manifest(List.of(wrongDigest),
                TrustedRootStrategyArtifactVerifier.computeArtifactDigest(List.of(wrongDigest)));
        assertEquals(FindingCode.DIGEST_MISMATCH,
                reader().verifyAndSnapshot(root, digestManifest).reasonCode());

        ArtifactFile correct = new ArtifactFile(
                "evaluation-artifact", "artifact.json", sha256(CONTENT), CONTENT.length, "application/json");
        StrategyArtifactManifest aggregateMismatch = manifest(List.of(correct), "f".repeat(64));
        assertEquals(FindingCode.ARTIFACT_DIGEST_MISMATCH,
                reader().verifyAndSnapshot(root, aggregateMismatch).reasonCode());
    }

    private Fixture fixture(String relativePath) throws IOException {
        Path root = Files.createDirectory(tempDir.resolve("root-" + UUID.randomUUID()));
        Path target = root.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.write(target, CONTENT);
        List<ArtifactFile> files = List.of(new ArtifactFile(
                "evaluation-artifact", relativePath, sha256(CONTENT), CONTENT.length, "application/json"));
        StrategyArtifactManifest manifest = manifest(files,
                TrustedRootStrategyArtifactVerifier.computeArtifactDigest(files));
        return new Fixture(root, manifest);
    }

    private static StrategyArtifactManifest manifest(List<ArtifactFile> files, String aggregateDigest) {
        return new StrategyArtifactManifest(
                StrategyArtifactManifest.SUPPORTED_SCHEMA_VERSION,
                "sv-stable-handle",
                UUID.fromString("22222222-2222-4222-8222-222222222222"),
                "eval-stable-handle",
                files,
                aggregateDigest,
                Instant.parse("2026-08-13T00:00:00Z"),
                "nq-research/1.0"
        );
    }

    private VerifiedOpenStrategyArtifactReader reader() {
        return new VerifiedOpenStrategyArtifactReader(verifier());
    }

    private TrustedRootStrategyArtifactVerifier verifier() {
        return new TrustedRootStrategyArtifactVerifier(POLICY);
    }

    private static void readAll(
            VerifiedOpenStrategyArtifactReader.VerifiedArtifactInput input,
            ByteArrayOutputStream output
    ) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (read == 0) continue;
            buffer.flip();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            output.write(bytes);
            buffer.clear();
        }
    }

    private static void drain(
            ArtifactFile ignored,
            VerifiedOpenStrategyArtifactReader.VerifiedArtifactInput input
    ) throws IOException {
        readAll(input, new ByteArrayOutputStream());
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static boolean isLinux() {
        return System.getProperty("os.name", "unknown").toLowerCase(Locale.ROOT).contains("linux");
    }

    private static void assumeLinux() {
        assumeTrue(isLinux(), "SUPPORTED_RUNTIME_LINUX_ONLY");
    }

    private record Fixture(Path root, StrategyArtifactManifest manifest) { }
}
