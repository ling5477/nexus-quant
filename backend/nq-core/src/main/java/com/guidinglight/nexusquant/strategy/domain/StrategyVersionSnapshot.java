package com.guidinglight.nexusquant.strategy.domain;

/**
 * StrategyVersionSnapshot 是发布记录固化策略版本时使用的轻量快照。
 *
 * Why:
 * 发布记录需要保存版本当时的输入状态，后续 strategy version 被归档或重新创建时，
 * 历史发布仍必须能独立解释其策略参数、配置和 checksum。
 */
public record StrategyVersionSnapshot(
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
