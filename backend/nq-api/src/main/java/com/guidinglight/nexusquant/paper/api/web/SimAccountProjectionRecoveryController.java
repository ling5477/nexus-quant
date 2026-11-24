package com.guidinglight.nexusquant.paper.api.web;

import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.scheduler.paper.SimAccountProjectionRecoveryService;
import com.guidinglight.nexusquant.scheduler.paper.SimAccountProjectionRecoveryService.Preview;
import com.guidinglight.nexusquant.scheduler.paper.SimAccountProjectionRecoveryService.Receipt;
import com.guidinglight.nexusquant.scheduler.paper.SimAccountProjectionRecoveryService.RecheckRequest;
import com.guidinglight.nexusquant.scheduler.paper.SimAccountProjectionRecoveryService.RecoveryRequest;
import com.guidinglight.nexusquant.scheduler.paper.SimAccountProjectionRecoveryService.ScopeNotFound;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Validated
@Profile("public-marketdata-manual")
@ConditionalOnProperty(prefix = "nq.strategy-sim", name = "enabled", havingValue = "true")
@RequestMapping("/api/paper-trading/strategy-sim/runs/{run}/projection-recovery")
public class SimAccountProjectionRecoveryController {
    private final GatewayAuthFacade auth;
    private final SimAccountProjectionRecoveryService recovery;

    public SimAccountProjectionRecoveryController(GatewayAuthFacade auth, SimAccountProjectionRecoveryService recovery) {
        this.auth = auth;
        this.recovery = recovery;
    }

    @GetMapping
    public Preview preview(@PathVariable @NotBlank @Size(max = 128) String run) {
        return guarded(() -> recovery.preview(run, actor()));
    }

    @PostMapping
    public Receipt recover(@PathVariable @NotBlank @Size(max = 128) String run, @RequestBody RecoveryRequest request) {
        return guarded(() -> recovery.recover(run, actor(), TraceIdContext.getOrCreate(), request));
    }

    @PostMapping("/finalization")
    public Receipt recheck(@PathVariable @NotBlank @Size(max = 128) String run, @RequestBody RecheckRequest request) {
        return guarded(() -> recovery.recheck(run, actor(), TraceIdContext.getOrCreate(), request));
    }

    @GetMapping("/operations/{operationId}")
    public Receipt receipt(@PathVariable @NotBlank @Size(max = 128) String run, @PathVariable UUID operationId) {
        return guarded(() -> recovery.receipt(run, actor(), operationId).orElseThrow(ScopeNotFound::new));
    }

    private static <T> T guarded(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (ScopeNotFound missing) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "SIM_PROJECTION_SCOPE_NOT_FOUND");
        } catch (IllegalStateException conflict) {
            if (conflict.getMessage() != null && conflict.getMessage().matches("SIM_[A-Z0-9_]+")) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, conflict.getMessage());
            }
            throw conflict;
        }
    }

    private String actor() {
        return auth.currentUser().orElseThrow(() ->
                new AuthenticationCredentialsNotFoundException("authentication required")).username();
    }
}
