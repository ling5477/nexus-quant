package com.guidinglight.nexusquant.strategy.domain;

import java.time.Instant;

/**
 * StrategyVersion 表示 GateI-1 的策略版本事实。
 *
 * Why:
 * `strategy_definitions` 记录当前策略定义和启停状态，但后续回测、发布和 Paper run
 * 需要引用不可变快照。该模型把版本号、参数快照、配置快照和 checksum 固化为可追溯输入。
 */
public record StrategyVersion(
        String strategyVersionId,
        String strategyCode,
        int version,
        String versionName,
        StrategyVersionStatus status,
        String paramSnapshotJson,
        String configSnapshotJson,
        String sourceSnapshotJson,
        String checksum,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {
}
