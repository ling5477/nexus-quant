package com.guidinglight.nexusquant.strategy.strategyrelease.artifact;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Strategy Artifact Manifest 的稳定 production contract。
 *
 * <p>字段故意限制为当前 publish/evaluation/dataset 链可稳定提供的 provenance 与 artifact 索引。
 * prototype 中的参数、风险预算和信号摘要尚无稳定 production 来源，因此本批次不将其提升为必填字段。
 * record 允许反序列化后的空值进入 validator，由 verifier 统一返回安全 reason code，而不是泄漏解析细节。
 */
public record StrategyArtifactManifest(
        String schemaVersion,
        String strategyVersionId,
        UUID datasetId,
        String evaluationId,
        List<ArtifactFile> artifactFiles,
        String artifactDigest,
        Instant generatedAt,
        String generatorVersion
) {

    public static final String SUPPORTED_SCHEMA_VERSION = "strategy-release-manifest.v1";

    public StrategyArtifactManifest {
        artifactFiles = artifactFiles == null ? null : List.copyOf(artifactFiles);
    }

    /**
     * 返回用于 fail-closed 表达“manifest 缺失”的空 contract；不会被 verifier 接受。
     */
    public static StrategyArtifactManifest empty() {
        return new StrategyArtifactManifest(null, null, null, null, null, null, null, null);
    }

    /**
     * ArtifactFile 只保存可审计 metadata；relativePath 必须由 trusted-root verifier 校验。
     */
    public record ArtifactFile(
            String logicalName,
            String relativePath,
            String sha256,
            long sizeBytes,
            String mediaType
    ) {
    }
}
