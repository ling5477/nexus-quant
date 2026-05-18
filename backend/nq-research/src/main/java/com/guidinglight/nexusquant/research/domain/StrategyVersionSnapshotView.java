package com.guidinglight.nexusquant.research.domain;

/**
 * StrategyVersionSnapshotView 是 research/publish 链路读取策略版本快照的只读视图。
 *
 * Why:
 * `nq-research` 不依赖 `nq-core`，但发布记录需要固化 strategy version 信息。
 * 通过 research 端口读取最小快照，可以保持模块边界，同时避免 publish service 直接查询 SQL。
 */
public record StrategyVersionSnapshotView(
        String strategyVersionId,
        String strategyCode,
        int version,
        String versionName,
        String status,
        String paramSnapshotJson,
        String configSnapshotJson,
        String sourceSnapshotJson,
        String checksum
) {
}
