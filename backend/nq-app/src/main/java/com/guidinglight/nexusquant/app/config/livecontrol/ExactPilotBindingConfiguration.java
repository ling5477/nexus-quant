package com.guidinglight.nexusquant.app.config.livecontrol;

import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingAuthority;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingControlPlane;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotScopeControlPlane;
import com.guidinglight.nexusquant.livecontrol.application.PilotScopeControlPlane;
import com.guidinglight.nexusquant.livecontrol.application.port.LiveControlAuthorizationPort;
import com.guidinglight.nexusquant.livecontrol.domain.port.ExactPilotBindingRepository;
import com.guidinglight.nexusquant.livecontrol.domain.port.ExactPilotScopeAuthorizationRepository;
import com.guidinglight.nexusquant.livecontrol.domain.port.LiveControlRepository;
import com.guidinglight.nexusquant.livecontrol.domain.port.PilotScopeRepository;
import com.guidinglight.nexusquant.livecontrol.infra.ExactPilotBindingService;
import com.guidinglight.nexusquant.livecontrol.infra.ExactPilotScopeAuthorizationService;
import com.guidinglight.nexusquant.livecontrol.infra.ExactPilotScopeControlSurfaceService;
import com.guidinglight.nexusquant.marketdata.domain.instrument.port.InstrumentCatalogReadPort;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseAdmissionStateRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Exact binding capability 的显式 composition root；默认不装配，不改变已接受的只读 deployment。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(ReadOnlyProviderObservationRuntimeIdentity.class)
@ConditionalOnProperty(
        prefix = "nq.live-control.exact-pilot-binding",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class ExactPilotBindingConfiguration {

    @Bean
    public ExactPilotRuntimeIdentity exactPilotRuntimeIdentity(
            ReadOnlyProviderObservationRuntimeIdentity runtime,
            @Value("${nq.live-control.exact-pilot-binding.manifest-sha256}") String manifestSha256,
            @Value("${nq.live-control.exact-pilot-binding.server-identity}") String serverIdentity,
            @Value("${nq.runtime.provider-observation.deployment-profile}") String runtimeProfile
    ) {
        return ExactPilotRuntimeIdentity.from(runtime, manifestSha256, serverIdentity, runtimeProfile);
    }

    @Bean
    public ExactPilotBindingAuthority exactPilotBindingAuthority(
            LiveControlRepository liveControlRepository,
            PilotScopeRepository pilotScopeRepository,
            ExchangeAccountRepository accountRepository,
            ExchangeAccountCredentialRepository credentialRepository,
            StrategyReleaseAdmissionStateRepository admissionRepository,
            InstrumentCatalogReadPort instrumentCatalog,
            KillSwitchService killSwitchService,
            ExactPilotRuntimeIdentity runtimeIdentity
    ) {
        return new StoredFactExactPilotBindingAuthority(
                liveControlRepository, pilotScopeRepository, accountRepository, credentialRepository,
                admissionRepository, instrumentCatalog, killSwitchService, runtimeIdentity
        );
    }

    @Bean
    public ExactPilotBindingControlPlane exactPilotBindingControlPlane(
            ExactPilotBindingAuthority authority,
            ExactPilotBindingRepository repository,
            ExactPilotScopeAuthorizationRepository scopeAuthorizationRepository,
            LiveControlAuthorizationPort authorization,
            PlatformTransactionManager transactionManager
    ) {
        return new ExactPilotBindingService(
                authority, repository, scopeAuthorizationRepository, authorization, transactionManager);
    }

    @Bean
    public ExactPilotScopeAuthorizationService exactPilotScopeAuthorizationService(
            ExactPilotBindingAuthority authority,
            ExactPilotBindingRepository bindingRepository,
            ExactPilotScopeAuthorizationRepository scopeAuthorizationRepository,
            LiveControlAuthorizationPort authorization,
            PlatformTransactionManager transactionManager
    ) {
        return new ExactPilotScopeAuthorizationService(
                authority, bindingRepository, scopeAuthorizationRepository, authorization, transactionManager);
    }

    @Bean
    public ExactPilotScopeControlPlane exactPilotScopeControlPlane(
            PilotScopeControlPlane pilotScopeControlPlane,
            ExactPilotScopeAuthorizationService scopeAuthorizationService,
            ExactPilotBindingControlPlane bindingControlPlane
    ) {
        return new ExactPilotScopeControlSurfaceService(
                pilotScopeControlPlane, scopeAuthorizationService, bindingControlPlane);
    }
}
