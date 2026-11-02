package com.guidinglight.nexusquant.strategy.api.dto;

import com.guidinglight.nexusquant.strategy.domain.StrategyRunPage;

import java.util.List;

/** 有界运行摘要与总数属于同次读取；接口不冒充已具备运行级账务或风险关联。 */
public record StrategyRunPageResponse(
        List<StrategyRunSummaryResponse> items, long total, int limit, int offset) {
    public static StrategyRunPageResponse from(StrategyRunPage page) {
        return new StrategyRunPageResponse(
                page.items().stream().map(StrategyRunSummaryResponse::from).toList(),
                page.total(),
                page.limit(),
                page.offset());
    }
}
