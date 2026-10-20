package com.guidinglight.nexusquant.strategy.api.web;

import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.auth.domain.AuthUserProfile;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.strategy.api.dto.ShadowRunCreationResultResponse;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.model.ShadowRunMaterializationActor;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.service.ShadowRunCreationResultQueryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

/**
 * 原 Shadow 创建命令的结果查询；编号继续使用请求头，避免进入 URL、历史记录和查询参数。
 */
@RestController
@RequestMapping("/api/strategy-releases")
public class ShadowRunCreationResultController {

    private final GatewayAuthFacade gatewayAuthFacade;
    private final CurrentUserProfileService profileService;
    private final ShadowRunCreationResultQueryService queryService;

    public ShadowRunCreationResultController(
            GatewayAuthFacade gatewayAuthFacade,
            CurrentUserProfileService profileService,
            ShadowRunCreationResultQueryService queryService) {
        this.gatewayAuthFacade =
                Objects.requireNonNull(gatewayAuthFacade, "gatewayAuthFacade must not be null");
        this.profileService =
                Objects.requireNonNull(profileService, "profileService must not be null");
        this.queryService = Objects.requireNonNull(queryService, "queryService must not be null");
    }

    /**
     * 仅核实原创建，不读取新的准入决策；404 保留未知结果，不能据此另起命令。
     */
    @GetMapping("/{publishRecordId}/shadow-run-creation-result")
    @Operation(summary = "查询原 Shadow 创建命令结果", security = @SecurityRequirement(name = "bearerAuth"))
    public ShadowRunCreationResultResponse query(
            @PathVariable String publishRecordId,
            @RequestHeader(
                            name =
                                    StrategyReleaseShadowRunMaterializationController
                                            .IDEMPOTENCY_KEY_HEADER,
                            required = false)
                    String commandIdentity) {
        var token =
                gatewayAuthFacade
                        .currentUser()
                        .orElseThrow(
                                () ->
                                        new AuthenticationCredentialsNotFoundException(
                                                "authentication required"));
        AuthUserProfile profile =
                profileService
                        .findByUsername(token.username())
                        .orElseThrow(
                                () ->
                                        new AuthenticationCredentialsNotFoundException(
                                                "authentication required"));
        return queryService
                .query(
                        publishRecordId,
                        commandIdentity,
                        new ShadowRunMaterializationActor(profile.userId(), profile.roles()))
                .map(ShadowRunCreationResultResponse::from)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Shadow creation result not found for current actor and release"));
    }
}
