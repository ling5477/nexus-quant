package com.guidinglight.nexusquant.strategy.strategyrelease.artifact;

import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactManifest.ArtifactFile;

import java.io.IOException;

/**
 * 已完成最终 closure 的 verified snapshot 同步 consumer。
 *
 * <p>该 callback 只能由已返回 {@code SUPPORTED_RUNTIME_CLOSED} 的
 * {@link StableArtifactSnapshotResult#consumeVerified(VerifiedOpenStrategyArtifactConsumer)} 触发，因而 API shape
 * 不存在“最终 path/source/manifest closure 前执行 callback”的入口。输入来自有总量 hard cap 的私有内存 snapshot，
 * 不暴露可变 source handle 或 backing array；callback 返回、失败或部分读取后输入与全部 snapshot 都会失效并清零。</p>
 */
@FunctionalInterface
public interface VerifiedOpenStrategyArtifactConsumer {

    void consume(ArtifactFile descriptor, VerifiedOpenStrategyArtifactReader.VerifiedArtifactInput input)
            throws IOException;
}
