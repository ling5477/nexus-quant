package com.guidinglight.nexusquant.paper.api.web;

import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.paper.api.dto.EmergencyStopEventResponse;
import com.guidinglight.nexusquant.paper.api.dto.EmergencyStopRequestBody;
import com.guidinglight.nexusquant.research.application.api.paper.PaperTradingApiService;
import com.guidinglight.nexusquant.research.application.paper.service.PaperEmergencyStopOperationService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/** 沿用原停机URL并要求稳定编号；请求操作者只能取认证主体，不能使用表单中的代填身份。 */
@Validated
@RestController
@ConditionalOnBean(PaperTradingApiService.class)
@RequestMapping("/api/paper-trading/runs/{paperRunId}")
public class PaperEmergencyStopOperationController {
    private final PaperEmergencyStopOperationService operations;
    private final GatewayAuthFacade auth;
    private final CurrentUserProfileService users;

    public PaperEmergencyStopOperationController(
            PaperEmergencyStopOperationService operations,
            GatewayAuthFacade auth,
            CurrentUserProfileService users) {
        this.operations = operations;
        this.auth = auth;
        this.users = users;
    }

    @PostMapping("/emergency-stop")
    public EmergencyStopEventResponse submit(
            @PathVariable @NotBlank @Size(max = 128) String paperRunId,
            @RequestHeader("Idempotency-Key") UUID operationId,
            @Valid @RequestBody EmergencyStopRequestBody request) {
        Actor actor = actor();
        try {
            return EmergencyStopEventResponse.from(
                    operations.submit(
                            operationId,
                            actor.owner(),
                            actor.username(),
                            paperRunId,
                            request.triggerType(),
                            request.reason()));
        } catch (IllegalArgumentException missing) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "PAPER_EMERGENCY_SOURCE_UNAVAILABLE", missing);
        }
    }

    /** 原结果查询没有新停机副作用；404仍须在客户端保持未确认。 */
    @GetMapping("/emergency-stop-operations/{operationId}")
    public EmergencyStopEventResponse find(
            @PathVariable @NotBlank @Size(max = 128) String paperRunId,
            @PathVariable UUID operationId) {
        Actor actor = actor();
        return EmergencyStopEventResponse.from(
                operations
                        .find(operationId, actor.owner(), paperRunId)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "PAPER_EMERGENCY_ORIGINAL_RESULT_NOT_RECORDED")));
    }

    private Actor actor() {
        String username =
                auth.currentUser()
                        .orElseThrow(
                                () ->
                                        new AuthenticationCredentialsNotFoundException(
                                                "authentication required"))
                        .username();
        var profile =
                users.findByUsername(username)
                        .orElseThrow(
                                () ->
                                        new AuthenticationCredentialsNotFoundException(
                                                "authentication required"));
        if (!profile.enabled() || profile.userId() == null || profile.userId() <= 0)
            throw new AuthenticationCredentialsNotFoundException("authentication required");
        if (!profile.roles().contains("ADMIN") && !profile.roles().contains("OPERATOR"))
            throw new AccessDeniedException("operator role required");
        return new Actor(profile.userId(), profile.username());
    }

    private record Actor(long owner, String username) {}
}
