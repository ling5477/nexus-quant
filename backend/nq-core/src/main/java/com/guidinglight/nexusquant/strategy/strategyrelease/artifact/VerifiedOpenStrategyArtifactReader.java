package com.guidinglight.nexusquant.strategy.strategyrelease.artifact;

import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactManifest.ArtifactFile;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactVerificationResult.FindingCode;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Linux production artifact 的 verified-open consumption boundary。
 *
 * <p>本类先复用 {@link TrustedRootStrategyArtifactVerifier} 完成 root/locator/manifest/完整文件集验证，
 * 再通过 {@link SecureDirectoryStream} 逐级 NOFOLLOW 打开对象。source digest 与 sealed snapshot 写入来自
 * 同一个 open file object；snapshot 是有总量上限且不暴露 backing array 的私有内存副本。全部 source
 * digest/identity/path 与 manifest 最终复核完成后，reader 才返回可消费结果，因此任何 callback 都不能在 closure 前执行。
 * 路径 replace/rename/symlink、file-directory swap、truncate、原地写入或同路径不同 identity 均 fail-closed。
 * 没有 SecureDirectoryStream/POSIX unlink 语义的平台不授权。</p>
 */
public final class VerifiedOpenStrategyArtifactReader {

    private static final int BUFFER_SIZE = 8192;
    private static final long DEFAULT_MAX_SNAPSHOT_BYTES = 64L * 1024 * 1024;
    private static final StableReadHook NOOP_HOOK = (ignoredRoot, ignoredArtifact) -> {
    };

    private final TrustedRootStrategyArtifactVerifier verifier;
    private final StableReadHook afterSetVerification;
    private final StableReadHook afterStableDigest;
    private final long maxSnapshotBytes;

    public VerifiedOpenStrategyArtifactReader(TrustedRootStrategyArtifactVerifier verifier) {
        this(verifier, DEFAULT_MAX_SNAPSHOT_BYTES);
    }

    public VerifiedOpenStrategyArtifactReader(
            TrustedRootStrategyArtifactVerifier verifier,
            long maxSnapshotBytes
    ) {
        this(verifier, NOOP_HOOK, NOOP_HOOK, maxSnapshotBytes);
    }

    VerifiedOpenStrategyArtifactReader(
            TrustedRootStrategyArtifactVerifier verifier,
            StableReadHook afterSetVerification,
            StableReadHook afterStableDigest
    ) {
        this(verifier, afterSetVerification, afterStableDigest, DEFAULT_MAX_SNAPSHOT_BYTES);
    }

    VerifiedOpenStrategyArtifactReader(
            TrustedRootStrategyArtifactVerifier verifier,
            StableReadHook afterSetVerification,
            StableReadHook afterStableDigest,
            long maxSnapshotBytes
    ) {
        this.verifier = Objects.requireNonNull(verifier, "verifier must not be null");
        this.afterSetVerification = Objects.requireNonNull(afterSetVerification);
        this.afterStableDigest = Objects.requireNonNull(afterStableDigest);
        if (maxSnapshotBytes <= 0 || maxSnapshotBytes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("maxSnapshotBytes must be in (0, Integer.MAX_VALUE]");
        }
        this.maxSnapshotBytes = maxSnapshotBytes;
    }

    /**
     * 验证 manifest 声明的完整 artifact set，并在最终 closure 后返回一次性 snapshot 结果。
     *
     * @return 仅支持 stable handle 的运行时可返回 SUPPORTED_RUNTIME_CLOSED
     */
    public StableArtifactSnapshotResult verifyAndSnapshot(
            Path trustedRoot,
            StrategyArtifactManifest manifest
    ) {
        StrategyArtifactVerificationResult initial = verifier.verify(trustedRoot, manifest);
        if (initial.status() != StrategyArtifactVerificationResult.Status.VERIFIED) {
            return rejected(initial.reasonCode(), initial.safeRelativeIdentifier());
        }
        if (initial.verifiedSizeBytes() > maxSnapshotBytes) {
            return rejected(FindingCode.IMMUTABLE_SNAPSHOT_LIMIT_EXCEEDED, "<artifact-set>");
        }
        if (!System.getProperty("os.name", "unknown").toLowerCase(Locale.ROOT).contains("linux")) {
            return rejected(FindingCode.PLATFORM_STABLE_HANDLE_UNAVAILABLE, "<root>");
        }

        Path realRoot;
        BasicFileAttributes rootBefore;
        Map<String, BasicFileAttributes> verifiedPathIdentities = new LinkedHashMap<>();
        try {
            realRoot = trustedRoot.toAbsolutePath().normalize().toRealPath(LinkOption.NOFOLLOW_LINKS);
            rootBefore = attributes(realRoot);
            if (!rootBefore.isDirectory() || rootBefore.fileKey() == null) {
                return rejected(FindingCode.STABLE_FILE_IDENTITY_UNAVAILABLE, "<root>");
            }
            for (ArtifactFile artifact : manifest.artifactFiles()) {
                BasicFileAttributes pathIdentity = attributes(realRoot.resolve(artifact.relativePath()));
                if (!pathIdentity.isRegularFile() || pathIdentity.fileKey() == null) {
                    return rejected(FindingCode.STABLE_FILE_IDENTITY_UNAVAILABLE, artifact.relativePath());
                }
                verifiedPathIdentities.put(artifact.relativePath(), pathIdentity);
            }
            afterSetVerification.run(realRoot, null);
        } catch (IOException | RuntimeException exception) {
            return rejected(FindingCode.ARTIFACT_CHANGED_DURING_STABLE_READ, "<root>");
        }

        List<ArtifactFile> verifiedDescriptors = new ArrayList<>();
        List<byte[]> verifiedSnapshots = new ArrayList<>();
        boolean snapshotsTransferred = false;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(realRoot)) {
            if (!(stream instanceof SecureDirectoryStream<Path> secureRoot)) {
                return rejected(FindingCode.PLATFORM_STABLE_HANDLE_UNAVAILABLE, "<root>");
            }
            long total = 0;
            List<ArtifactFile> artifacts = new ArrayList<>(manifest.artifactFiles());
            artifacts.sort(Comparator.comparing(ArtifactFile::logicalName)
                    .thenComparing(ArtifactFile::relativePath));
            for (ArtifactFile artifact : artifacts) {
                long nextTotal = Math.addExact(total, artifact.sizeBytes());
                if (nextTotal > maxSnapshotBytes) {
                    return rejected(FindingCode.IMMUTABLE_SNAPSHOT_LIMIT_EXCEEDED, artifact.relativePath());
                }
                StableArtifactRead read = readOne(
                        realRoot,
                        secureRoot,
                        artifact,
                        verifiedPathIdentities.get(artifact.relativePath())
                );
                if (read.failure() != null) {
                    return read.failure();
                }
                verifiedDescriptors.add(artifact);
                verifiedSnapshots.add(read.bytes());
                total = nextTotal;
            }
            BasicFileAttributes rootAfter = attributes(realRoot);
            if (!sameIdentity(rootBefore, rootAfter)) {
                return rejected(FindingCode.ARTIFACT_CHANGED_DURING_STABLE_READ, "<root>");
            }
            StrategyArtifactVerificationResult finalVerification = verifier.verify(realRoot, manifest);
            if (finalVerification.status() != StrategyArtifactVerificationResult.Status.VERIFIED) {
                return rejected(FindingCode.ARTIFACT_CHANGED_DURING_STABLE_READ, "<artifact-set>");
            }
            StableArtifactSnapshotResult result = StableArtifactSnapshotResult.closed(
                    manifest.artifactDigest(), total, verifiedDescriptors, verifiedSnapshots);
            snapshotsTransferred = true;
            return result;
        } catch (UnsupportedOperationException exception) {
            return rejected(FindingCode.PLATFORM_STABLE_HANDLE_UNAVAILABLE, "<root>");
        } catch (IOException | SecurityException | ArithmeticException exception) {
            return rejected(FindingCode.ARTIFACT_CHANGED_DURING_STABLE_READ, "<artifact-set>");
        } catch (OutOfMemoryError error) {
            return rejected(FindingCode.IMMUTABLE_SNAPSHOT_LIMIT_EXCEEDED, "<artifact-set>");
        } finally {
            if (!snapshotsTransferred) {
                eraseSnapshots(verifiedSnapshots);
            }
        }
    }

    private StableArtifactRead readOne(
            Path realRoot,
            SecureDirectoryStream<Path> secureRoot,
            ArtifactFile artifact,
            BasicFileAttributes verifiedPathIdentity
    ) {
        List<SecureDirectoryStream<Path>> openedDirectories = new ArrayList<>();
        try {
            String[] segments = artifact.relativePath().split("/", -1);
            SecureDirectoryStream<Path> current = secureRoot;
            for (int index = 0; index < segments.length - 1; index++) {
                DirectoryStream<Path> child = current.newDirectoryStream(
                        Path.of(segments[index]), LinkOption.NOFOLLOW_LINKS);
                if (!(child instanceof SecureDirectoryStream<Path> secureChild)) {
                    child.close();
                    return failed(FindingCode.PLATFORM_STABLE_HANDLE_UNAVAILABLE, artifact.relativePath());
                }
                openedDirectories.add(secureChild);
                current = secureChild;
            }

            Path fileName = Path.of(segments[segments.length - 1]);
            BasicFileAttributes pathBefore = secureAttributes(current, fileName);
            if (!pathBefore.isRegularFile() || pathBefore.fileKey() == null) {
                return failed(FindingCode.STABLE_FILE_IDENTITY_UNAVAILABLE, artifact.relativePath());
            }
            if (!sameIdentity(verifiedPathIdentity, pathBefore)) {
                return failed(FindingCode.ARTIFACT_CHANGED_DURING_STABLE_READ, artifact.relativePath());
            }
            if (artifact.sizeBytes() > maxSnapshotBytes) {
                return failed(FindingCode.IMMUTABLE_SNAPSHOT_LIMIT_EXCEEDED, artifact.relativePath());
            }
            Set<OpenOption> options = Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
            try (SeekableByteChannel channel = current.newByteChannel(fileName, options)) {
                SnapshotRead snapshot = snapshotAndDigest(channel, artifact.sizeBytes());
                boolean accepted = false;
                try {
                    if (!matches(artifact, snapshot.digest())) {
                        return failed(snapshot.digest().tooLarge
                                        ? FindingCode.ARTIFACT_TOO_LARGE : FindingCode.DIGEST_MISMATCH,
                                artifact.relativePath());
                    }
                    afterStableDigest.run(realRoot, artifact);
                    DigestRead sealed = digest(snapshot.bytes());
                    channel.position(0);
                    DigestRead second = digest(channel, artifact.sizeBytes());
                    BasicFileAttributes pathAfter = secureAttributes(current, fileName);
                    if (!matches(artifact, sealed) || !matches(artifact, second)
                            || !sameIdentity(pathBefore, pathAfter)) {
                        return failed(FindingCode.ARTIFACT_CHANGED_DURING_STABLE_READ,
                                artifact.relativePath());
                    }
                    StableArtifactRead acceptedRead = new StableArtifactRead(snapshot.bytes(), null);
                    accepted = true;
                    return acceptedRead;
                } finally {
                    if (!accepted) {
                        Arrays.fill(snapshot.bytes(), (byte) 0);
                    }
                }
            }
        } catch (IOException | RuntimeException exception) {
            return failed(FindingCode.ARTIFACT_CHANGED_DURING_STABLE_READ, artifact.relativePath());
        } catch (OutOfMemoryError error) {
            return failed(FindingCode.IMMUTABLE_SNAPSHOT_LIMIT_EXCEEDED, artifact.relativePath());
        } finally {
            for (int index = openedDirectories.size() - 1; index >= 0; index--) {
                try {
                    openedDirectories.get(index).close();
                } catch (IOException ignored) {
                    // The operation is already complete or rejected; closing must not mask the primary result.
                }
            }
        }
    }

    private static BasicFileAttributes secureAttributes(SecureDirectoryStream<Path> directory, Path name)
            throws IOException {
        BasicFileAttributeView view = directory.getFileAttributeView(
                name, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (view == null) {
            throw new IOException("stable attribute view unavailable");
        }
        return view.readAttributes();
    }

    private static BasicFileAttributes attributes(Path path) throws IOException {
        return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }

    private static DigestRead digest(SeekableByteChannel channel, long expectedSize) throws IOException {
        MessageDigest digest = sha256();
        long size = 0;
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
        int read;
        while ((read = channel.read(buffer)) != -1) {
            if (read == 0) {
                buffer.clear();
                continue;
            }
            size = Math.addExact(size, read);
            if (size > expectedSize) {
                return new DigestRead(null, size, true);
            }
            buffer.flip();
            digest.update(buffer);
            buffer.clear();
        }
        return new DigestRead(HexFormat.of().formatHex(digest.digest()), size, false);
    }

    /** 从已打开 source handle 读取精确长度的私有 snapshot，并对完全相同的 bytes 计算 digest。 */
    private static SnapshotRead snapshotAndDigest(SeekableByteChannel source, long expectedSize) throws IOException {
        byte[] bytes = new byte[Math.toIntExact(expectedSize)];
        boolean transferred = false;
        try {
            MessageDigest digest = sha256();
            long size = 0;
            ByteBuffer target = ByteBuffer.wrap(bytes);
            while (target.hasRemaining()) {
                int before = target.position();
                int read = source.read(target);
                if (read == -1) break;
                if (read == 0) {
                    continue;
                }
                size = Math.addExact(size, read);
                ByteBuffer observed = target.asReadOnlyBuffer();
                observed.position(before);
                observed.limit(target.position());
                digest.update(observed);
            }
            SnapshotRead result;
            if (!target.hasRemaining()) {
                ByteBuffer extra = ByteBuffer.allocate(1);
                int extraRead = source.read(extra);
                if (extraRead > 0) {
                    result = new SnapshotRead(bytes, new DigestRead(null, size + extraRead, true));
                    transferred = true;
                    return result;
                }
            }
            result = new SnapshotRead(
                    bytes,
                    new DigestRead(HexFormat.of().formatHex(digest.digest()), size, false));
            transferred = true;
            return result;
        } finally {
            if (!transferred) {
                Arrays.fill(bytes, (byte) 0);
            }
        }
    }

    private static DigestRead digest(byte[] bytes) {
        return new DigestRead(HexFormat.of().formatHex(sha256().digest(bytes)), bytes.length, false);
    }

    private static boolean matches(ArtifactFile artifact, DigestRead observed) {
        return !observed.tooLarge
                && observed.size == artifact.sizeBytes()
                && MessageDigest.isEqual(
                artifact.sha256().getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                observed.sha256.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    private static boolean sameIdentity(BasicFileAttributes left, BasicFileAttributes right) {
        return left.isRegularFile() == right.isRegularFile()
                && left.size() == right.size()
                && left.lastModifiedTime().equals(right.lastModifiedTime())
                && left.fileKey() != null
                && left.fileKey().equals(right.fileKey());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static StableArtifactSnapshotResult rejected(FindingCode code, String safeIdentifier) {
        return StableArtifactSnapshotResult.rejected(code, safeIdentifier);
    }

    private static StableArtifactRead failed(FindingCode code, String safeIdentifier) {
        return new StableArtifactRead(null, rejected(code, safeIdentifier));
    }

    private static void eraseSnapshots(List<byte[]> snapshots) {
        for (byte[] snapshot : snapshots) {
            Arrays.fill(snapshot, (byte) 0);
        }
        snapshots.clear();
    }

    private record DigestRead(String sha256, long size, boolean tooLarge) {
    }

    private record SnapshotRead(byte[] bytes, DigestRead digest) {
    }

    private record StableArtifactRead(byte[] bytes, StableArtifactSnapshotResult failure) {
    }

    @FunctionalInterface
    interface StableReadHook {
        void run(Path root, ArtifactFile artifact) throws IOException;
    }

    /**
     * callback 生命周期和线程绑定的 artifact 输入；不暴露 close 或底层 file handle。
     */
    public static final class VerifiedArtifactInput {
        private final ByteBuffer snapshot;
        private final long size;
        private final Thread ownerThread;
        private boolean active = true;

        VerifiedArtifactInput(ByteBuffer snapshot, long size) {
            this.snapshot = snapshot;
            this.size = size;
            this.ownerThread = Thread.currentThread();
        }

        public int read(ByteBuffer target) throws IOException {
            requireActive();
            Objects.requireNonNull(target, "target must not be null");
            if (!snapshot.hasRemaining()) return -1;
            int read = Math.min(target.remaining(), snapshot.remaining());
            ByteBuffer chunk = snapshot.asReadOnlyBuffer();
            chunk.limit(chunk.position() + read);
            target.put(chunk);
            snapshot.position(snapshot.position() + read);
            return read;
        }

        public long size() {
            requireActive();
            return size;
        }

        private void requireActive() {
            if (!active || Thread.currentThread() != ownerThread) {
                throw new IllegalStateException("verified artifact input is outside callback scope");
            }
        }

        void invalidate() {
            active = false;
        }

        long consumedBytes() {
            return snapshot.position();
        }
    }
}
