package com.guidinglight.nexusquant.strategy.strategyrelease.artifact;

import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactManifest.ArtifactFile;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactVerificationResult.FindingCode;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Strategy Release artifact 的 trusted-root production verifier。
 *
 * <p>实现使用 NOFOLLOW_LINKS、逐级组件检查、real-path containment、普通文件校验、流式 SHA-256、
 * 文件集与资源上限校验，以及验证前后 identity/目录快照对比。它能关闭明显的 symbolic link、
 * Java NIO 可识别 junction/reparse 与常见替换竞态，但不声称提供 OS 级原子稳定句柄。
 * VERIFIED 只表示 artifact integrity/provenance 通过，不表示 Shadow、LIVE、交易或部署授权。
 */
@Component
public class TrustedRootStrategyArtifactVerifier {

    private static final int BUFFER_SIZE = 8192;
    private static final int SENSITIVE_SCAN_OVERLAP = 4096;
    private static final Pattern SHA_256 = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern OPAQUE_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$");
    private static final Pattern LOGICAL_NAME = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$");
    private static final Pattern DRIVE_PREFIX = Pattern.compile("^[A-Za-z]:.*");
    private static final Pattern SAFE_PATH_SEGMENT = Pattern.compile("^[A-Za-z0-9._-]+$");
    private static final Pattern MEDIA_TYPE =
            Pattern.compile("^[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+$");
    private static final Pattern SENSITIVE_VALUE = Pattern.compile(
            "(?i)(?:-----BEGIN\\s+(?:RSA\\s+|EC\\s+|OPENSSH\\s+)?PRIVATE\\s+KEY-----|"
                    + "(?:api[_-]?key|secret|passphrase|private[_-]?key|mnemonic|credential(?:[_-]?material)?|"
                    + "access[_-]?token|account[_-]?token|session[_-]?token|authorization|cookie|"
                    + "raw[_-]?private[_-]?(?:request|response))\\s*[\\\"']?\\s*[:=]\\s*"
                    + "(?:[\\\"'][^\\\"'\\r\\n]{4,}[\\\"']|[A-Za-z0-9+/=_-]{8,}))"
    );
    private static final Set<String> TEXT_MEDIA_TYPES = Set.of(
            "application/json",
            "application/x-ndjson",
            "application/yaml",
            "application/x-yaml",
            "text/plain",
            "text/csv"
    );
    private static final Set<String> SENSITIVE_MARKERS = Set.of(
            "apikey",
            "secret",
            "passphrase",
            "privatekey",
            "mnemonic",
            "credentialmaterial",
            "accesstoken",
            "accounttoken",
            "sessiontoken",
            "authorization",
            "cookie",
            "rawprivaterequest",
            "rawprivateresponse"
    );
    private static final VerificationHook NOOP_HOOK = ignored -> {
    };

    private final StrategyArtifactVerificationPolicy policy;
    private final VerificationHook afterPreReadAttributes;

    /**
     * Spring production wiring；上限均可通过安全配置覆盖，默认值为 64 files / 1 GiB per file / 4 GiB total。
     */
    @Autowired
    public TrustedRootStrategyArtifactVerifier(
            @Value("${nq.strategy-release.artifact.max-file-count:64}") int maxFileCount,
            @Value("${nq.strategy-release.artifact.max-file-size-bytes:1073741824}") long maxFileSizeBytes,
            @Value("${nq.strategy-release.artifact.max-total-size-bytes:4294967296}") long maxTotalSizeBytes
    ) {
        this(new StrategyArtifactVerificationPolicy(maxFileCount, maxFileSizeBytes, maxTotalSizeBytes));
    }

    /**
     * 显式 policy 构造器，供 bounded integration/unit 场景使用。
     */
    public TrustedRootStrategyArtifactVerifier(StrategyArtifactVerificationPolicy policy) {
        this(policy, NOOP_HOOK);
    }

    TrustedRootStrategyArtifactVerifier(
            StrategyArtifactVerificationPolicy policy,
            VerificationHook afterPreReadAttributes
    ) {
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.afterPreReadAttributes = Objects.requireNonNull(
                afterPreReadAttributes,
                "afterPreReadAttributes must not be null"
        );
    }

    /**
     * 只校验 manifest contract 与 canonical aggregate digest，不访问文件系统。
     *
     * @return empty 表示 contract 可进入 artifact verification；否则返回单一确定性 rejection
     */
    public Optional<StrategyArtifactVerificationResult> validateManifest(StrategyArtifactManifest manifest) {
        if (manifest == null) {
            return Optional.of(rejected(FindingCode.MANIFEST_REQUIRED, "<manifest>"));
        }
        if (manifest.schemaVersion() == null
                || manifest.strategyVersionId() == null
                || manifest.datasetId() == null
                || manifest.evaluationId() == null
                || manifest.artifactFiles() == null
                || manifest.artifactDigest() == null
                || manifest.generatedAt() == null
                || manifest.generatorVersion() == null) {
            return Optional.of(rejected(FindingCode.MANIFEST_FIELD_MISSING, "<manifest>"));
        }
        if (!StrategyArtifactManifest.SUPPORTED_SCHEMA_VERSION.equals(manifest.schemaVersion())) {
            return Optional.of(rejected(FindingCode.UNSUPPORTED_SCHEMA_VERSION, "<manifest>"));
        }
        if (!hasOpaqueId(manifest.strategyVersionId()) || !hasOpaqueId(manifest.evaluationId())) {
            return Optional.of(rejected(FindingCode.INVALID_IDENTIFIER, "<manifest>"));
        }
        if (!safeMetadata(manifest.generatorVersion()) || manifest.generatorVersion().length() > 128) {
            return Optional.of(rejected(FindingCode.INVALID_ARTIFACT_METADATA, "<manifest>"));
        }
        if (containsSensitiveMarker(manifest.generatorVersion())) {
            return Optional.of(rejected(FindingCode.SENSITIVE_METADATA, "<manifest>"));
        }
        if (!isSha256(manifest.artifactDigest())) {
            return Optional.of(rejected(FindingCode.INVALID_DIGEST, "<manifest>"));
        }
        if (manifest.artifactFiles().isEmpty()) {
            return Optional.of(rejected(FindingCode.MANIFEST_FIELD_MISSING, "<artifact-files>"));
        }
        if (manifest.artifactFiles().size() > policy.maxFileCount()) {
            return Optional.of(rejected(FindingCode.ARTIFACT_COUNT_LIMIT_EXCEEDED, "<artifact-files>"));
        }

        Set<String> logicalNames = new LinkedHashSet<>();
        Set<String> relativePaths = new LinkedHashSet<>();
        Set<String> caseFoldedRelativePaths = new LinkedHashSet<>();
        long declaredTotal = 0;
        for (ArtifactFile artifact : manifest.artifactFiles()) {
            if (artifact == null) {
                return Optional.of(rejected(FindingCode.MANIFEST_FIELD_MISSING, "<artifact-file>"));
            }
            String safePath = safeRelativeIdentifier(artifact.relativePath());
            FindingCode pathFailure = validateRelativePath(artifact.relativePath());
            if (pathFailure != null) {
                return Optional.of(rejected(pathFailure, safePath));
            }
            if (!safeMetadata(artifact.logicalName())
                    || !LOGICAL_NAME.matcher(artifact.logicalName()).matches()
                    || !safeMetadata(artifact.mediaType())
                    || !MEDIA_TYPE.matcher(artifact.mediaType()).matches()) {
                return Optional.of(rejected(FindingCode.INVALID_ARTIFACT_METADATA, safePath));
            }
            if (containsSensitiveMarker(artifact.logicalName())
                    || containsSensitiveMarker(artifact.relativePath())) {
                return Optional.of(rejected(FindingCode.SENSITIVE_METADATA, safePath));
            }
            if (!TEXT_MEDIA_TYPES.contains(artifact.mediaType().toLowerCase(Locale.ROOT))) {
                return Optional.of(rejected(FindingCode.UNSUPPORTED_MEDIA_TYPE, safePath));
            }
            if (!isSha256(artifact.sha256())) {
                return Optional.of(rejected(FindingCode.INVALID_DIGEST, safePath));
            }
            if (artifact.sizeBytes() <= 0) {
                return Optional.of(rejected(FindingCode.SIZE_MISMATCH, safePath));
            }
            if (artifact.sizeBytes() > policy.maxFileSizeBytes()) {
                return Optional.of(rejected(FindingCode.ARTIFACT_TOO_LARGE, safePath));
            }
            if (!logicalNames.add(artifact.logicalName()) || !relativePaths.add(artifact.relativePath())) {
                return Optional.of(rejected(FindingCode.DUPLICATE_ARTIFACT, safePath));
            }
            // Deployment must behave identically on case-sensitive and case-insensitive filesystems.
            if (!caseFoldedRelativePaths.add(artifact.relativePath().toLowerCase(Locale.ROOT))) {
                return Optional.of(rejected(FindingCode.CASE_COLLISION, safePath));
            }
            try {
                declaredTotal = Math.addExact(declaredTotal, artifact.sizeBytes());
            } catch (ArithmeticException exception) {
                return Optional.of(rejected(FindingCode.TOTAL_SIZE_LIMIT_EXCEEDED, "<artifact-files>"));
            }
            if (declaredTotal > policy.maxTotalSizeBytes()) {
                return Optional.of(rejected(FindingCode.TOTAL_SIZE_LIMIT_EXCEEDED, "<artifact-files>"));
            }
        }

        String computedDigest;
        try {
            computedDigest = computeArtifactDigest(manifest.artifactFiles());
        } catch (RuntimeException exception) {
            return Optional.of(rejected(FindingCode.CANONICALIZATION_FAILED, "<manifest>"));
        }
        if (!constantTimeEquals(manifest.artifactDigest(), computedDigest)) {
            return Optional.of(rejected(FindingCode.ARTIFACT_DIGEST_MISMATCH, "<manifest>"));
        }
        return Optional.empty();
    }

    /**
     * 对 manifest 声明的完整 artifact set 执行 trusted-root verification。
     *
     * @param trustedRoot 由受信 provisioning 提供的 artifact 根目录
     * @param manifest 已声明逐文件 digest/size 的 manifest
     * @return VERIFIED 或 fail-closed REJECTED；不抛出含路径/内容的文件系统异常
     */
    public StrategyArtifactVerificationResult verify(Path trustedRoot, StrategyArtifactManifest manifest) {
        Optional<StrategyArtifactVerificationResult> manifestFailure = validateManifest(manifest);
        if (manifestFailure.isPresent()) {
            return manifestFailure.get();
        }

        RootContext rootContext;
        try {
            rootContext = resolveTrustedRoot(trustedRoot);
        } catch (SafeVerificationException exception) {
            return rejected(exception.findingCode(), exception.safeRelativeIdentifier());
        }

        CaptureResult beforeCapture = captureDirectory(rootContext);
        if (beforeCapture.failure() != null) {
            return beforeCapture.failure();
        }
        DirectorySnapshot before = beforeCapture.snapshot();
        Set<String> declared = manifest.artifactFiles().stream()
                .map(ArtifactFile::relativePath)
                .collect(Collectors.toCollection(TreeSet::new));
        String missing = declared.stream().filter(path -> !before.files().contains(path)).findFirst().orElse(null);
        if (missing != null) {
            return rejected(FindingCode.ARTIFACT_NOT_FOUND, missing);
        }
        String extra = before.files().stream().filter(path -> !declared.contains(path)).findFirst().orElse(null);
        if (extra != null) {
            return rejected(FindingCode.UNDECLARED_ARTIFACT, extra);
        }

        long verifiedSize = 0;
        List<ArtifactFile> sorted = new ArrayList<>(manifest.artifactFiles());
        sorted.sort(Comparator.comparing(ArtifactFile::logicalName).thenComparing(ArtifactFile::relativePath));
        for (ArtifactFile artifact : sorted) {
            FileVerification fileVerification = verifyFile(rootContext, artifact);
            if (fileVerification.failure() != null) {
                return fileVerification.failure();
            }
            try {
                verifiedSize = Math.addExact(verifiedSize, fileVerification.verifiedSizeBytes());
            } catch (ArithmeticException exception) {
                return rejected(FindingCode.TOTAL_SIZE_LIMIT_EXCEEDED, "<artifact-files>");
            }
            if (verifiedSize > policy.maxTotalSizeBytes()) {
                return rejected(FindingCode.TOTAL_SIZE_LIMIT_EXCEEDED, "<artifact-files>");
            }
        }

        CaptureResult afterCapture = captureDirectory(rootContext);
        if (afterCapture.failure() != null) {
            return afterCapture.failure();
        }
        if (!before.equals(afterCapture.snapshot())) {
            return rejected(FindingCode.ARTIFACT_CHANGED_DURING_VERIFICATION, "<artifact-set>");
        }
        return StrategyArtifactVerificationResult.verified(
                manifest.artifactDigest(),
                manifest.artifactFiles().size(),
                verifiedSize
        );
    }

    /**
     * 计算 prototype 已冻结的 canonical aggregate digest。
     *
     * <p>排序为 logicalName、relativePath；字段顺序为 logicalName/relativePath/sha256/sizeBytes/mediaType；
     * U+001F 分隔字段、LF 分隔记录、末尾无 LF；对 UTF-8 bytes 计算 lowercase SHA-256。
     */
    public static String computeArtifactDigest(List<ArtifactFile> artifacts) {
        Objects.requireNonNull(artifacts, "artifacts must not be null");
        List<ArtifactFile> sorted = new ArrayList<>(artifacts);
        sorted.sort(Comparator.comparing(ArtifactFile::logicalName).thenComparing(ArtifactFile::relativePath));
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
        return sha256Utf8(canonical);
    }

    private RootContext resolveTrustedRoot(Path trustedRoot) throws SafeVerificationException {
        if (trustedRoot == null) {
            throw safeFailure(FindingCode.TRUSTED_ROOT_INVALID, "<root>");
        }
        try {
            Path root = trustedRoot.toAbsolutePath().normalize();
            BasicFileAttributes rootAttributes = readAttributes(root);
            if (rootAttributes.isSymbolicLink() || Files.isSymbolicLink(root)) {
                throw safeFailure(FindingCode.SYMLINK_OR_REPARSE_NOT_ALLOWED, "<root>");
            }
            if (rootAttributes.isOther()) {
                throw safeFailure(FindingCode.SYMLINK_OR_REPARSE_NOT_ALLOWED, "<root>");
            }
            if (!rootAttributes.isDirectory()) {
                throw safeFailure(FindingCode.TRUSTED_ROOT_INVALID, "<root>");
            }
            Path realRoot = root.toRealPath();
            BasicFileAttributes realAttributes = readAttributes(realRoot);
            if (!realAttributes.isDirectory() || realAttributes.isSymbolicLink() || realAttributes.isOther()) {
                throw safeFailure(FindingCode.TRUSTED_ROOT_INVALID, "<root>");
            }
            return new RootContext(root, realRoot);
        } catch (SafeVerificationException exception) {
            throw exception;
        } catch (NoSuchFileException exception) {
            throw safeFailure(FindingCode.TRUSTED_ROOT_INVALID, "<root>");
        } catch (IOException | SecurityException exception) {
            throw safeFailure(FindingCode.TRUSTED_ROOT_INVALID, "<root>");
        }
    }

    private CaptureResult captureDirectory(RootContext rootContext) {
        Map<String, FileIdentity> identities = new LinkedHashMap<>();
        Set<String> files = new TreeSet<>();
        long[] totalSize = {0};
        try {
            Files.walkFileTree(rootContext.realRoot(), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) throws IOException {
                    String relative = relativeIdentifier(rootContext.realRoot(), dir);
                    rejectLinkOrSpecial(dir, attributes, relative);
                    Path real = dir.toRealPath();
                    if (!real.startsWith(rootContext.realRoot())) {
                        throw safeFailure(FindingCode.PATH_ESCAPES_TRUSTED_ROOT, relative);
                    }
                    identities.put(relative, identity("D", attributes));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    String relative = relativeIdentifier(rootContext.realRoot(), file);
                    rejectLinkOrSpecial(file, attributes, relative);
                    if (!attributes.isRegularFile()) {
                        throw safeFailure(FindingCode.ARTIFACT_NOT_REGULAR_FILE, relative);
                    }
                    Path real = file.toRealPath();
                    if (!real.startsWith(rootContext.realRoot())) {
                        throw safeFailure(FindingCode.PATH_ESCAPES_TRUSTED_ROOT, relative);
                    }
                    if (attributes.size() > policy.maxFileSizeBytes()) {
                        throw safeFailure(FindingCode.ARTIFACT_TOO_LARGE, relative);
                    }
                    files.add(relative);
                    if (files.size() > policy.maxFileCount()) {
                        throw safeFailure(FindingCode.ARTIFACT_COUNT_LIMIT_EXCEEDED, "<artifact-files>");
                    }
                    try {
                        totalSize[0] = Math.addExact(totalSize[0], attributes.size());
                    } catch (ArithmeticException exception) {
                        throw safeFailure(FindingCode.TOTAL_SIZE_LIMIT_EXCEEDED, "<artifact-files>");
                    }
                    if (totalSize[0] > policy.maxTotalSizeBytes()) {
                        throw safeFailure(FindingCode.TOTAL_SIZE_LIMIT_EXCEEDED, "<artifact-files>");
                    }
                    identities.put(relative, identity("F", attributes));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exception) throws IOException {
                    String relative = relativeIdentifier(rootContext.realRoot(), file);
                    if (Files.isSymbolicLink(file)) {
                        throw safeFailure(FindingCode.SYMLINK_OR_REPARSE_NOT_ALLOWED, relative);
                    }
                    throw safeFailure(FindingCode.VERIFICATION_IO_FAILED, relative);
                }
            });
            return new CaptureResult(new DirectorySnapshot(Map.copyOf(identities), Set.copyOf(files), totalSize[0]), null);
        } catch (SafeVerificationException exception) {
            return new CaptureResult(null, rejected(exception.findingCode(), exception.safeRelativeIdentifier()));
        } catch (IOException | SecurityException exception) {
            return new CaptureResult(null, rejected(FindingCode.VERIFICATION_IO_FAILED, "<artifact-set>"));
        }
    }

    private FileVerification verifyFile(RootContext rootContext, ArtifactFile artifact) {
        String relative = artifact.relativePath();
        try {
            Path target = rootContext.root().resolve(relative).normalize();
            if (!target.startsWith(rootContext.root())) {
                return failedFile(FindingCode.PATH_ESCAPES_TRUSTED_ROOT, relative);
            }
            inspectPathComponents(rootContext.root(), relative);
            Path realTarget = target.toRealPath();
            if (!realTarget.startsWith(rootContext.realRoot())) {
                return failedFile(FindingCode.PATH_ESCAPES_TRUSTED_ROOT, relative);
            }

            BasicFileAttributes before = readAttributes(realTarget);
            rejectLinkOrSpecial(realTarget, before, relative);
            if (!before.isRegularFile()) {
                return failedFile(FindingCode.ARTIFACT_NOT_REGULAR_FILE, relative);
            }
            if (before.size() > policy.maxFileSizeBytes()) {
                return failedFile(FindingCode.ARTIFACT_TOO_LARGE, relative);
            }

            afterPreReadAttributes.run(realTarget);
            DigestRead digestRead = streamDigest(realTarget, policy.maxFileSizeBytes());
            if (digestRead.tooLarge()) {
                return failedFile(FindingCode.ARTIFACT_TOO_LARGE, relative);
            }
            if (digestRead.sensitiveValueFound()) {
                return failedFile(FindingCode.SENSITIVE_ARTIFACT_VALUE, relative);
            }

            Path realTargetAfter = target.toRealPath();
            if (!realTargetAfter.startsWith(rootContext.realRoot()) || !realTargetAfter.equals(realTarget)) {
                return failedFile(FindingCode.ARTIFACT_CHANGED_DURING_VERIFICATION, relative);
            }
            BasicFileAttributes after = readAttributes(realTargetAfter);
            if (!sameIdentity(before, after)) {
                return failedFile(FindingCode.ARTIFACT_CHANGED_DURING_VERIFICATION, relative);
            }
            if (digestRead.sizeBytes() != artifact.sizeBytes()) {
                return failedFile(FindingCode.SIZE_MISMATCH, relative);
            }
            if (!constantTimeEquals(artifact.sha256(), digestRead.sha256())) {
                return failedFile(FindingCode.DIGEST_MISMATCH, relative);
            }
            return new FileVerification(digestRead.sizeBytes(), null);
        } catch (SafeVerificationException exception) {
            return new FileVerification(
                    0,
                    rejected(exception.findingCode(), exception.safeRelativeIdentifier())
            );
        } catch (NoSuchFileException exception) {
            return failedFile(FindingCode.ARTIFACT_NOT_FOUND, relative);
        } catch (UnsupportedOperationException exception) {
            return failedFile(FindingCode.PLATFORM_LINK_GUARANTEE_UNAVAILABLE, relative);
        } catch (FileSystemException exception) {
            Path target = rootContext.root().resolve(relative).normalize();
            return failedFile(
                    Files.isSymbolicLink(target)
                            ? FindingCode.SYMLINK_OR_REPARSE_NOT_ALLOWED
                            : FindingCode.VERIFICATION_IO_FAILED,
                    relative
            );
        } catch (IOException | SecurityException exception) {
            return failedFile(FindingCode.VERIFICATION_IO_FAILED, relative);
        }
    }

    private void inspectPathComponents(Path root, String relativePath) throws IOException {
        Path current = root;
        String[] segments = relativePath.split("/", -1);
        for (int index = 0; index < segments.length; index++) {
            current = current.resolve(segments[index]);
            BasicFileAttributes attributes = readAttributes(current);
            rejectLinkOrSpecial(current, attributes, relativePath);
            if (index < segments.length - 1 && !attributes.isDirectory()) {
                throw safeFailure(FindingCode.ARTIFACT_NOT_FOUND, relativePath);
            }
        }
    }

    private void rejectLinkOrSpecial(Path path, BasicFileAttributes attributes, String relative) throws IOException {
        if (attributes.isSymbolicLink() || Files.isSymbolicLink(path) || attributes.isOther()) {
            throw safeFailure(FindingCode.SYMLINK_OR_REPARSE_NOT_ALLOWED, relative);
        }
    }

    private DigestRead streamDigest(Path target, long maxAllowedBytes) throws IOException {
        MessageDigest digest = newSha256();
        SensitiveContentScanner scanner = new SensitiveContentScanner();
        long sizeBytes = 0;
        Set<OpenOption> options = Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
        try (SeekableByteChannel channel = Files.newByteChannel(target, options)) {
            ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
            int read;
            while ((read = channel.read(buffer)) != -1) {
                if (read == 0) {
                    buffer.clear();
                    continue;
                }
                try {
                    sizeBytes = Math.addExact(sizeBytes, read);
                } catch (ArithmeticException exception) {
                    return new DigestRead(null, 0, true, false);
                }
                buffer.flip();
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                buffer.clear();
                if (sizeBytes > maxAllowedBytes) {
                    return new DigestRead(null, sizeBytes, true, false);
                }
                digest.update(chunk);
                if (scanner.accept(chunk)) {
                    return new DigestRead(null, sizeBytes, false, true);
                }
            }
        }
        return new DigestRead(HexFormat.of().formatHex(digest.digest()), sizeBytes, false, false);
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

    private static FileIdentity identity(String kind, BasicFileAttributes attributes) {
        boolean regularFile = "F".equals(kind);
        return new FileIdentity(
                kind,
                regularFile ? attributes.size() : 0,
                regularFile ? attributes.lastModifiedTime().toMillis() : 0,
                attributes.fileKey()
        );
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
            return Path.of(value).isAbsolute() ? FindingCode.INVALID_RELATIVE_PATH : null;
        } catch (InvalidPathException exception) {
            return FindingCode.INVALID_RELATIVE_PATH;
        }
    }

    private static boolean hasOpaqueId(String value) {
        return value != null && OPAQUE_ID.matcher(value).matches();
    }

    private static boolean safeMetadata(String value) {
        return value != null && !value.isBlank() && !containsControl(value) && value.indexOf('\u001f') < 0;
    }

    private static boolean containsSensitiveMarker(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return SENSITIVE_MARKERS.stream().anyMatch(normalized::contains);
    }

    private static boolean containsControl(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }

    private static boolean isSha256(String value) {
        return value != null && SHA_256.matcher(value).matches();
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return expected != null
                && actual != null
                && MessageDigest.isEqual(
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
        return HexFormat.of().formatHex(newSha256().digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static String relativeIdentifier(Path root, Path value) {
        try {
            if (root.equals(value)) {
                return "<root>";
            }
            return safeRelativeIdentifier(root.relativize(value).toString().replace('\\', '/'));
        } catch (RuntimeException exception) {
            return "<invalid-relative-path>";
        }
    }

    private static String safeRelativeIdentifier(String value) {
        FindingCode finding = validateRelativePath(value);
        if (finding != null) {
            return "<invalid-relative-path>";
        }
        return value.length() <= 512 ? value : "<invalid-relative-path>";
    }

    private static StrategyArtifactVerificationResult rejected(FindingCode code, String safeIdentifier) {
        return StrategyArtifactVerificationResult.rejected(code, safeIdentifier);
    }

    private static FileVerification failedFile(FindingCode code, String relative) {
        return new FileVerification(0, rejected(code, safeRelativeIdentifier(relative)));
    }

    private static SafeVerificationException safeFailure(FindingCode code, String relative) {
        return new SafeVerificationException(code, relative);
    }

    private record RootContext(Path root, Path realRoot) {
    }

    private record FileIdentity(String kind, long size, long lastModifiedMillis, Object fileKey) {
    }

    private record DirectorySnapshot(Map<String, FileIdentity> entries, Set<String> files, long totalSizeBytes) {
    }

    private record CaptureResult(DirectorySnapshot snapshot, StrategyArtifactVerificationResult failure) {
    }

    private record FileVerification(long verifiedSizeBytes, StrategyArtifactVerificationResult failure) {
    }

    private record DigestRead(String sha256, long sizeBytes, boolean tooLarge, boolean sensitiveValueFound) {
    }

    private static final class SensitiveContentScanner {
        private String overlap = "";

        private boolean accept(byte[] chunk) {
            String current = overlap + new String(chunk, StandardCharsets.ISO_8859_1);
            if (SENSITIVE_VALUE.matcher(current).find()) {
                return true;
            }
            overlap = current.length() <= SENSITIVE_SCAN_OVERLAP
                    ? current
                    : current.substring(current.length() - SENSITIVE_SCAN_OVERLAP);
            return false;
        }
    }

    private static final class SafeVerificationException extends IOException {
        private final FindingCode findingCode;
        private final String safeRelativeIdentifier;

        private SafeVerificationException(FindingCode findingCode, String safeRelativeIdentifier) {
            super();
            this.findingCode = findingCode;
            this.safeRelativeIdentifier = safeRelativeIdentifier;
        }

        private FindingCode findingCode() {
            return findingCode;
        }

        private String safeRelativeIdentifier() {
            return safeRelativeIdentifier;
        }
    }

    @FunctionalInterface
    interface VerificationHook {
        void run(Path target) throws IOException;
    }
}
