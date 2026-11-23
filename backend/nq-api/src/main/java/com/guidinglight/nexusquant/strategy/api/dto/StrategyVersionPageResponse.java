package com.guidinglight.nexusquant.strategy.api.dto;

import com.guidinglight.nexusquant.strategy.domain.StrategyVersionPage;

import java.util.List;

/** 窗口与总数明确返回，前端不再从一个被截断的版本数组推测完整目录。 */
public record StrategyVersionPageResponse(
        List<StrategyVersionListItemResponse> items, long total, int limit, int offset) {
    public static StrategyVersionPageResponse from(StrategyVersionPage page) {
        return new StrategyVersionPageResponse(
                page.items().stream().map(StrategyVersionListItemResponse::from).toList(),
                page.total(),
                page.limit(),
                page.offset());
    }
}
