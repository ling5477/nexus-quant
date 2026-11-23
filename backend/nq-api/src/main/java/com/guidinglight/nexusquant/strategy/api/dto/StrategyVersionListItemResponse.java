package com.guidinglight.nexusquant.strategy.api.dto;

import com.guidinglight.nexusquant.strategy.domain.StrategyVersionListItem;

import java.time.Instant;

/** 列表元信息不构造空快照；用户展开或选中后仍读取原完整版本详情。 */
public record StrategyVersionListItemResponse(
        String strategyVersionId,
        String strategyCode,
        int version,
        String versionName,
        String status,
        String checksum,
        String createdBy,
        Instant createdAt,
        Instant updatedAt) {
    public static StrategyVersionListItemResponse from(StrategyVersionListItem item) {
        return new StrategyVersionListItemResponse(
                item.strategyVersionId(),
                item.strategyCode(),
                item.version(),
                item.versionName(),
                item.status().name(),
                item.checksum(),
                item.createdBy(),
                item.createdAt(),
                item.updatedAt());
    }
}
