package com.guidinglight.nexusquant.app.research;

import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import java.util.UUID;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 用户显式选择的身份只作为查询参数；服务端重新验证完整血缘。 */
@RestController
@RequestMapping("/api/strategy-experiments/validations/{validationId}/sim-comparison")
public class ResearchSimComparisonController {
    private final ResearchSimComparisonService service;
    private final GatewayAuthFacade auth;
    private final CurrentUserProfileService profiles;

    public ResearchSimComparisonController(ResearchSimComparisonService service,GatewayAuthFacade auth,CurrentUserProfileService profiles) {
        this.service=service; this.auth=auth; this.profiles=profiles;
    }

    @GetMapping
    public ResearchSimComparisonService.Comparison read(@PathVariable UUID validationId,@RequestParam UUID selectedTrialId,
            @RequestParam String strategyVersionId,@RequestParam(required=false) String publishId,@RequestParam(required=false) String paperRunId) {
        var actor=auth.currentUser().flatMap(c->profiles.findByUsername(c.username()))
                .orElseThrow(()->new AuthenticationCredentialsNotFoundException("authentication required"));
        if(actor.userId()==null || !actor.enabled() || actor.mustChangePassword())
            throw new AuthenticationCredentialsNotFoundException("authentication required");
        return service.compare(validationId,selectedTrialId,strategyVersionId,publishId,paperRunId,actor.userId());
    }
}
