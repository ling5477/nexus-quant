package com.guidinglight.nexusquant.strategy.strategyrelease.artifact;

import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactManifest.ArtifactFile;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactVerificationResult.FindingCode;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Strategy artifact verified-open 的一次性、受生命周期约束的 snapshot 结果。
 *
 * <p>{@link Status#SUPPORTED_RUNTIME_CLOSED} 只在完整 artifact set 的 source handle、path identity、
 * trusted-root 与 manifest 最终复核全部通过后返回。调用方只能在 reader 返回以后通过
 * {@link #consumeVerified(VerifiedOpenStrategyArtifactConsumer)} 同步消费私有只读 snapshot；消费结束、异常
 * 或显式 {@link #close()} 后会清零全部 backing buffer。结果绑定创建线程，不允许跨线程延长 secret-like
 * artifact bytes 的生命周期。该状态不是 deployment、LIVE 或交易授权。</p>
 */
public final class StableArtifactSnapshotResult implements AutoCloseable {

    private final Status status;
    private final FindingCode reasonCode;
    private final String safeRelativeIdentifier;
    private final String artifactDigest;
    private final int snapshotFileCount;
    private final long snapshotSizeBytes;
    private final List<ArtifactSnapshot> snapshots;
    private final Thread ownerThread;
    private boolean active;
    private boolean consuming;
    private boolean closeRequested;

    private StableArtifactSnapshotResult(
            Status status,
            FindingCode reasonCode,
            String safeRelativeIdentifier,
            String artifactDigest,
            int snapshotFileCount,
            long snapshotSizeBytes,
            List<ArtifactSnapshot> snapshots
    ) {
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.reasonCode = reasonCode;
        this.safeRelativeIdentifier = safeRelativeIdentifier;
        this.artifactDigest = artifactDigest;
        this.snapshotFileCount = snapshotFileCount;
        this.snapshotSizeBytes = snapshotSizeBytes;
        this.snapshots = new ArrayList<>(Objects.requireNonNull(snapshots, "snapshots must not be null"));
        this.ownerThread = Thread.currentThread();
        this.active = status == Status.SUPPORTED_RUNTIME_CLOSED;
        if (status == Status.SUPPORTED_RUNTIME_CLOSED) {
            Objects.requireNonNull(artifactDigest, "artifactDigest must not be null");
            if (reasonCode != null || snapshotFileCount <= 0 || snapshotSizeBytes <= 0
                    || snapshots.size() != snapshotFileCount) {
                throw new IllegalArgumentException("closed result requires matching positive snapshot facts");
            }
        } else if (reasonCode == null || !snapshots.isEmpty()) {
            throw new IllegalArgumentException("rejected result requires reasonCode and no snapshots");
        }
    }

    static StableArtifactSnapshotResult closed(
            String digest,
            long sizeBytes,
            List<ArtifactFile> descriptors,
            List<byte[]> snapshotBytes
    ) {
        Objects.requireNonNull(descriptors, "descriptors must not be null");
        Objects.requireNonNull(snapshotBytes, "snapshotBytes must not be null");
        if (descriptors.size() != snapshotBytes.size()) {
            throw new IllegalArgumentException("descriptor and snapshot counts must match");
        }
        List<ArtifactSnapshot> snapshots = new ArrayList<>(descriptors.size());
        for (int index = 0; index < descriptors.size(); index++) {
            snapshots.add(new ArtifactSnapshot(descriptors.get(index), snapshotBytes.get(index)));
        }
        return new StableArtifactSnapshotResult(
                Status.SUPPORTED_RUNTIME_CLOSED,
                null,
                null,
                digest,
                descriptors.size(),
                sizeBytes,
                snapshots
        );
    }

    public static StableArtifactSnapshotResult rejected(FindingCode code, String safeIdentifier) {
        return new StableArtifactSnapshotResult(
                Status.REJECTED,
                Objects.requireNonNull(code),
                safeIdentifier,
                null,
                0,
                0,
                List.of()
        );
    }

    /**
     * 在最终 closure 已通过且 reader 已返回后，一次性同步消费全部只读 snapshot。
     *
     * <p>无论消费成功、部分读取、callback 异常或运行时异常，所有 snapshot 都会在返回前清零并失效。
     * callback 可以构建尚未启动的 immutable worker package；此方法本身不授予部署或交易权限。</p>
     *
     * @throws IOException callback 失败或任一 artifact 未被完整读取
     * @throws IllegalStateException rejected、已消费、已关闭或跨线程调用
     */
    public synchronized void consumeVerified(VerifiedOpenStrategyArtifactConsumer consumer) throws IOException {
        requireConsumable();
        Objects.requireNonNull(consumer, "consumer must not be null");
        consuming = true;
        try {
            for (ArtifactSnapshot artifactSnapshot : snapshots) {
                VerifiedOpenStrategyArtifactReader.VerifiedArtifactInput input =
                        new VerifiedOpenStrategyArtifactReader.VerifiedArtifactInput(
                                ByteBuffer.wrap(artifactSnapshot.bytes).asReadOnlyBuffer(),
                                artifactSnapshot.descriptor.sizeBytes()
                        );
                try {
                    consumer.consume(artifactSnapshot.descriptor, input);
                    if (input.consumedBytes() != artifactSnapshot.descriptor.sizeBytes()) {
                        throw new IOException("verified artifact was not fully consumed");
                    }
                } finally {
                    input.invalidate();
                }
            }
        } finally {
            consuming = false;
            eraseSnapshots();
        }
    }

    public Status status() {
        return status;
    }

    public FindingCode reasonCode() {
        return reasonCode;
    }

    public String safeRelativeIdentifier() {
        return safeRelativeIdentifier;
    }

    public String artifactDigest() {
        return artifactDigest;
    }

    public int snapshotFileCount() {
        return snapshotFileCount;
    }

    public long snapshotSizeBytes() {
        return snapshotSizeBytes;
    }

    @Override
    public synchronized void close() {
        if (consuming) {
            closeRequested = true;
            return;
        }
        eraseSnapshots();
    }

    private void requireConsumable() {
        if (status != Status.SUPPORTED_RUNTIME_CLOSED || !active || consuming
                || Thread.currentThread() != ownerThread) {
            throw new IllegalStateException("verified artifact snapshots are unavailable");
        }
    }

    private void eraseSnapshots() {
        if (!active && !closeRequested) {
            return;
        }
        for (ArtifactSnapshot snapshot : snapshots) {
            Arrays.fill(snapshot.bytes, (byte) 0);
        }
        snapshots.clear();
        active = false;
        closeRequested = false;
    }

    private static final class ArtifactSnapshot {
        private final ArtifactFile descriptor;
        private final byte[] bytes;

        private ArtifactSnapshot(ArtifactFile descriptor, byte[] bytes) {
            this.descriptor = Objects.requireNonNull(descriptor, "descriptor must not be null");
            this.bytes = Objects.requireNonNull(bytes, "bytes must not be null");
        }
    }

    public enum Status {
        SUPPORTED_RUNTIME_CLOSED,
        REJECTED
    }
}
