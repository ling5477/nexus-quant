package com.guidinglight.nexusquant.strategy.api.web;

import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.strategy.api.dto.StrategyRunBusinessEventResponse;
import com.guidinglight.nexusquant.strategy.api.dto.StrategyRunLedgerEntryResponse;
import com.guidinglight.nexusquant.strategy.api.dto.StrategyRunRiskEventResponse;
import com.guidinglight.nexusquant.strategy.application.StrategyRunRelationService;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunRelatedPage;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunRelatedQuery;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 沿用运行读取范围的稳定关联视图，不扩大成账户级查询或任何Risk/Ledger写入。 */
@RestController
public class StrategyRunRelationController {
    private final StrategyRunRelationService service;

    private final GatewayAuthFacade auth;
    private final CurrentUserProfileService profiles;

    public StrategyRunRelationController(
            StrategyRunRelationService service,
            GatewayAuthFacade auth,
            CurrentUserProfileService profiles) {
        this.service = service;
        this.auth = auth;
        this.profiles = profiles;
    }

    /** 所有者只来自已认证主体及数据库资料，URL和请求参数不能提供另一个owner。 */
    private long owner() {
        var current =
                auth.currentUser()
                        .orElseThrow(
                                () ->
                                        new AuthenticationCredentialsNotFoundException(
                                                "authentication required"));
        return profiles.findByUsername(current.username())
                .map(profile -> profile.userId())
                .orElseThrow(
                        () ->
                                new AuthenticationCredentialsNotFoundException(
                                        "authentication required"));
    }

    @GetMapping("/api/strategy-runs/{runId}/business-events/page")
    @Operation(summary = "分页查询可确定归属的运行业务事件", security = @SecurityRequirement(name = "bearerAuth"))
    public StrategyRunRelatedPage<StrategyRunBusinessEventResponse> businessEvents(
            @PathVariable String runId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        var page =
                service.businessEvents(owner(), new StrategyRunRelatedQuery(runId, limit, offset));
        return new StrategyRunRelatedPage<>(
                page.strategyRunId(),
                page.items().stream().map(StrategyRunBusinessEventResponse::from).toList(),
                page.total(),
                page.limit(),
                page.offset());
    }

    @GetMapping("/api/strategy-runs/{runId}/risk-events/page")
    @Operation(summary = "分页查询运行订单风控事件", security = @SecurityRequirement(name = "bearerAuth"))
    public StrategyRunRelatedPage<StrategyRunRiskEventResponse> riskEvents(
            @PathVariable String runId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        var page = service.riskEvents(owner(), new StrategyRunRelatedQuery(runId, limit, offset));
        return new StrategyRunRelatedPage<>(
                page.strategyRunId(),
                page.items().stream().map(StrategyRunRiskEventResponse::from).toList(),
                page.total(),
                page.limit(),
                page.offset());
    }

    @GetMapping("/api/strategy-runs/{runId}/ledger-entries/page")
    @Operation(summary = "分页查询运行成交记账分录", security = @SecurityRequirement(name = "bearerAuth"))
    public StrategyRunRelatedPage<StrategyRunLedgerEntryResponse> ledgerEntries(
            @PathVariable String runId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        var page =
                service.ledgerEntries(owner(), new StrategyRunRelatedQuery(runId, limit, offset));
        return new StrategyRunRelatedPage<>(
                page.strategyRunId(),
                page.items().stream().map(StrategyRunLedgerEntryResponse::from).toList(),
                page.total(),
                page.limit(),
                page.offset());
    }
}
