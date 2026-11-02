package com.guidinglight.nexusquant.paper.api.web;

import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.paper.api.dto.PaperLegacyAnalysisResponse;
import com.guidinglight.nexusquant.research.application.paper.service.PaperLegacyAnalysisService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 当前登录主体限定私有费用来源；不接受请求传入owner，也不将历史Paper接口冒充独立SIM全站分析。 */
@RestController
public class PaperLegacyAnalysisController {
    private final GatewayAuthFacade auth;
    private final CurrentUserProfileService profiles;
    private final PaperLegacyAnalysisService service;

    public PaperLegacyAnalysisController(
            GatewayAuthFacade auth,
            CurrentUserProfileService profiles,
            PaperLegacyAnalysisService service) {
        this.auth = auth;
        this.profiles = profiles;
        this.service = service;
    }

    @GetMapping("/api/paper-trading/legacy-analysis")
    @Operation(
            summary = "读取历史Paper诊断、评估与规则复盘",
            security = @SecurityRequirement(name = "bearerAuth"))
    public PaperLegacyAnalysisResponse read() {
        var user =
                auth.currentUser()
                        .orElseThrow(
                                () ->
                                        new AuthenticationCredentialsNotFoundException(
                                                "authentication required"));
        long owner =
                profiles.findByUsername(user.username())
                        .orElseThrow(
                                () ->
                                        new AuthenticationCredentialsNotFoundException(
                                                "authentication required"))
                        .userId();
        return PaperLegacyAnalysisResponse.from(service.read(owner));
    }
}
