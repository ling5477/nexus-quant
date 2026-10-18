package com.guidinglight.nexusquant.marketdata.api.web;

import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.marketdata.api.dto.CreateMarketdataIngestionJobRequest;
import com.guidinglight.nexusquant.marketdata.api.dto.MarketdataIngestionJobResponse;
import com.guidinglight.nexusquant.marketdata.api.dto.MarketdataIngestionRunResponse;
import com.guidinglight.nexusquant.marketdata.application.command.CreateMarketdataIngestionJobCommand;
import com.guidinglight.nexusquant.marketdata.application.service.MarketdataIngestionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/** 稳定编号直接复用正式任务和运行主键；读取沿用共享行情范围，写入仍须当前操作员角色。 */
@RestController
@RequestMapping("/api/marketdata/ingestion-jobs")
public class MarketdataIngestionOperationController {
    private final MarketdataIngestionService service;
    private final GatewayAuthFacade auth;
    private final CurrentUserProfileService profiles;

    public MarketdataIngestionOperationController(
            MarketdataIngestionService service,
            GatewayAuthFacade auth,
            CurrentUserProfileService profiles) {
        this.service = service;
        this.auth = auth;
        this.profiles = profiles;
    }

    @PostMapping("/{jobId}")
    @Operation(summary = "按原编号创建行情采集任务或返回原结果", security = @SecurityRequirement(name = "bearerAuth"))
    public MarketdataIngestionJobResponse create(
            @PathVariable UUID jobId,
            @Valid @RequestBody CreateMarketdataIngestionJobRequest request) {
        return MarketdataIngestionJobResponse.from(
                service.createJob(
                        jobId,
                        new CreateMarketdataIngestionJobCommand(
                                request.exchangeCode(),
                                request.marketType(),
                                request.symbol(),
                                request.interval(),
                                request.startTime(),
                                request.endTime(),
                                operator())));
    }

    @GetMapping("/{jobId}/creation-result")
    @Operation(
            summary = "查询原创建者的行情采集任务结果",
            description = "404表示当前未读到本人已提交的原结果；不能据此换号创建。任务当前状态另按共享目录读取。",
            security = @SecurityRequirement(name = "bearerAuth"))
    public MarketdataIngestionJobResponse findCreation(@PathVariable UUID jobId) {
        String actor =
                auth.currentUser()
                        .orElseThrow(
                                () ->
                                        new AuthenticationCredentialsNotFoundException(
                                                "authentication required"))
                        .username();
        return service.findJob(jobId)
                .filter(job -> job.createdBy().equals(actor))
                .map(MarketdataIngestionJobResponse::from)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "original ingestion creation result is not recorded"));
    }

    @PostMapping("/{jobId}/runs/{runId}")
    @Operation(summary = "按原执行编号采集一次或返回原结果", security = @SecurityRequirement(name = "bearerAuth"))
    public MarketdataIngestionRunResponse execute(
            @PathVariable UUID jobId, @PathVariable UUID runId) {
        operator();
        return MarketdataIngestionRunResponse.from(service.runOnce(jobId, runId));
    }

    @GetMapping("/{jobId}/runs/{runId}")
    @Operation(
            summary = "查询原采集执行结果",
            description = "404只表示当前没有已提交结果，不证明原请求从未执行；保留原编号查询或继续。",
            security = @SecurityRequirement(name = "bearerAuth"))
    public MarketdataIngestionRunResponse find(@PathVariable UUID jobId, @PathVariable UUID runId) {
        auth.currentUser()
                .orElseThrow(
                        () ->
                                new AuthenticationCredentialsNotFoundException(
                                        "authentication required"));
        return service.findRun(jobId, runId)
                .map(MarketdataIngestionRunResponse::from)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "original ingestion result is not recorded"));
    }

    private String operator() {
        var claims =
                auth.currentUser()
                        .orElseThrow(
                                () ->
                                        new AuthenticationCredentialsNotFoundException(
                                                "authentication required"));
        var profile =
                profiles.findByUsername(claims.username())
                        .orElseThrow(
                                () ->
                                        new AuthenticationCredentialsNotFoundException(
                                                "authentication required"));
        if (!profile.enabled() || profile.userId() == null || profile.userId() <= 0)
            throw new AuthenticationCredentialsNotFoundException("authentication required");
        if (!profile.roles().contains("ADMIN") && !profile.roles().contains("OPERATOR"))
            throw new AccessDeniedException("operator role required");
        return profile.username();
    }
}
