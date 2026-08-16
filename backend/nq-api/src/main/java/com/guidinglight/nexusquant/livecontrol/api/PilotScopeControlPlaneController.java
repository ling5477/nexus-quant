package com.guidinglight.nexusquant.livecontrol.api;

import com.guidinglight.nexusquant.auth.application.CurrentUserProfileService;
import com.guidinglight.nexusquant.auth.domain.AuthUserProfile;
import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.livecontrol.application.AuthenticatedLiveControlActor;
import com.guidinglight.nexusquant.livecontrol.application.PilotScopeControlPlane;
import com.guidinglight.nexusquant.livecontrol.domain.OperatorApproval;
import com.guidinglight.nexusquant.livecontrol.domain.PilotScopePreflightResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;

import java.util.Objects;
import java.util.UUID;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GateY-6D 最小 authenticated operator API。Controller 只做 typed mapping 与认证 identity 传递。
 */
@Validated
@RestController
@RequestMapping("/api/live-control/pilot-sessions")
public class PilotScopeControlPlaneController {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final GatewayAuthFacade gatewayAuthFacade;
    private final CurrentUserProfileService currentUserProfileService;
    private final PilotScopeControlPlane controlPlane;

    public PilotScopeControlPlaneController(
            GatewayAuthFacade gatewayAuthFacade,
            CurrentUserProfileService currentUserProfileService,
            PilotScopeControlPlane controlPlane
    ) {
        this.gatewayAuthFacade = Objects.requireNonNull(gatewayAuthFacade);
        this.currentUserProfileService = Objects.requireNonNull(currentUserProfileService);
        this.controlPlane = Objects.requireNonNull(controlPlane);
    }

    @PostMapping
    @Operation(
            summary = "物化 exact pilot scope 与完整 prerequisite facts",
            description = "只写 control-plane facts；不创建 ExecutionIntent，不调用交易所，不启动 worker。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public PilotScopeMaterializationResponse materialize(
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            @Valid @RequestBody PilotScopeMaterializationRequest request
    ) {
        String traceId = TraceIdContext.getOrCreate();
        return PilotScopeMaterializationResponse.from(controlPlane.materialize(
                actor(), request.toCommand(idempotencyKey, UUID.randomUUID().toString(), traceId)));
    }

    @PostMapping("/{sessionId}/approval")
    @Operation(
            summary = "由独立 LIVE_APPROVER 审批 exact pilot scope",
            description = "审批绑定 pilotScopeId + pilotScopeHash；禁止 creator 自审批。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public OperatorApproval approve(
            @PathVariable UUID sessionId,
            @Valid @RequestBody PilotScopeApprovalRequest request
    ) {
        TraceIdContext.getOrCreate();
        return controlPlane.approve(actor(), request.toCommand(sessionId));
    }

    @PostMapping("/{sessionId}/preflight")
    @Operation(
            summary = "运行 stored-fact pilot preflight",
            description = "只返回 eligibility fact；不创建订单、ExecutionIntent 或任何 exchange mutation。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public PilotScopePreflightResult preflight(@PathVariable UUID sessionId) {
        TraceIdContext.getOrCreate();
        return controlPlane.preflight(actor(), sessionId);
    }

    private AuthenticatedLiveControlActor actor() {
        var token = gatewayAuthFacade.currentUser()
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("authentication required"));
        AuthUserProfile profile = currentUserProfileService.findByUsername(token.username())
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("authentication required"));
        return new AuthenticatedLiveControlActor(profile.userId());
    }
}
