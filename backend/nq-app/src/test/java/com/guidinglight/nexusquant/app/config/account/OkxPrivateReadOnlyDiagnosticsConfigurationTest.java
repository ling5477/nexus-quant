package com.guidinglight.nexusquant.app.config.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialRepository;
import com.guidinglight.nexusquant.account.infra.okx.readonly.OkxPrivateCredentialExecutor;
import com.guidinglight.nexusquant.account.infra.okx.readonly.JdbcOkxPrivateCredentialExecutor;
import com.guidinglight.nexusquant.account.infra.okx.readonly.OkxPrivateReadonlyProbeService;
import com.guidinglight.nexusquant.adapter.api.service.port.TradingAdapter;
import com.guidinglight.nexusquant.livecontrol.execution.application.provider.SpotExecutionProviderPort;
import com.guidinglight.nexusquant.adapter.okx.privateread.transport.OkxPrivateReadTransport;
import com.guidinglight.nexusquant.adapter.okx.privateread.transport.OkxAccountFactsReadTransport;
import com.guidinglight.nexusquant.adapter.okx.privateread.transport.OkxPrivateRealTransport;
import com.guidinglight.nexusquant.account.infra.okx.readonly.OkxAccountFactsObservationService;
import com.guidinglight.nexusquant.marketdata.application.instrument.InstrumentCatalogService;
import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.adapter.okx.ws.OkxWsClient;
import com.guidinglight.nexusquant.app.config.ExchangeAdapterConfiguration;
import com.guidinglight.nexusquant.app.config.livecontrol.ReadOnlyProviderObservationConfiguration;
import com.guidinglight.nexusquant.app.config.livecontrol.endpoint.ReadOnlyRuntimeDiagnosticEndpoint;
import com.guidinglight.nexusquant.app.config.livecontrol.model.ReadOnlyProviderObservationRuntimeIdentity;
import com.guidinglight.nexusquant.livecontrol.application.port.PilotPrerequisiteObservationAuthority;
import com.guidinglight.nexusquant.risk.application.command.KillSwitchEngageCommand;
import com.guidinglight.nexusquant.risk.domain.model.KillSwitchScope;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import com.guidinglight.nexusquant.risk.domain.model.KillSwitchState;
import com.guidinglight.nexusquant.risk.application.port.KillSwitchStateRepository;
import com.guidinglight.nexusquant.risk.domain.model.KillSwitchStatus;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

class OkxPrivateReadOnlyDiagnosticsConfigurationTest {

    private static final String STABLE_PREFIX = "nq.okx.private-readonly-diagnostics";
    private static final String LEGACY_PREFIX = "nq.gatew.okx-private-readonly";

    @Test
    void defaultProfileDoesNotCreatePrivateTransportOrDecryptor() {
        try (AnnotationConfigApplicationContext context = context((String) null, STABLE_PREFIX, false, false)) {
            assertPrivateBeansAbsent(context);
        }
    }

    @Test
    void explicitProfileWithFlagFalseDoesNotCreatePrivateTransportOrDecryptor() {
        try (AnnotationConfigApplicationContext context = context(
                "okx-private-readonly-diagnostics",
                STABLE_PREFIX,
                false,
                false
        )) {
            assertPrivateBeansAbsent(context);
        }
    }

    @Test
    void explicitProfileAndFlagCreateOnlyReadonlyComponentsWithoutStartupNetwork() {
        try (AnnotationConfigApplicationContext context = context(
                "okx-private-readonly-diagnostics",
                STABLE_PREFIX,
                true,
                false
        )) {
            assertFalse(context.getBeansOfType(OkxPrivateReadTransport.class).isEmpty());
            assertFalse(context.getBeansOfType(OkxPrivateCredentialExecutor.class).isEmpty());
            assertFalse(context.getBeansOfType(OkxPrivateReadonlyProbeService.class).isEmpty());
            assertFalse(context.getBeansOfType(OkxAccountFactsObservationService.class).isEmpty());
            assertFalse(context.getBeansOfType(OkxAccountFactsController.class).isEmpty());
            assertTrue(context.getBean(OkxPrivateReadTransport.class) instanceof OkxAccountFactsReadTransport);
            assertFalse(context.getBean(OkxPrivateReadTransport.class) instanceof OkxPrivateRealTransport);
            assertTrue(context.getBeansOfType(TradingAdapter.class).isEmpty());
            assertTrue(context.getBeansOfType(OkxWsClient.class).isEmpty());
            assertTrue(mockingDetails(context.getBean(JdbcTemplate.class)).getInvocations().stream()
                    .noneMatch(invocation -> invocation.getMethod().getName().matches(
                            "query|queryForObject|queryForList|update|execute|call")));
        }
    }

    @Test
    void legacyProfileAndKeysCannotCreateReadOnlyComponents() {
        try (AnnotationConfigApplicationContext context = context(
                "gatew-okx-readonly",
                LEGACY_PREFIX,
                true,
                false
        )) {
            assertTrue(context.getBeansOfType(OkxPrivateReadTransport.class).isEmpty());
            assertTrue(context.getBeansOfType(OkxPrivateCredentialExecutor.class).isEmpty());
            assertTrue(context.getBeansOfType(OkxPrivateReadonlyProbeService.class).isEmpty());
        }
    }

    @Test
    void scopedExplicitProfileStillRequiresExactReadOnlyFlagsAndCreatesNoTradingAdapter() {
        try (AnnotationConfigApplicationContext context = context(
                "scoped-okx-private-readonly", STABLE_PREFIX, true, false)) {
            assertEquals(1, context.getBeansOfType(OkxPrivateReadTransport.class).size());
            assertEquals(1, context.getBeansOfType(OkxPrivateCredentialExecutor.class).size());
            assertEquals(1, context.getBeansOfType(OkxPrivateReadonlyProbeService.class).size());
            assertEquals(1, context.getBeansOfType(OkxAccountFactsController.class).size());
            assertTrue(context.getBean(OkxPrivateReadTransport.class) instanceof OkxAccountFactsReadTransport);
            assertTrue(context.getBeansOfType(OkxPrivateRealTransport.class).isEmpty());
            assertEquals(1, context.getBeansOfType(ReadOnlyProviderObservationRuntimeIdentity.class).size());
            assertEquals(1, context.getBeansOfType(ReadOnlyRuntimeDiagnosticEndpoint.class).size());
            assertFalse(context.getBean(ReadOnlyRuntimeDiagnosticEndpoint.class).read().providerObservationEnabled());
            assertTrue(context.getBeansOfType(PilotPrerequisiteObservationAuthority.class).isEmpty());
            JdbcOkxPrivateCredentialExecutor executor =
                    (JdbcOkxPrivateCredentialExecutor) context.getBean(OkxPrivateCredentialExecutor.class);
            assertSame(context.getBean(OkxAccountFactsReadTransport.class),
                    ReflectionTestUtils.getField(executor, "transport"));
            assertTrue(context.getBeansOfType(TradingAdapter.class).isEmpty());
            assertTrue(context.getBeansOfType(SpotExecutionProviderPort.class).isEmpty());
            assertTrue(context.getBeansOfType(OkxWsClient.class).isEmpty());
            assertTrue(mockingDetails(context.getBean(JdbcTemplate.class)).getInvocations().stream()
                    .noneMatch(invocation -> invocation.getMethod().getName().matches(
                            "query|queryForObject|queryForList|update|execute|call")));
        }
    }

    @Test
    void conflictingReadonlyProfilesCreateNoPrivateComponents() {
        try (AnnotationConfigApplicationContext context = context(
                new String[]{"okx-private-readonly-diagnostics", "scoped-okx-private-readonly"},
                STABLE_PREFIX, true, false)) {
            assertPrivateBeansAbsent(context);
        }
    }

    @Test
    void conflictingEnableKeysFailClosed() {
        try (AnnotationConfigApplicationContext context = context(
                "okx-private-readonly-diagnostics",
                STABLE_PREFIX,
                true,
                false,
                Map.of(LEGACY_PREFIX + ".enabled", false)
        )) {
            assertPrivateBeansAbsent(context);
        }
    }

    @Test
    void liveTrueFailsClosedByNotCreatingPrivateComponents() {
        try (AnnotationConfigApplicationContext context = context(
                "okx-private-readonly-diagnostics",
                STABLE_PREFIX,
                true,
                true
        )) {
            assertPrivateBeansAbsent(context);
        }
    }

    @Test
    void missingOrInvalidLivePropertyFailsClosed() {
        try (AnnotationConfigApplicationContext missing = context(
                "okx-private-readonly-diagnostics",
                STABLE_PREFIX,
                true,
                null
        );
             AnnotationConfigApplicationContext invalid = context(
                     "okx-private-readonly-diagnostics",
                     STABLE_PREFIX,
                     true,
                     "invalid"
             )) {
            assertPrivateBeansAbsent(missing);
            assertPrivateBeansAbsent(invalid);
        }
    }

    private static AnnotationConfigApplicationContext context(
            String profile,
            String prefix,
            boolean enabled,
            Object live
    ) {
        return context(profile, prefix, enabled, live, Map.of());
    }

    private static AnnotationConfigApplicationContext context(
            String profile,
            String prefix,
            boolean enabled,
            Object live,
            Map<String, Object> overrides
    ) {
        return context(profile == null ? null : new String[]{profile}, prefix, enabled, live, overrides);
    }

    private static AnnotationConfigApplicationContext context(
            String[] profiles,
            String prefix,
            boolean enabled,
            Object live
    ) {
        return context(profiles, prefix, enabled, live, Map.of());
    }

    private static AnnotationConfigApplicationContext context(
            String[] profiles,
            String prefix,
            boolean enabled,
            Object live,
            Map<String, Object> overrides
    ) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        if (profiles != null) {
            context.getEnvironment().setActiveProfiles(profiles);
        }
        Map<String, Object> properties = new HashMap<>();
        properties.put(prefix + ".enabled", enabled);
        properties.put(prefix + ".order-submission-enabled", false);
        properties.put(prefix + ".transfer-enabled", false);
        properties.put(prefix + ".withdraw-enabled", false);
        properties.put("nq.env-safety.ci", false);
        properties.put("nq.env-safety.real-exchange-enabled", false);
        properties.put("nq.env-safety.real-client-enabled", false);
        properties.put("nq.env-safety.real-provider-enabled", false);
        properties.put("nq.env-safety.no-outbound", false);
        boolean scoped = profiles != null && Arrays.asList(profiles).contains("scoped-okx-private-readonly");
        if (scoped) {
            properties.put("nq.runtime.provider-observation.enabled", true);
            properties.put("nq.runtime.provider-observation.release-id", "1".repeat(40));
            properties.put("nq.runtime.provider-observation.source-commit", "1".repeat(40));
            properties.put("NQ_RELEASE_MANIFEST_SHA256", "2".repeat(64));
            properties.put("nq.runtime.provider-observation.capability-identity", "read-only-provider-observation");
            properties.put("nq.runtime.provider-observation.order-submission-enabled", false);
            properties.put("nq.runtime.provider-observation.cancel-enabled", false);
            properties.put("nq.runtime.provider-observation.transfer-enabled", false);
            properties.put("nq.runtime.provider-observation.withdraw-enabled", false);
            properties.put("server.address", "127.0.0.1");
        }
        if (live != null) {
            properties.put("nq.env-safety.live-enabled", live);
        }
        properties.putAll(overrides);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("readonly-diagnostics-test", properties));
        context.register(
                OkxPrivateReadOnlyDiagnosticsConfiguration.class,
                OkxAccountFactsController.class,
                ExchangeAdapterConfiguration.class,
                Dependencies.class
        );
        if (scoped) {
            context.register(ReadOnlyProviderObservationConfiguration.class);
        }
        context.refresh();
        return context;
    }

    private static void assertPrivateBeansAbsent(AnnotationConfigApplicationContext context) {
        assertTrue(context.getBeansOfType(OkxPrivateReadTransport.class).isEmpty());
        assertTrue(context.getBeansOfType(OkxPrivateCredentialExecutor.class).isEmpty());
        assertTrue(context.getBeansOfType(OkxPrivateReadonlyProbeService.class).isEmpty());
        assertTrue(context.getBeansOfType(OkxAccountFactsObservationService.class).isEmpty());
        assertTrue(context.getBeansOfType(OkxAccountFactsController.class).isEmpty());
    }

    @Configuration
    static class Dependencies {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        JdbcTemplate jdbcTemplate() {
            return mock(JdbcTemplate.class);
        }

        @Bean
        ExchangeAccountRepository exchangeAccountRepository() {
            return mock(ExchangeAccountRepository.class);
        }

        @Bean
        ExchangeAccountCredentialRepository exchangeAccountCredentialRepository() {
            return mock(ExchangeAccountCredentialRepository.class);
        }

        @Bean
        InstrumentCatalogService instrumentCatalogService() {
            return mock(InstrumentCatalogService.class);
        }

        @Bean
        GatewayAuthFacade gatewayAuthFacade() {
            return mock(GatewayAuthFacade.class);
        }

        @Bean
        CurrentUserProfileService currentUserProfileService() {
            return mock(CurrentUserProfileService.class);
        }

        @Bean
        OkxPrivateReadOnlyPermissionProbeProperties permissionProperties() {
            return new OkxPrivateReadOnlyPermissionProbeProperties(true, "203.0.113.8");
        }

        @Bean
        KillSwitchService killSwitchService() {
            KillSwitchStateRepository repository = new KillSwitchStateRepository() {
                @Override
                public Optional<KillSwitchState> findByScope(KillSwitchScope scope) {
                    return Optional.of(new KillSwitchState(
                            scope,
                            KillSwitchStatus.DISENGAGED,
                            1,
                            "TEST_DISENGAGED",
                            "TEST_FIXTURE",
                            Instant.parse("2026-07-13T23:59:59Z"),
                            "tester",
                            "trace-config-fixture"
                    ));
                }

                @Override
                public KillSwitchState engage(KillSwitchEngageCommand command) {
                    throw new UnsupportedOperationException();
                }
            };
            return new KillSwitchService(
                    repository,
                    Clock.fixed(Instant.parse("2026-07-14T00:00:00Z"), ZoneOffset.UTC)
            );
        }

        @Bean
        AccountCredentialRuntimeProperties accountCredentialRuntimeProperties() {
            AccountCredentialRuntimeProperties properties = new AccountCredentialRuntimeProperties();
            properties.setMasterKey("test-master-key");
            return properties;
        }
    }
}
