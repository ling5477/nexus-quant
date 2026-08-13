package com.guidinglight.nexusquant.app.config.livecontrol;

import com.guidinglight.nexusquant.adapter.api.service.TradingAdapter;
import com.guidinglight.nexusquant.account.infra.okx.readonly.OkxPrivateCredentialExecutor;
import com.guidinglight.nexusquant.app.config.ExchangeAdapterConfiguration;
import com.guidinglight.nexusquant.livecontrol.deployment.WorkerDeploymentAdmissionService;
import com.guidinglight.nexusquant.livecontrol.deployment.WorkerOperationSafetyGate;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.TrustedRootStrategyArtifactVerifier;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.VerifiedOpenStrategyArtifactReader;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** Non-default deployment admission wiring must have no startup side effect。 */
class WorkerDeploymentBoundaryConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(WorkerDeploymentBoundaryConfiguration.class, Dependencies.class);

    @Test
    void defaultContextDoesNotCreateWorkerDeploymentBoundary() {
        runner.run(context -> {
            assertTrue(context.getBeansOfType(WorkerDeploymentAdmissionService.class).isEmpty());
            assertTrue(context.getBeansOfType(VerifiedOpenStrategyArtifactReader.class).isEmpty());
        });
    }

    @Test
    void explicitProfileAndFlagsCreatePoliciesWithoutTradingOrCredentialRuntime() {
        runner.withUserConfiguration(ExchangeAdapterConfiguration.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("worker-deployment-admission"))
                .withPropertyValues(
                        "nq.live-control.worker-deployment-admission.enabled=true",
                        "nq.env-safety.live-enabled=false")
                .run(context -> {
                    assertTrue(context.getStartupFailure() == null);
                    assertFalse(context.getBeansOfType(WorkerDeploymentAdmissionService.class).isEmpty());
                    assertFalse(context.getBeansOfType(WorkerOperationSafetyGate.class).isEmpty());
                    assertFalse(context.getBeansOfType(VerifiedOpenStrategyArtifactReader.class).isEmpty());
                    assertTrue(context.getBeansOfType(TradingAdapter.class).isEmpty());
                    assertTrue(context.getBeansOfType(OkxPrivateCredentialExecutor.class).isEmpty());
                });
    }

    @Configuration
    static class Dependencies {
        @Bean KillSwitchService killSwitchService() { return mock(KillSwitchService.class); }
        @Bean TrustedRootStrategyArtifactVerifier trustedRootStrategyArtifactVerifier() {
            return mock(TrustedRootStrategyArtifactVerifier.class);
        }
    }
}
