package com.guidinglight.nexusquant.strategy.api.web;

import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.auth.domain.AuthUserProfile;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.strategy.api.dto.StrategyScheduleCreationRequestBody;
import com.guidinglight.nexusquant.strategy.api.dto.StrategyScheduleCreationResponse;
import com.guidinglight.nexusquant.strategy.application.StrategyScheduleCreationService;
import com.guidinglight.nexusquant.strategy.application.command.StrategyScheduleCreateRequest;

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

/** 原创建者的恢复入口与创建命令保持同一角色边界；其他管理员没有回执旁路权限。 */
@RestController
@RequestMapping("/api/strategy-schedule-creations")
public class StrategyScheduleCreationController {
    private final StrategyScheduleCreationService creations;
    private final GatewayAuthFacade auth;
    private final CurrentUserProfileService profiles;

    public StrategyScheduleCreationController(
            StrategyScheduleCreationService creations,
            GatewayAuthFacade auth,
            CurrentUserProfileService profiles) {
        this.creations = creations;
        this.auth = auth;
        this.profiles = profiles;
    }

    @PostMapping
    @Operation(summary = "按原编号创建计划或读取同输入原结果", security = @SecurityRequirement(name = "bearerAuth"))
    public StrategyScheduleCreationResponse submit(
            @Valid @RequestBody StrategyScheduleCreationRequestBody body) {
        var profile = profile();
        var input = body.request();
        return StrategyScheduleCreationResponse.from(
                creations.submit(
                        body.creationId(),
                        profile.userId(),
                        profile.username(),
                        new StrategyScheduleCreateRequest(
                                input.strategyId(),
                                input.scheduleType(),
                                input.cronExpr(),
                                input.timezone(),
                                input.enabled(),
                                input.windowConfig(),
                                input.dedupScope()),
                        TraceIdContext.getOrCreate()));
    }

    @GetMapping("/{creationId}")
    @Operation(
            summary = "查询本人原创建回执",
            description = "404仅表示当前未读到本人的已提交回执，不证明原请求从未执行。",
            security = @SecurityRequirement(name = "bearerAuth"))
    public StrategyScheduleCreationResponse find(@PathVariable UUID creationId) {
        var profile = profile();
        return creations
                .find(creationId, profile.userId())
                .map(StrategyScheduleCreationResponse::from)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "original schedule creation result is not recorded"));
    }

    private AuthUserProfile profile() {
        var actor =
                auth.currentUser()
                        .orElseThrow(
                                () ->
                                        new AuthenticationCredentialsNotFoundException(
                                                "authentication required"));
        var profile =
                profiles.findByUsername(actor.username())
                        .orElseThrow(
                                () ->
                                        new AuthenticationCredentialsNotFoundException(
                                                "authentication required"));
        if (profile.userId() == null || profile.userId() <= 0 || !profile.enabled())
            throw new AuthenticationCredentialsNotFoundException("authentication required");
        if (!profile.roles().contains("ADMIN") && !profile.roles().contains("OPERATOR"))
            throw new AccessDeniedException("operator role required");
        return profile;
    }
}
