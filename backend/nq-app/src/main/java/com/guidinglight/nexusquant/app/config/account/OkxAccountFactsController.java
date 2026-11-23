package com.guidinglight.nexusquant.app.config.account;

import com.guidinglight.nexusquant.account.infra.okx.readonly.AccountFactsSnapshot;
import com.guidinglight.nexusquant.account.infra.okx.readonly.OkxAccountFactsObservationService;
import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 仅在显式只读 profile 中注册，人工 POST 才触发私有观察。 */
@Validated
@RestController
@Profile({"okx-private-readonly-diagnostics & !scoped-okx-private-readonly",
        "scoped-okx-private-readonly & !okx-private-readonly-diagnostics"})
@Conditional(OkxPrivateReadOnlyDiagnosticsConfiguration.OkxPrivateReadOnlyDiagnosticsEnabledCondition.class)
@ConditionalOnProperty(prefix = "nq.env-safety",
        name = {"ci", "live-enabled", "real-exchange-enabled", "real-client-enabled", "real-provider-enabled"},
        havingValue = "false", matchIfMissing = false)
@ConditionalOnProperty(prefix = "nq.env-safety", name = "no-outbound",
        havingValue = "false", matchIfMissing = false)
@RequestMapping("/api/exchange-accounts/{accountId}/credentials/{credentialId}/account-facts")
public class OkxAccountFactsController {
    private final OkxAccountFactsObservationService service;
    private final GatewayAuthFacade auth;
    private final CurrentUserProfileService users;

    public OkxAccountFactsController(OkxAccountFactsObservationService service,
                                     GatewayAuthFacade auth,
                                     CurrentUserProfileService users) {
        this.service = service;
        this.auth = auth;
        this.users = users;
    }

    @PostMapping("/observe")
    public AccountFactsSnapshot observe(@PathVariable @Positive long accountId,
                                        @PathVariable @Positive long credentialId) {
        var user = auth.currentUser()
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("authentication required"));
        long ownerId = users.findByUsername(user.username())
                .map(profile -> profile.userId())
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("authentication required"));
        return service.observe(ownerId, accountId, credentialId);
    }
}
