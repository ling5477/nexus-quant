package com.guidinglight.nexusquant.paper.api.web;

import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.scheduler.paper.StrategySimAnalysisQuery;
import com.guidinglight.nexusquant.scheduler.paper.StrategySimPerformanceQuery;
import com.guidinglight.nexusquant.scheduler.paper.StrategySimForwardEvaluationQuery;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

/** 资产与复盘共享同一实例身份和经济读取，入口不执行采样、撮合或状态迁移。 */
@Validated
@RestController
@ConditionalOnProperty(prefix = "nq.strategy-sim", name = "enabled", havingValue = "true")
@RequestMapping("/api/paper-trading/strategy-sim/analysis/runs")
public class StrategySimAnalysisController {
    private final GatewayAuthFacade auth;
    private final CurrentUserProfileService profiles;
    private final StrategySimAnalysisQuery query;

    public StrategySimAnalysisController(
            GatewayAuthFacade auth,
            CurrentUserProfileService profiles,
            StrategySimAnalysisQuery query) {
        this.auth = auth;
        this.profiles = profiles;
        this.query = query;
    }

    @GetMapping
    public StrategySimAnalysisQuery.Catalog list(@RequestParam(defaultValue = "0") int offset) {
        if (offset < 0 || offset > 950 || offset % 50 != 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_SIM_ANALYSIS_PAGE");
        return query.list(owner(), offset);
    }

    @GetMapping("/{paperRunId}")
    public StrategySimAnalysisQuery.Analysis read(
            @PathVariable @NotBlank @Size(max = 128) String paperRunId) {
        return query.read(owner(), paperRunId)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "SIM_RUN_NOT_FOUND"));
    }

    private long owner() {
        var user =
                auth.currentUser()
                        .orElseThrow(
                                () ->
                                        new AuthenticationCredentialsNotFoundException(
                                                "authentication required"));
        return profiles.findByUsername(user.username())
                .orElseThrow(
                        () ->
                                new AuthenticationCredentialsNotFoundException(
                                        "authentication required"))
                .userId();
    }

    /** 模型回放时间与真实成交时间分别声明；查询区间不会触发推进、撮合或历史补写。 */
    @GetMapping("/{paperRunId}/performance")
    public StrategySimPerformanceQuery.View performance(
            @PathVariable @NotBlank @Size(max = 128) String paperRunId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return query.readPerformance(owner(), paperRunId, from, to)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "SIM_RUN_NOT_FOUND"));
    }

    /** 观察区间是左闭右开；仅组织已有事实，不推进SIM、采样或生成评价写记录。 */
    @GetMapping("/{paperRunId}/forward-evaluation")
    public StrategySimForwardEvaluationQuery.Evaluation forwardEvaluation(
            @PathVariable @NotBlank @Size(max = 128) String paperRunId,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) Instant asOf) {
        return query.readForwardEvaluation(owner(), paperRunId, from, to, asOf)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "SIM_RUN_NOT_FOUND"));
    }
}
