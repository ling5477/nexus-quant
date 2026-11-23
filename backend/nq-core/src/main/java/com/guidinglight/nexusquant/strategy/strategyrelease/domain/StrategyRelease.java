package com.guidinglight.nexusquant.strategy.strategyrelease.domain;

import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactManifest;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactVerificationResult;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Strategy Release 的 immutable production view/aggregate。
 *
 * <p>releaseAnchorId 与 publishRecordId 必须始终相同，并共同引用
 * {@code backtest_publish_records.publish_record_id}。本模型不创建平行 release UUID，不包含 Shadow、
 * LIVE、交易审批或部署状态，也不产生数据库写入。
 */
public record StrategyRelease(
        String releaseAnchorId,
        String publishRecordId,
        String strategyVersionId,
        UUID datasetId,
        String evaluationId,
        StrategyArtifactManifest artifactManifest,
        String artifactDigest,
        StrategyReleaseStatus releaseStatus,
        StrategyArtifactVerificationResult verificationResult,
        Instant createdAt,
        Instant publishedAt
) {
    public StrategyRelease {
        releaseAnchorId = requireText(releaseAnchorId, "releaseAnchorId");
        publishRecordId = requireText(publishRecordId, "publishRecordId");
        if (!releaseAnchorId.equals(publishRecordId)) {
            throw new IllegalArgumentException("releaseAnchorId must equal publishRecordId");
        }
        Objects.requireNonNull(artifactManifest, "artifactManifest must not be null");
        Objects.requireNonNull(releaseStatus, "releaseStatus must not be null");
        Objects.requireNonNull(verificationResult, "verificationResult must not be null");
        if (releaseStatus == StrategyReleaseStatus.VERIFIED
                && verificationResult.status() != StrategyArtifactVerificationResult.Status.VERIFIED) {
            throw new IllegalArgumentException("VERIFIED release requires VERIFIED artifact result");
        }
        if (releaseStatus == StrategyReleaseStatus.REJECTED
                && verificationResult.status() != StrategyArtifactVerificationResult.Status.REJECTED) {
            throw new IllegalArgumentException("REJECTED release requires REJECTED artifact result");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
