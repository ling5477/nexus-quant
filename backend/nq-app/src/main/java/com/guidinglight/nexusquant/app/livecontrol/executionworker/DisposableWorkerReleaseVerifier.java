package com.guidinglight.nexusquant.app.livecontrol.executionworker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.io.InputStream;

/**
 * Local disposable release manifest verifier；不授予 production worker 启动。
 */
final class DisposableWorkerReleaseVerifier {

    private static final Duration MAXIMUM_AGE = Duration.ofHours(24);

    VerifiedRelease verify(
            Path manifest,
            Path actualArtifact,
            String expectedRelease,
            String expectedWorker,
            String expectedManifestDigest,
            Instant now
    ) throws IOException {
        Path value = manifest.toAbsolutePath().normalize();
        if (!Files.isRegularFile(value, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(value)
                || Files.size(value) > 8192) {
            throw denied("RELEASE_MANIFEST_NOT_STABLE");
        }
        requireReadOnly(value);
        byte[] manifestBytes = Files.readAllBytes(value);
        String manifestDigest = sha256(manifestBytes);
        if (!constantTimeDigestEquals(expectedManifestDigest, manifestDigest)) {
            throw denied("RELEASE_MANIFEST_DIGEST_MISMATCH");
        }
        Map<String, String> fields = fields(new String(manifestBytes, StandardCharsets.UTF_8).lines().toList());
        String release = field(fields, "releaseId");
        String worker = field(fields, "workerIdentity");
        String artifactDigest = field(fields, "artifactDigest");
        Instant createdAt = Instant.parse(field(fields, "createdAt"));
        boolean immutable = Boolean.parseBoolean(field(fields, "immutable"));
        if (!expectedRelease.equals(release) || !expectedWorker.equals(worker)) {
            throw denied("RELEASE_OR_WORKER_IDENTITY_MISMATCH");
        }
        if (!immutable) throw denied("WRITABLE_RELEASE_DENIED");
        if (!artifactDigest.matches("[0-9a-f]{64}")
                || !constantTimeDigestEquals(artifactDigest, digestStableArtifact(actualArtifact))) {
            throw denied("ARTIFACT_DIGEST_INVALID");
        }
        if (createdAt.isAfter(now) || Duration.between(createdAt, now).compareTo(MAXIMUM_AGE) > 0) {
            throw denied("STALE_RELEASE_DENIED");
        }
        return new VerifiedRelease(release, worker, artifactDigest, manifestDigest, createdAt);
    }

    private static Map<String, String> fields(List<String> lines) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : lines) {
            int separator = line.indexOf('=');
            if (separator < 1 || values.putIfAbsent(line.substring(0, separator),
                    line.substring(separator + 1).trim()) != null) {
                throw denied("RELEASE_MANIFEST_MALFORMED");
            }
        }
        if (!values.keySet().equals(java.util.Set.of(
                "releaseId", "workerIdentity", "artifactDigest", "createdAt", "immutable"))) {
            throw denied("RELEASE_MANIFEST_MALFORMED");
        }
        return values;
    }

    private static String field(Map<String, String> fields, String name) {
        String value = fields.get(name);
        if (value == null || value.isBlank() || value.length() > 256) {
            throw denied("RELEASE_MANIFEST_FIELD_MISSING");
        }
        return value;
    }

    private static String digestStableArtifact(Path supplied) throws IOException {
        Path artifact = supplied.toAbsolutePath().normalize();
        BasicFileAttributes before = Files.readAttributes(
                artifact, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!before.isRegularFile() || Files.isSymbolicLink(artifact) || before.size() <= 0
                || before.size() > 64L * 1024 * 1024) {
            throw denied("ACTUAL_ARTIFACT_NOT_STABLE");
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
        try (InputStream input = Files.newInputStream(
                artifact, java.nio.file.StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer = new byte[64 * 1024];
            for (int count; (count = input.read(buffer)) >= 0; ) {
                if (count > 0) digest.update(buffer, 0, count);
            }
        }
        BasicFileAttributes after = Files.readAttributes(
                artifact, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!after.isRegularFile() || before.size() != after.size()
                || !before.lastModifiedTime().equals(after.lastModifiedTime())
                || (before.fileKey() != null && !before.fileKey().equals(after.fileKey()))) {
            throw denied("ACTUAL_ARTIFACT_CHANGED_DURING_VERIFICATION");
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void requireReadOnly(Path manifest) throws IOException {
        DosFileAttributeView dos = Files.getFileAttributeView(
                manifest, DosFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (dos != null) {
            if (!dos.readAttributes().isReadOnly()) throw denied("WRITABLE_RELEASE_DENIED");
            return;
        }
        PosixFileAttributeView posix = Files.getFileAttributeView(
                manifest, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (posix == null) throw denied("RELEASE_IMMUTABILITY_UNSUPPORTED");
        var permissions = posix.readAttributes().permissions();
        if (permissions.contains(PosixFilePermission.OWNER_WRITE)
                || permissions.contains(PosixFilePermission.GROUP_WRITE)
                || permissions.contains(PosixFilePermission.OTHERS_WRITE)) {
            throw denied("WRITABLE_RELEASE_DENIED");
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static boolean constantTimeDigestEquals(String expected, String actual) {
        if (expected == null || !expected.matches("[0-9a-f]{64}")) {
            throw denied("EXPECTED_DIGEST_INVALID");
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII));
    }

    private static IllegalStateException denied(String reason) {
        return new IllegalStateException("DISPOSABLE_RELEASE_DENIED:" + reason);
    }

    record VerifiedRelease(
            String releaseId,
            String workerIdentity,
            String artifactDigest,
            String manifestDigest,
            Instant createdAt
    ) {
    }
}
