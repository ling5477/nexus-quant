package com.guidinglight.nexusquant.strategy.api.dto;

import com.guidinglight.nexusquant.strategy.domain.StrategyRunOverview;

import java.util.List;
import java.util.Map;

/** 概览不附带无界子数组；明确保留目前无法稳定按运行关联的能力缺口。 */
public record StrategyRunOverviewResponse(
        StrategyRunSummaryResponse run,
        long orderCount,
        long tradeCount,
        String traceId,
        List<String> unavailableRelationships,
        Map<String, String> relationshipScopes) {
    public static StrategyRunOverviewResponse from(StrategyRunOverview overview) {
        return new StrategyRunOverviewResponse(
                StrategyRunSummaryResponse.from(overview.run()),
                overview.orderCount(),
                overview.tradeCount(),
                overview.traceId(),
                List.of("UNSCOPED_SYSTEM_EVENTS"),
                Map.of(
                        "risk",
                        "ORDER_EVENTS",
                        "ledger",
                        "TRADE_POSTINGS",
                        "events",
                        "CANONICAL_BUSINESS_EVENTS"));
    }
}
