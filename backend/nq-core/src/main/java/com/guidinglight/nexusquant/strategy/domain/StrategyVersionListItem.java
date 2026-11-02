package com.guidinglight.nexusquant.strategy.domain;

import java.time.Instant;

/** 目录只读取身份和版本元信息；完整冻结快照经原精确详情读取，避免翻页重复传输大JSON。 */
public record StrategyVersionListItem(
        String strategyVersionId,
        String strategyCode,
        int version,
        String versionName,
        StrategyVersionStatus status,
        String checksum,
        String createdBy,
        Instant createdAt,
        Instant updatedAt) {}
