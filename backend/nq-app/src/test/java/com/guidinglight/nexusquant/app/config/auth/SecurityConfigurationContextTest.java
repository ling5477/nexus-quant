package com.guidinglight.nexusquant.app.config.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.application.CredentialPermissionProbeService;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.account.infra.okx.readonly.OkxPrivateCredentialExecutor;
import com.guidinglight.nexusquant.app.config.livecontrol.MinimalLivePilotConfiguration;
import com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository;
import com.guidinglight.nexusquant.auth.application.AuthService;
import com.guidinglight.nexusquant.auth.application.CurrentUserProfileService;
import com.guidinglight.nexusquant.auth.domain.port.AuthUserRepository;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotBindingControlPlane;
import com.guidinglight.nexusquant.livecontrol.application.LiveSessionControlService;
import com.guidinglight.nexusquant.livecontrol.application.PilotScopeControlPlane;
import com.guidinglight.nexusquant.livecontrol.domain.port.ExactPilotBindingRepository;
import com.guidinglight.nexusquant.livecontrol.domain.port.LiveControlRepository;
import com.guidinglight.nexusquant.livecontrol.domain.port.PilotExecutionLeaseRepository;
import com.guidinglight.nexusquant.livecontrol.domain.port.PilotScopeRepository;
import com.guidinglight.nexusquant.livecontrol.execution.application.port.ExecutionIntentRepository;
import com.guidinglight.nexusquant.marketdata.domain.instrument.port.InstrumentCatalogReadPort;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import com.guidinglight.nexusquant.scheduler.service.TradeLedgerGateway;
import com.guidinglight.nexusquant.scheduler.service.port.TradeRepository;
import com.guidinglight.nexusquant.security.token.TokenService;
import com.guidinglight.nexusquant.trading.application.OrderCommandService;
import com.guidinglight.nexusquant.trading.application.OrderLifecycleService;
import com.guidinglight.nexusquant.trading.application.port.TradingVenueGateway;
import com.guidinglight.nexusquant.trading.domain.port.OrderRepository;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

class SecurityConfigurationContextTest {

    private static final String[] SECURITY_PROPERTIES = {
            "--spring.main.banner-mode=off",
            "--nq.security.issuer=nexus-quant-non-web-test",
            "--nq.security.secret=non-web-test-change-me-change-me-123456",
            "--nq.security.access-token-ttl=PT30M"
    };

    @Test
    void nonWebSpringApplicationStartsWithoutServletSecurityChain() {
        SpringApplication application = new SpringApplication(
                SecurityConfiguration.class,
                NeutralSecurityDependencies.class
        );
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setRegisterShutdownHook(false);

        try (ConfigurableApplicationContext context = application.run(SECURITY_PROPERTIES)) {
            assertEquals(0, context.getBeansOfType(SecurityFilterChain.class).size());
            assertNotNull(context.getBean(PasswordEncoder.class));
            assertNotNull(context.getBean(TokenService.class));
            assertNotNull(context.getBean(AuthService.class));
            assertNotNull(context.getBean(CurrentUserProfileService.class));
        }
    }

    @Test
    void minimalPilotConfigurationInitializesInNonWebContextWithoutExternalCalls() {
        CredentialPermissionProbeService permissionProbeService = mock(CredentialPermissionProbeService.class);
        OkxPrivateCredentialExecutor credentialExecutor = mock(OkxPrivateCredentialExecutor.class);
        OrderCommandService orderCommandService = mock(OrderCommandService.class);

        new ApplicationContextRunner()
                .withUserConfiguration(
                        MinimalLivePilotConfiguration.class,
                        PilotBindingDependencyConfiguration.class,
                        GatewaySelectionProbeConfiguration.class,
                        SecurityConfiguration.class
                )
                .withPropertyValues(
                        "spring.main.web-application-type=none",
                        "nq.security.issuer=nexus-quant-pilot-context-test",
                        "nq.security.secret=pilot-context-test-change-me-change-me-123456",
                        "nq.security.access-token-ttl=PT30M",
                        "nq.runtime.minimal-live-pilot.enabled=true",
                        "nq.runtime.minimal-live-pilot.order-submission-enabled=true",
                        "nq.runtime.minimal-live-pilot.cancel-enabled=true",
                        "nq.runtime.minimal-live-pilot.transfer-enabled=false",
                        "nq.runtime.minimal-live-pilot.withdraw-enabled=false",
                        "nq.runtime.minimal-live-pilot.exchange-account-id=1",
                        "nq.runtime.minimal-live-pilot.credential-reference-id=1",
                        "nq.runtime.minimal-live-pilot.instrument=BTC-USDT",
                        "nq.runtime.minimal-live-pilot.side=BUY",
                        "nq.runtime.minimal-live-pilot.configured-max-notional=10"
                )
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(AuthUserRepository.class, () -> mock(AuthUserRepository.class))
                .withBean(ExchangeAccountRepository.class, () -> mock(ExchangeAccountRepository.class))
                .withBean(InstrumentCatalogReadPort.class, () -> mock(InstrumentCatalogReadPort.class))
                .withBean(CredentialPermissionProbeService.class, () -> permissionProbeService)
                .withBean(PilotScopeControlPlane.class, () -> mock(PilotScopeControlPlane.class))
                .withBean(PilotScopeRepository.class, () -> mock(PilotScopeRepository.class))
                .withBean(PilotExecutionLeaseRepository.class, () -> mock(PilotExecutionLeaseRepository.class))
                .withBean(LiveSessionControlService.class, () -> mock(LiveSessionControlService.class))
                .withBean(KillSwitchService.class, () -> mock(KillSwitchService.class))
                .withBean(LiveControlRepository.class, () -> mock(LiveControlRepository.class))
                .withBean(OkxPrivateCredentialExecutor.class, () -> credentialExecutor)
                .withBean(ExecutionIntentRepository.class, () -> mock(ExecutionIntentRepository.class))
                .withBean(ExactPilotBindingRepository.class, () -> mock(ExactPilotBindingRepository.class))
                .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
                .withBean(OrderCommandService.class, () -> orderCommandService)
                .withBean(OrderLifecycleService.class, () -> mock(OrderLifecycleService.class))
                .withBean(OrderRepository.class, () -> mock(OrderRepository.class))
                .withBean(TradeRepository.class, () -> mock(TradeRepository.class))
                .withBean(TradeLedgerGateway.class, () -> mock(TradeLedgerGateway.class))
                .withBean(AuditLogRepository.class, () -> mock(AuditLogRepository.class))
                .run(context -> {
                    assertEquals(0, context.getBeansOfType(SecurityFilterChain.class).size());
                    assertEquals(2, context.getBeansOfType(ApplicationRunner.class).size());
                    assertNotNull(context.getBean("minimalPilotStartupRecovery"));
                    assertNotNull(context.getBean("minimalLivePilotRunner"));
                    assertNotNull(context.getBean(PasswordEncoder.class));
                    assertNotNull(context.getBean(TokenService.class));
                    assertNotNull(context.getBean(AuthService.class));
                    assertSame(
                            context.getBean("minimalPilotTradingVenueGateway"),
                            context.getBean(SelectedGateway.class).gateway()
                    );
                    verifyNoInteractions(permissionProbeService, credentialExecutor, orderCommandService);
                });
    }

    @Test
    void minimalPilotCompositionDoesNotUseOrderingSensitiveClassCondition() {
        assertFalse(MinimalLivePilotConfiguration.class.isAnnotationPresent(
                org.springframework.boot.autoconfigure.condition.ConditionalOnBean.class
        ));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class NeutralSecurityDependencies {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        AuthUserRepository authUserRepository() {
            return mock(AuthUserRepository.class);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PilotBindingDependencyConfiguration {

        @Bean
        ExactPilotBindingControlPlane exactPilotBindingControlPlane() {
            return mock(ExactPilotBindingControlPlane.class);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class GatewaySelectionProbeConfiguration {

        @Bean
        TradingVenueGateway competingTradingVenueGateway() {
            return mock(TradingVenueGateway.class);
        }

        @Bean
        SelectedGateway selectedGateway(TradingVenueGateway gateway) {
            return new SelectedGateway(gateway);
        }
    }

    private record SelectedGateway(TradingVenueGateway gateway) {
    }
}
