package com.guidinglight.nexusquant.app.livecontrol.executionworker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisposableWorkerReleaseVerifierTest {

    private static final Instant NOW = Instant.parse("2026-08-13T12:00:00Z");

    @TempDir
    Path temporaryDirectory;

    @Test
    void acceptsOnlyExactlyBoundFreshImmutableRelease() throws Exception {
        Path artifact = writeArtifact("verified worker artifact");
        String artifactDigest = digest(artifact);
        Path manifest = writeManifest("release-a", "worker-a", artifactDigest,
                NOW.minus(1, ChronoUnit.HOURS), true);

        DisposableWorkerReleaseVerifier.VerifiedRelease verified = verify(
                manifest, artifact, "release-a", "worker-a", digest(manifest));

        assertEquals("release-a", verified.releaseId());
        assertEquals("worker-a", verified.workerIdentity());
        assertEquals(artifactDigest, verified.artifactDigest());
        assertEquals(digest(manifest), verified.manifestDigest());
        assertFalse(verified.manifestDigest().isBlank());
    }

    @Test
    void deniesTamperedWrongWritableStaleAndMismatchedInputs() throws Exception {
        Path artifact = writeArtifact("verified worker artifact");
        String artifactDigest = digest(artifact);
        Path manifest = writeManifest("release-a", "worker-a", artifactDigest,
                NOW.minus(1, ChronoUnit.HOURS), true);
        String originalDigest = digest(manifest);
        makeWritable(manifest);
        Files.writeString(manifest, Files.readString(manifest) + "extra=tampered\n", StandardCharsets.UTF_8);
        assertDenied("WRITABLE_RELEASE_DENIED",
                () -> verify(manifest, artifact, "release-a", "worker-a", originalDigest));

        Path stable = writeManifest("release-a", "worker-a", artifactDigest,
                NOW.minus(1, ChronoUnit.HOURS), true);
        assertDenied("RELEASE_OR_WORKER_IDENTITY_MISMATCH",
                () -> verify(stable, artifact, "release-b", "worker-a", digest(stable)));
        assertDenied("RELEASE_OR_WORKER_IDENTITY_MISMATCH",
                () -> verify(stable, artifact, "release-a", "worker-b", digest(stable)));
        Path substitutedArtifact = writeArtifact("substituted worker artifact");
        assertDenied("ARTIFACT_DIGEST_INVALID",
                () -> verify(stable, substitutedArtifact, "release-a", "worker-a", digest(stable)));

        Path writable = writeManifest("release-a", "worker-a", artifactDigest,
                NOW.minus(1, ChronoUnit.HOURS), false);
        assertDenied("WRITABLE_RELEASE_DENIED",
                () -> verify(writable, artifact, "release-a", "worker-a", digest(writable)));

        Path stale = writeManifest("release-a", "worker-a", artifactDigest,
                NOW.minus(25, ChronoUnit.HOURS), true);
        assertDenied("STALE_RELEASE_DENIED",
                () -> verify(stale, artifact, "release-a", "worker-a", digest(stale)));
    }

    private DisposableWorkerReleaseVerifier.VerifiedRelease verify(
            Path manifest,
            Path artifact,
            String release,
            String worker,
            String manifestDigest
    ) throws Exception {
        return new DisposableWorkerReleaseVerifier().verify(
                manifest, artifact, release, worker, manifestDigest, NOW);
    }

    private Path writeArtifact(String contents) throws Exception {
        Path artifact = temporaryDirectory.resolve(contents.startsWith("substituted")
                ? "nq-app-substituted.jar" : "nq-app-verified.jar");
        Files.writeString(artifact, contents, StandardCharsets.UTF_8);
        return artifact;
    }

    private Path writeManifest(
            String release,
            String worker,
            String artifactDigest,
            Instant createdAt,
            boolean immutable
    ) throws Exception {
        Path manifest = temporaryDirectory.resolve("release-" + UUID.randomUUID() + ".properties");
        Files.writeString(manifest, String.join("\n",
                "releaseId=" + release,
                "workerIdentity=" + worker,
                "artifactDigest=" + artifactDigest,
                "createdAt=" + createdAt,
                "immutable=" + immutable,
                ""), StandardCharsets.UTF_8);
        if (immutable) makeReadOnly(manifest);
        return manifest;
    }

    private static void makeReadOnly(Path path) throws Exception {
        DosFileAttributeView dos = Files.getFileAttributeView(path, DosFileAttributeView.class);
        if (dos != null) {
            dos.setReadOnly(true);
            return;
        }
        PosixFileAttributeView posix = Files.getFileAttributeView(path, PosixFileAttributeView.class);
        var permissions = posix.readAttributes().permissions();
        permissions.remove(PosixFilePermission.OWNER_WRITE);
        permissions.remove(PosixFilePermission.GROUP_WRITE);
        permissions.remove(PosixFilePermission.OTHERS_WRITE);
        posix.setPermissions(permissions);
    }

    private static void makeWritable(Path path) throws Exception {
        DosFileAttributeView dos = Files.getFileAttributeView(path, DosFileAttributeView.class);
        if (dos != null) {
            dos.setReadOnly(false);
            return;
        }
        PosixFileAttributeView posix = Files.getFileAttributeView(path, PosixFileAttributeView.class);
        var permissions = posix.readAttributes().permissions();
        permissions.add(PosixFilePermission.OWNER_WRITE);
        posix.setPermissions(permissions);
    }

    private static String digest(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static void assertDenied(String reason, ThrowingOperation operation) {
        IllegalStateException failure = assertThrows(IllegalStateException.class, operation::run);
        assertTrue(failure.getMessage().endsWith(reason), failure::getMessage);
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run() throws Exception;
    }
}
