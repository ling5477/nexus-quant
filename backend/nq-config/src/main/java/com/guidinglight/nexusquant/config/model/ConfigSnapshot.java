package com.guidinglight.nexusquant.config.model;

import java.time.Instant;
import java.util.Map;

/**
 * ConfigSnapshot 描述策略/风控参数快照。
 *
 * Why:
 * docs/MODULES.md 要求 nq-config 负责参数版本化与快照口径，
 * 骨架阶段先固定快照模型，避免后续各模块自行定义。
 */
public record ConfigSnapshot(
        String snapshotId,
        String scope,
        String scopeId,
        Map<String, String> values,
        Instant createdAt,
        String traceId
) {
}
