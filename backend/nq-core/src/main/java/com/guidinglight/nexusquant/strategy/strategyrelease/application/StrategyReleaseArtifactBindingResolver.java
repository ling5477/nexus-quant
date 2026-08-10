package com.guidinglight.nexusquant.strategy.strategyrelease.application;

import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactManifest;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactVerificationResult.FindingCode;

import java.nio.file.Path;
import java.util.Objects;

/**
 * 将服务端持久化的 opaque storage key 解析为受控 artifact-set 与 manifest。
 *
 * <p>实现只能使用服务端配置与持久化 key，不得从 request、digest、publishRecordId 或工作目录推导路径。
 */
public interface StrategyReleaseArtifactBindingResolver {

    ArtifactBindingResolution resolve(String artifactStorageKey, String manifestStorageKey);

    /** 内部解析结果；Path 与 manifest 不得进入 Controller、DTO 或日志。 */
    record ArtifactBindingResolution(
            Path artifactRoot,
            StrategyArtifactManifest manifest,
            FindingCode reasonCode,
            String safeStorageIdentifier
    ) {
        public ArtifactBindingResolution {
            if (reasonCode == null) {
                Objects.requireNonNull(artifactRoot, "resolved artifactRoot must not be null");
                Objects.requireNonNull(manifest, "resolved manifest must not be null");
            } else if (artifactRoot != null || manifest != null) {
                throw new IllegalArgumentException("rejected resolution must not expose internal locations or manifest");
            }
        }

        public static ArtifactBindingResolution resolved(Path artifactRoot, StrategyArtifactManifest manifest) {
            return new ArtifactBindingResolution(
                    Objects.requireNonNull(artifactRoot, "artifactRoot must not be null"),
                    Objects.requireNonNull(manifest, "manifest must not be null"),
                    null,
                    null
            );
        }

        public static ArtifactBindingResolution rejected(FindingCode reasonCode, String safeStorageIdentifier) {
            return new ArtifactBindingResolution(
                    null,
                    null,
                    Objects.requireNonNull(reasonCode, "reasonCode must not be null"),
                    safeStorageIdentifier
            );
        }

        public boolean resolved() {
            return reasonCode == null;
        }
    }
}
