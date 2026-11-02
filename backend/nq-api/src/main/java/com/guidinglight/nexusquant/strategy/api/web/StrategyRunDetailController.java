package com.guidinglight.nexusquant.strategy.api.web;

import com.guidinglight.nexusquant.strategy.api.dto.StrategyRunOrderItemResponse;
import com.guidinglight.nexusquant.strategy.api.dto.StrategyRunOverviewResponse;
import com.guidinglight.nexusquant.strategy.api.dto.StrategyRunTradeItemResponse;
import com.guidinglight.nexusquant.strategy.application.StrategyRunDetailService;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunRelatedPage;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunRelatedQuery;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 复用既有运行读取权限；新概览和子页不包含任何交易mutation，旧聚合详情保持兼容。 */
@RestController
public class StrategyRunDetailController {
    private final StrategyRunDetailService service;

    public StrategyRunDetailController(StrategyRunDetailService service) {
        this.service = service;
    }

    @GetMapping("/api/strategy-runs/{runId}/overview")
    @Operation(summary = "查询精确运行概览", security = @SecurityRequirement(name = "bearerAuth"))
    public StrategyRunOverviewResponse overview(@PathVariable String runId) {
        return StrategyRunOverviewResponse.from(service.overview(runId));
    }

    @GetMapping("/api/strategy-runs/{runId}/orders/page")
    @Operation(summary = "分页查询运行关联订单", security = @SecurityRequirement(name = "bearerAuth"))
    public StrategyRunRelatedPage<StrategyRunOrderItemResponse> orders(
            @PathVariable String runId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        var page = service.orders(new StrategyRunRelatedQuery(runId, limit, offset));
        return new StrategyRunRelatedPage<>(
                page.strategyRunId(),
                page.items().stream().map(StrategyRunOrderItemResponse::from).toList(),
                page.total(),
                page.limit(),
                page.offset());
    }

    @GetMapping("/api/strategy-runs/{runId}/trades/page")
    @Operation(summary = "分页查询运行关联成交", security = @SecurityRequirement(name = "bearerAuth"))
    public StrategyRunRelatedPage<StrategyRunTradeItemResponse> trades(
            @PathVariable String runId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        var page = service.trades(new StrategyRunRelatedQuery(runId, limit, offset));
        return new StrategyRunRelatedPage<>(
                page.strategyRunId(),
                page.items().stream().map(StrategyRunTradeItemResponse::from).toList(),
                page.total(),
                page.limit(),
                page.offset());
    }
}
