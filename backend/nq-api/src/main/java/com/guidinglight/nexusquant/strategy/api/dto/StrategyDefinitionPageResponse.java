package com.guidinglight.nexusquant.strategy.api.dto;

import com.guidinglight.nexusquant.strategy.domain.StrategyDefinitionPage;

import java.util.List;

/** 分页只改变读取窗口，单行继续使用原正式策略响应合同。 */
public record StrategyDefinitionPageResponse(
        List<StrategyDefinitionResponse> items, long total, int limit, int offset) {
    public static StrategyDefinitionPageResponse from(StrategyDefinitionPage page) {
        return new StrategyDefinitionPageResponse(
                page.items().stream().map(StrategyDefinitionResponse::from).toList(),
                page.total(),
                page.limit(),
                page.offset());
    }
}
