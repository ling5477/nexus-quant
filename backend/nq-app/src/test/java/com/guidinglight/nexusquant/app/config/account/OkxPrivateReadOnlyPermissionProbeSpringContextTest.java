package com.guidinglight.nexusquant.app.config.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.domain.port.ExchangeCredentialPermissionProbePort;
import com.guidinglight.nexusquant.account.domain.CredentialPermissionExpectation;
import com.guidinglight.nexusquant.account.infra.probe.NoRealExchangeCredentialPermissionProbePort;
import com.guidinglight.nexusquant.account.infra.probe.OkxRealReadonlyPermissionProbePort;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import com.guidinglight.nexusquant.scheduler.service.OkxRecoveryService;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * OKX private read-only permission probe 的 Spring composition 回归。
 *
 * <p>只构造 Bean，不调用 probe；因此即使 Real port 被选择也不会访问网络、解密 credential、
 * 启动 scheduler 或触达交易写侧。</p>
 */
class OkxPrivateReadOnlyPermissionProbeSpringContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withUserConfiguration(
                    AccountModuleConfiguration.class,
                    OkxPrivateReadOnlyDiagnosticsConfiguration.class,
                    Dependencies.class
            )
            .withPropertyValues("nq.account.credentials.master-key=test-master-key");

    @Test
    void defaultContextUsesExactlyOneNoRealPort() {
        contextRunner.run(context -> assertSelected(context, NoRealExchangeCredentialPermissionProbePort.class));
    }

    @Test
    void ciOrNoOutboundContextUsesNoRealEvenWithSoakProfile() {
        realCandidateRunner("nq.env-safety.ci=true")
                .run(context -> assertSelected(context, NoRealExchangeCredentialPermissionProbePort.class));
        realCandidateRunner("nq.env-safety.no-outbound=true")
                .run(context -> assertSelected(context, NoRealExchangeCredentialPermissionProbePort.class));
    }

    @Test
    void explicitSoakProfileSelectsRealPortWithoutStartupNetwork() {
        realCandidateRunner().run(context -> {
            assertTrue(context.getStartupFailure() == null);
            assertSelected(context, OkxRealReadonlyPermissionProbePort.class);
            assertEquals(CredentialPermissionExpectation.READ_ONLY_DIAGNOSTIC,
                    context.getBean(OkxRealReadonlyPermissionProbePort.class).permissionExpectation());
        });
    }

    @Test
    void explicitScopedGateYProfileSelectsPilotReadinessPolicyWithoutStartupNetwork() {
        candidateRunner(new String[]{"scoped-okx-private-readonly"}).run(context -> {
            assertTrue(context.getStartupFailure() == null);
            assertSelected(context, OkxRealReadonlyPermissionProbePort.class);
            assertEquals(CredentialPermissionExpectation.GATEY_PILOT_READINESS,
                    context.getBean(OkxRealReadonlyPermissionProbePort.class).permissionExpectation());
        });
    }

    @Test
    void scopedGateYProfileDoesNotRegisterOkxRecoveryOrItsScheduler() {
        new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles(
                        "scoped-okx-private-readonly"
                ))
                .withUserConfiguration(OkxRecoveryService.class)
                .run(context -> {
                    assertTrue(context.getStartupFailure() == null);
                    assertTrue(context.getBeansOfType(OkxRecoveryService.class).isEmpty());
                });
    }

    @Test
    void conflictingGateWAndGateYProfilesFailClosedToNoReal() {
        candidateRunner(new String[]{"okx-private-readonly-diagnostics", "scoped-okx-private-readonly"})
                .run(context -> assertSelected(context, NoRealExchangeCredentialPermissionProbePort.class));
    }

    @Test
    void legacyProfileAndKeysRemainCompatible() {
        legacyCandidateRunner().run(context -> assertSelected(context, OkxRealReadonlyPermissionProbePort.class));
    }

    @Test
    void conflictingStableAndLegacyPermissionKeysFailClosed() {
        realCandidateRunner(
                "nq.gatew.okx-private-readonly.permission-probe.expected-ip=203.0.113.9"
        ).run(context -> assertSelected(context, NoRealExchangeCredentialPermissionProbePort.class));
    }

    @Test
    void liveTrueRejectsRealComposition() {
        realCandidateRunner("nq.env-safety.live-enabled=true")
                .run(context -> assertSelected(context, NoRealExchangeCredentialPermissionProbePort.class));
    }

    @Test
    void springManagedObjectMapperSerializesJavaTimeWithoutCredentialFields() {
        contextRunner.run(context -> {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            String json = assertDoesNotThrow(() -> mapper.writeValueAsString(new TimeFixture(
                    Instant.parse("2026-07-16T00:00:00Z"),
                    OffsetDateTime.parse("2026-07-16T08:00:00+08:00"),
                    LocalDateTime.parse("2026-07-16T08:00:00")
            )));

            assertTrue(json.contains("2026-07-16"));
            assertFalse(json.toLowerCase().contains("credential"));
            assertFalse(json.toLowerCase().contains("secret"));
        });
    }

    private ApplicationContextRunner realCandidateRunner(String... overrides) {
        return candidateRunner(new String[]{"okx-private-readonly-diagnostics"}, overrides);
    }

    private ApplicationContextRunner candidateRunner(String[] profiles, String... overrides) {
        ApplicationContextRunner runner = contextRunner
                .withInitializer(context -> context.getEnvironment().setActiveProfiles(profiles))
                .withPropertyValues(
                        "nq.okx.private-readonly-diagnostics.enabled=true",
                        "nq.okx.private-readonly-diagnostics.order-submission-enabled=false",
                        "nq.okx.private-readonly-diagnostics.transfer-enabled=false",
                        "nq.okx.private-readonly-diagnostics.withdraw-enabled=false",
                        "nq.okx.private-readonly-diagnostics.permission-probe.enabled=true",
                        "nq.okx.private-readonly-diagnostics.permission-probe.expected-ip=203.0.113.8",
                        "nq.env-safety.ci=false",
                        "nq.env-safety.live-enabled=false",
                        "nq.env-safety.real-exchange-enabled=false",
                        "nq.env-safety.real-client-enabled=false",
                        "nq.env-safety.real-provider-enabled=false",
                        "nq.env-safety.no-outbound=false"
                );
        return overrides.length == 0 ? runner : runner.withPropertyValues(overrides);
    }

    private ApplicationContextRunner legacyCandidateRunner() {
        return contextRunner
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("gatew-okx-readonly-soak"))
                .withPropertyValues(
                        "nq.gatew.okx-private-readonly.enabled=true",
                        "nq.gatew.okx-private-readonly.order-submission-enabled=false",
                        "nq.gatew.okx-private-readonly.transfer-enabled=false",
                        "nq.gatew.okx-private-readonly.withdraw-enabled=false",
                        "nq.gatew.okx-private-readonly.permission-probe.enabled=true",
                        "nq.gatew.okx-private-readonly.permission-probe.expected-ip=203.0.113.8",
                        "nq.env-safety.ci=false",
                        "nq.env-safety.live-enabled=false",
                        "nq.env-safety.real-exchange-enabled=false",
                        "nq.env-safety.real-client-enabled=false",
                        "nq.env-safety.real-provider-enabled=false",
                        "nq.env-safety.no-outbound=false"
                );
    }

    private static void assertSelected(
            org.springframework.context.ApplicationContext context,
            Class<? extends ExchangeCredentialPermissionProbePort> expectedType
    ) {
        assertEquals(1, context.getBeansOfType(ExchangeCredentialPermissionProbePort.class).size());
        assertInstanceOf(expectedType, context.getBean(ExchangeCredentialPermissionProbePort.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class Dependencies {
        @Bean
        JdbcTemplate jdbcTemplate() {
            return mock(JdbcTemplate.class);
        }

        @Bean
        PlatformTransactionManager platformTransactionManager() {
            return mock(PlatformTransactionManager.class);
        }

        @Bean
        KillSwitchService killSwitchService() {
            return mock(KillSwitchService.class);
        }
    }

    private record TimeFixture(
            Instant instant,
            OffsetDateTime offsetDateTime,
            LocalDateTime localDateTime
    ) {
    }
}
