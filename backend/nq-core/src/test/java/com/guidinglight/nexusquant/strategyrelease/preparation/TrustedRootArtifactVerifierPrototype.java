package com.guidinglight.nexusquant.strategyrelease.preparation;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * PRE-GATEX trusted-root artifact verification 安全原型。
 *
 * <p>该实现只存在于测试源码，不是 production verifier，也不产生策略批准、Shadow 启动、LIVE readiness
 * 或交易授权。双重属性检查只能降低常见 TOCTOU 风险，不能替代稳定文件描述符级防护。
 */
final class TrustedRootArtifactVerifierPrototype {

    private static final int BUFFER_SIZE = 8192;
    private static final Pattern SHA_256 = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern DRIVE_PREFIX = Pattern.compile("^[A-Za-z]:.*");
    private static final Pattern SAFE_PATH_SEGMENT = Pattern.compile("^[A-Za-z0-9._-]+$");
    private static final VerificationHook NOOP_HOOK = ignored -> {
    };

    private final VerificationHook afterPreReadAttributes;

    TrustedRootArtifactVerifierPrototype() {
        this(NOOP_HOOK);
    }

    /**
     * 仅供测试在 pre-read attributes 与文件打开之间制造确定性变化，不得移入 production source。
     */
    TrustedRootArtifactVerifierPrototype(VerificationHook afterPreReadAttributes) {
        this.afterPreReadAttributes = Objects.requireNonNull(afterPreReadAttributes, "hook");
    }

    VerificationResult verify(VerificationRequest request) {
        if (request == null) {
            return rejected(
                    "",
                    "",
                    FindingCode.TRUSTED_ROOT_INVALID,
                    "verification request is required"
            );
        }

        String logicalName = safeDisplay(request.logicalName(), "<invalid-logical-name>");
        String relativePath = safeRelativePathDisplay(request.relativePath());
        if (hasUnsafeMetadataValue(request.logicalName())) {
            return rejected(
                    logicalName,
                    relativePath,
                    FindingCode.CANONICALIZATION_FAILED,
                    "logical name is invalid"
            );
        }

        FindingCode pathFinding = validateRelativePath(request.relativePath());
        if (pathFinding != null) {
            return rejected(logicalName, relativePath, pathFinding, "relative path is invalid");
        }
        if (hasInvalidSha256(request.expectedSha256())) {
            return rejected(
                    logicalName,
                    relativePath,
                    FindingCode.DIGEST_MISMATCH,
                    "expected digest must be lowercase SHA-256"
            );
        }
        if (request.expectedSizeBytes() <= 0) {
            return rejected(logicalName, relativePath, FindingCode.SIZE_MISMATCH, "expected size is invalid");
        }
        if (request.maxAllowedBytes() <= 0) {
            return rejected(
                    logicalName,
                    relativePath,
                    FindingCode.ARTIFACT_TOO_LARGE,
                    "maximum allowed bytes is invalid"
            );
        }

        Path trustedRoot = request.trustedRoot();
        if (trustedRoot == null) {
            return rejected(
                    logicalName,
                    relativePath,
                    FindingCode.TRUSTED_ROOT_INVALID,
                    "trusted root is invalid"
            );
        }

        try {
            if (Files.isSymbolicLink(trustedRoot)) {
                return rejected(
                        logicalName,
                        relativePath,
                        FindingCode.SYMLINK_NOT_ALLOWED,
                        "symbolic links are not allowed"
                );
            }

            Path root = trustedRoot.toAbsolutePath().normalize();
            BasicFileAttributes rootAttributes;
            try {
                rootAttributes = readAttributes(root);
            } catch (NoSuchFileException exception) {
                return rejected(
                        logicalName,
                        relativePath,
                        FindingCode.TRUSTED_ROOT_INVALID,
                        "trusted root is invalid"
                );
            }
            if (rootAttributes.isSymbolicLink() || Files.isSymbolicLink(root)) {
                return rejected(
                        logicalName,
                        relativePath,
                        FindingCode.SYMLINK_NOT_ALLOWED,
                        "symbolic links are not allowed"
                );
            }
            if (rootAttributes.isOther()) {
                return rejected(
                        logicalName,
                        relativePath,
                        FindingCode.SPECIAL_FILE_NOT_ALLOWED,
                        "special file types are not allowed"
                );
            }
            if (!rootAttributes.isDirectory()) {
                return rejected(
                        logicalName,
                        relativePath,
                        FindingCode.TRUSTED_ROOT_INVALID,
                        "trusted root must be a directory"
                );
            }

            Path rootRealPath = root.toRealPath();
            Path target = root.resolve(request.relativePath()).normalize();
            if (!target.startsWith(root)) {
                return rejected(
                        logicalName,
                        relativePath,
                        FindingCode.PATH_ESCAPES_TRUSTED_ROOT,
                        "relative path escapes trusted root"
                );
            }

            VerificationResult componentFailure = inspectPathComponents(root, request.relativePath(), request);
            if (componentFailure != null) {
                return componentFailure;
            }

            Path targetRealPath = target.toRealPath();
            if (!targetRealPath.startsWith(rootRealPath)) {
                return rejected(
                        logicalName,
                        relativePath,
                        FindingCode.PATH_ESCAPES_TRUSTED_ROOT,
                        "resolved path escapes trusted root"
                );
            }

            BasicFileAttributes preReadAttributes = readAttributes(targetRealPath);
            VerificationResult typeFailure = rejectUnsafeTargetType(preReadAttributes, targetRealPath, request);
            if (typeFailure != null) {
                return typeFailure;
            }
            if (preReadAttributes.size() > request.maxAllowedBytes()) {
                return rejected(
                        logicalName,
                        relativePath,
                        FindingCode.ARTIFACT_TOO_LARGE,
                        "artifact exceeds maximum allowed bytes"
                );
            }

            afterPreReadAttributes.run(targetRealPath);
            DigestRead digestRead = streamDigest(targetRealPath, request.maxAllowedBytes());

            BasicFileAttributes postReadAttributes;
            try {
                postReadAttributes = readAttributes(targetRealPath);
            } catch (NoSuchFileException exception) {
                return rejected(
                        logicalName,
                        relativePath,
                        FindingCode.ARTIFACT_CHANGED_DURING_VERIFICATION,
                        "artifact changed during verification"
                );
            }
            if (!sameIdentity(preReadAttributes, postReadAttributes)) {
                return rejected(
                        logicalName,
                        relativePath,
                        FindingCode.ARTIFACT_CHANGED_DURING_VERIFICATION,
                        "artifact changed during verification"
                );
            }
            if (digestRead.sizeBytes() != request.expectedSizeBytes()) {
                return rejected(logicalName, relativePath, FindingCode.SIZE_MISMATCH, "artifact size does not match");
            }
            if (!constantTimeEquals(request.expectedSha256(), digestRead.sha256())) {
                return rejected(
                        logicalName,
                        relativePath,
                        FindingCode.DIGEST_MISMATCH,
                        "artifact digest does not match"
                );
            }

            return new VerificationResult(
                    Status.VERIFIED,
                    null,
                    logicalName,
                    relativePath,
                    digestRead.sha256(),
                    digestRead.sizeBytes(),
                    safePathIdentity(preReadAttributes.fileKey()),
                    "artifact integrity verified; this is not trading authorization"
            );
        } catch (NoSuchFileException exception) {
            return rejected(
                    logicalName,
                    relativePath,
                    FindingCode.ARTIFACT_NOT_FOUND,
                    "artifact or trusted root was not found"
            );
        } catch (UnsupportedOperationException exception) {
            return new VerificationResult(
                    Status.UNKNOWN,
                    FindingCode.PLATFORM_LINK_GUARANTEE_UNAVAILABLE,
                    logicalName,
                    relativePath,
                    null,
                    null,
                    null,
                    "platform cannot guarantee no-follow file access"
            );
        } catch (FileSystemException ignored) {
            return mapFileSystemFailure(request);
        } catch (IOException | SecurityException exception) {
            return rejected(
                    logicalName,
                    relativePath,
                    FindingCode.VERIFICATION_IO_FAILED,
                    "artifact verification could not be completed safely"
            );
        }
    }

    static AggregateDigestResult computeAggregateDigest(List<ArtifactDescriptor> artifacts) {
        if (artifacts == null || artifacts.isEmpty()) {
            return aggregateRejected("artifact index is empty");
        }

        List<ArtifactDescriptor> sorted = new ArrayList<>(artifacts);
        for (ArtifactDescriptor artifact : sorted) {
            if (artifact == null
                    || hasUnsafeMetadataValue(artifact.logicalName())
                    || validateRelativePath(artifact.relativePath()) != null
                    || hasInvalidSha256(artifact.sha256())
                    || artifact.sizeBytes() <= 0
                    || hasUnsafeMetadataValue(artifact.mediaType())) {
                return aggregateRejected("artifact index cannot be canonicalized safely");
            }
        }

        sorted.sort(Comparator.comparing(ArtifactDescriptor::logicalName)
                .thenComparing(ArtifactDescriptor::relativePath));
        String canonical = sorted.stream()
                .map(artifact -> String.join(
                        "\u001f",
                        artifact.logicalName(),
                        artifact.relativePath(),
                        artifact.sha256(),
                        Long.toString(artifact.sizeBytes()),
                        artifact.mediaType()
                ))
                .collect(Collectors.joining("\n"));
        try {
            return new AggregateDigestResult(Status.VERIFIED, null, sha256Utf8(canonical), "aggregate digest computed");
        } catch (IllegalStateException exception) {
            return new AggregateDigestResult(
                    Status.UNKNOWN,
                    FindingCode.CANONICALIZATION_FAILED,
                    null,
                    "SHA-256 is unavailable"
            );
        }
    }

    private VerificationResult inspectPathComponents(
            Path root,
            String relativePath,
            VerificationRequest request
    ) throws IOException {
        Path current = root;
        String[] segments = relativePath.split("/", -1);
        for (int index = 0; index < segments.length; index++) {
            current = current.resolve(segments[index]);
            BasicFileAttributes attributes = readAttributes(current);
            if (attributes.isSymbolicLink() || Files.isSymbolicLink(current)) {
                return rejected(
                        request,
                        FindingCode.SYMLINK_NOT_ALLOWED,
                        "symbolic links are not allowed"
                );
            }
            if (attributes.isOther()) {
                return rejected(
                        request,
                        FindingCode.SPECIAL_FILE_NOT_ALLOWED,
                        "special file types are not allowed"
                );
            }
            if (index < segments.length - 1 && !attributes.isDirectory()) {
                return rejected(
                        request,
                        FindingCode.ARTIFACT_NOT_FOUND,
                        "artifact path is not traversable"
                );
            }
        }
        return null;
    }

    private VerificationResult rejectUnsafeTargetType(
            BasicFileAttributes attributes,
            Path target,
            VerificationRequest request
    ) {
        if (attributes.isSymbolicLink() || Files.isSymbolicLink(target)) {
            return rejected(request, FindingCode.SYMLINK_NOT_ALLOWED, "symbolic links are not allowed");
        }
        if (attributes.isOther()) {
            return rejected(request, FindingCode.SPECIAL_FILE_NOT_ALLOWED, "special file types are not allowed");
        }
        if (!attributes.isRegularFile()) {
            return rejected(
                    request,
                    FindingCode.ARTIFACT_NOT_REGULAR_FILE,
                    "artifact must be a regular file"
            );
        }
        return null;
    }

    private DigestRead streamDigest(Path target, long maxAllowedBytes) throws IOException {
        MessageDigest digest = newSha256();
        long sizeBytes = 0;
        Set<OpenOption> options = Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
        try (SeekableByteChannel channel = Files.newByteChannel(target, options)) {
            ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
            int read;
            while (sizeBytes < maxAllowedBytes) {
                buffer.clear();
                buffer.limit((int) Math.min(buffer.capacity(), maxAllowedBytes - sizeBytes));
                read = channel.read(buffer);
                if (read == -1) {
                    break;
                }
                if (read == 0) {
                    continue;
                }
                sizeBytes += read;
                buffer.flip();
                digest.update(buffer);
            }
        }
        return new DigestRead(HexFormat.of().formatHex(digest.digest()), sizeBytes);
    }

    private VerificationResult mapFileSystemFailure(VerificationRequest request) {
        Path target = null;
        try {
            if (request.trustedRoot() != null && validateRelativePath(request.relativePath()) == null) {
                target = request.trustedRoot().toAbsolutePath().normalize()
                        .resolve(request.relativePath())
                        .normalize();
            }
        } catch (InvalidPathException ignored) {
            // 输入路径已由固定 finding 表达；不得回传原始异常。
        }
        if (target != null && Files.isSymbolicLink(target)) {
            return rejected(request, FindingCode.SYMLINK_NOT_ALLOWED, "symbolic links are not allowed");
        }
        return rejected(
                request,
                FindingCode.VERIFICATION_IO_FAILED,
                "artifact verification could not be completed safely"
        );
    }

    private static BasicFileAttributes readAttributes(Path path) throws IOException {
        return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }

    private static boolean sameIdentity(BasicFileAttributes before, BasicFileAttributes after) {
        return before.isRegularFile() == after.isRegularFile()
                && before.size() == after.size()
                && before.lastModifiedTime().equals(after.lastModifiedTime())
                && Objects.equals(before.fileKey(), after.fileKey());
    }

    private static FindingCode validateRelativePath(String value) {
        if (value == null || value.isBlank() || containsControl(value)) {
            return FindingCode.INVALID_RELATIVE_PATH;
        }
        if (value.startsWith("/")
                || value.startsWith("\\")
                || value.contains("\\")
                || DRIVE_PREFIX.matcher(value).matches()
                || value.contains("//")) {
            return FindingCode.INVALID_RELATIVE_PATH;
        }

        String[] segments = value.split("/", -1);
        for (String segment : segments) {
            if ("..".equals(segment)) {
                return FindingCode.PATH_ESCAPES_TRUSTED_ROOT;
            }
            if (segment.isBlank() || ".".equals(segment) || !SAFE_PATH_SEGMENT.matcher(segment).matches()) {
                return FindingCode.INVALID_RELATIVE_PATH;
            }
        }
        try {
            if (Path.of(value).isAbsolute()) {
                return FindingCode.INVALID_RELATIVE_PATH;
            }
        } catch (InvalidPathException exception) {
            return FindingCode.INVALID_RELATIVE_PATH;
        }
        return null;
    }

    private static boolean hasUnsafeMetadataValue(String value) {
        return value == null
                || value.isBlank()
                || containsControl(value)
                || value.indexOf('\u001f') >= 0;
    }

    private static boolean containsControl(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }

    private static boolean hasInvalidSha256(String value) {
        return value == null || !SHA_256.matcher(value).matches();
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII)
        );
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String sha256Utf8(String value) {
        MessageDigest digest = newSha256();
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static String safePathIdentity(Object fileKey) {
        return fileKey == null ? "file-key-unavailable" : "file-key-sha256:" + sha256Utf8(fileKey.toString());
    }

    private static String safeRelativePathDisplay(String value) {
        if (value == null
                || value.isBlank()
                || containsControl(value)
                || value.startsWith("/")
                || value.startsWith("\\")
                || value.contains("\\")
                || DRIVE_PREFIX.matcher(value).matches()) {
            return "<invalid-relative-path>";
        }
        return safeDisplay(value, "<invalid-relative-path>");
    }

    private static String safeDisplay(String value, String fallback) {
        if (value == null || value.isBlank() || containsControl(value)) {
            return fallback;
        }
        return value.length() <= 512 ? value : value.substring(0, 512);
    }

    private static VerificationResult rejected(
            VerificationRequest request,
            FindingCode findingCode,
            String safeReason
    ) {
        return rejected(
                safeDisplay(request.logicalName(), "<invalid-logical-name>"),
                safeRelativePathDisplay(request.relativePath()),
                findingCode,
                safeReason
        );
    }

    private static VerificationResult rejected(
            String logicalName,
            String relativePath,
            FindingCode findingCode,
            String safeReason
    ) {
        return new VerificationResult(
                Status.REJECTED,
                findingCode,
                logicalName,
                relativePath,
                null,
                null,
                null,
                safeReason
        );
    }

    private static AggregateDigestResult aggregateRejected(String safeReason) {
        return new AggregateDigestResult(
                Status.REJECTED,
                FindingCode.CANONICALIZATION_FAILED,
                null,
                safeReason
        );
    }

    enum Status {
        VERIFIED,
        REJECTED,
        UNKNOWN
    }

    enum FindingCode {
        INVALID_RELATIVE_PATH,
        PATH_ESCAPES_TRUSTED_ROOT,
        TRUSTED_ROOT_INVALID,
        SYMLINK_NOT_ALLOWED,
        SPECIAL_FILE_NOT_ALLOWED,
        ARTIFACT_NOT_FOUND,
        ARTIFACT_NOT_REGULAR_FILE,
        ARTIFACT_TOO_LARGE,
        SIZE_MISMATCH,
        DIGEST_MISMATCH,
        ARTIFACT_CHANGED_DURING_VERIFICATION,
        PLATFORM_LINK_GUARANTEE_UNAVAILABLE,
        CANONICALIZATION_FAILED,
        VERIFICATION_IO_FAILED
    }

    record VerificationRequest(
            Path trustedRoot,
            String logicalName,
            String relativePath,
            String expectedSha256,
            long expectedSizeBytes,
            long maxAllowedBytes
    ) {
    }

    record VerificationResult(
            Status status,
            FindingCode findingCode,
            String logicalName,
            String relativePath,
            String actualSha256,
            Long actualSizeBytes,
            String verifiedPathIdentity,
            String safeReason
    ) {
    }

    record ArtifactDescriptor(
            String logicalName,
            String relativePath,
            String sha256,
            long sizeBytes,
            String mediaType
    ) {
    }

    record AggregateDigestResult(
            Status status,
            FindingCode findingCode,
            String digest,
            String safeReason
    ) {
    }

    private record DigestRead(String sha256, long sizeBytes) {
    }

    @FunctionalInterface
    interface VerificationHook {
        void run(Path target) throws IOException;
    }
}
