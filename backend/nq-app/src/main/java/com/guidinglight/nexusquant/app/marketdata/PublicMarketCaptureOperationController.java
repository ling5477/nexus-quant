package com.guidinglight.nexusquant.app.marketdata;

import com.guidinglight.nexusquant.app.marketdata.PublicMarketCaptureOperationService.OperationView;
import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.auth.domain.AuthUserProfile;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/** 恢复查询独立于来源启用开关；身份来自当前认证用户，提交仍要求当前有效操作员角色。 */
@RestController
@RequestMapping("/api/marketdata/public-captures")
public final class PublicMarketCaptureOperationController {
    private final PublicMarketCaptureOperationService operations;
    private final GatewayAuthFacade auth;
    private final CurrentUserProfileService profiles;

    public PublicMarketCaptureOperationController(
            PublicMarketCaptureOperationService operations,
            GatewayAuthFacade auth,
            CurrentUserProfileService profiles) {
        this.operations = operations;
        this.auth = auth;
        this.profiles = profiles;
    }

    @PostMapping("/{captureId}")
    public OperationView submit(@PathVariable UUID captureId, @RequestBody CaptureRequest request) {
        var actor = profile();
        if (!actor.roles().contains("ADMIN") && !actor.roles().contains("OPERATOR")) {
            throw new AccessDeniedException("operator role required");
        }
        return operations.submit(
                captureId, request.start(), request.end(), actor.userId(), actor.username());
    }

    @GetMapping("/{captureId}")
    public ResponseEntity<OperationView> find(@PathVariable UUID captureId) {
        return ResponseEntity.of(operations.find(captureId, profile().userId()));
    }

    private AuthUserProfile profile() {
        var result =
                auth.currentUser()
                        .flatMap(claims -> profiles.findByUsername(claims.username()))
                        .orElseThrow(
                                () ->
                                        new AuthenticationCredentialsNotFoundException(
                                                "authentication required"));
        if (result.userId() == null
                || result.userId() <= 0
                || !result.enabled()
                || result.mustChangePassword()) {
            throw new AuthenticationCredentialsNotFoundException("authentication required");
        }
        return result;
    }

    public record CaptureRequest(Instant start, Instant end) {}
}
