package com.guidinglight.nexusquant.strategy.strategyrelease.artifact;

/**
 * Artifact verification 的资源上限。
 *
 * <p>所有值由配置注入；默认值只在 Spring wiring 处声明，测试可使用更小的显式上限验证 fail-closed 行为。
 */
public record StrategyArtifactVerificationPolicy(
        int maxFileCount,
        long maxFileSizeBytes,
        long maxTotalSizeBytes
) {
    public StrategyArtifactVerificationPolicy {
        if (maxFileCount <= 0) {
            throw new IllegalArgumentException("maxFileCount must be positive");
        }
        if (maxFileSizeBytes <= 0) {
            throw new IllegalArgumentException("maxFileSizeBytes must be positive");
        }
        if (maxTotalSizeBytes < maxFileSizeBytes) {
            throw new IllegalArgumentException("maxTotalSizeBytes must be at least maxFileSizeBytes");
        }
    }
}
