package com.guidinglight.nexusquant.app.config.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialRepository;
import com.guidinglight.nexusquant.account.infra.okx.readonly.OkxPrivateCredentialExecutor;
import com.guidinglight.nexusquant.account.infra.okx.readonly.OkxPrivateReadonlyProbeService;
import com.guidinglight.nexusquant.adapter.api.service.TradingAdapter;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadTransport;
import com.guidinglight.nexusquant.adapter.okx.service.OkxWsClient;
import com.guidinglight.nexusquant.app.config.ExchangeAdapterConfiguration;
import com.guidinglight.nexusquant.risk.service.KillSwitchEngageCommand;
import com.guidinglight.nexusquant.risk.service.KillSwitchScope;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import com.guidinglight.nexusquant.risk.service.KillSwitchState;
import com.guidinglight.nexusquant.risk.service.KillSwitchStateRepository;
import com.guidinglight.nexusquant.risk.service.KillSwitchStatus;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class OkxPrivateReadOnlyDiagnosticsConfigurationTest {

    private static final String STABLE_PREFIX = "nq.okx.private-readonly-diagnostics";
    private static final String LEGACY_PREFIX = "nq.gatew.okx-private-readonly";

    @Test
    void defaultProfileDoesNotCreatePrivateTransportOrDecryptor() {
        try (AnnotationConfigApplicationContext context = context(null, STABLE_PREFIX, false, false)) {
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
            assertTrue(context.getBeansOfType(TradingAdapter.class).isEmpty());
            assertTrue(context.getBeansOfType(OkxWsClient.class).isEmpty());
        }
    }

    @Test
    void legacyProfileAndKeysStillCreateReadOnlyComponents() {
        try (AnnotationConfigApplicationContext context = context(
                "gatew-okx-readonly",
                LEGACY_PREFIX,
                true,
                false
        )) {
            assertFalse(context.getBeansOfType(OkxPrivateReadTransport.class).isEmpty());
            assertFalse(context.getBeansOfType(OkxPrivateCredentialExecutor.class).isEmpty());
            assertFalse(context.getBeansOfType(OkxPrivateReadonlyProbeService.class).isEmpty());
        }
    }

    @Test
    void scopedExplicitProfileStillRequiresExactReadOnlyFlagsAndCreatesNoTradingAdapter() {
        try (AnnotationConfigApplicationContext context = context(
                "scoped-okx-private-readonly", STABLE_PREFIX, true, false)) {
            assertFalse(context.getBeansOfType(OkxPrivateReadonlyProbeService.class).isEmpty());
            assertTrue(context.getBeansOfType(TradingAdapter.class).isEmpty());
            assertTrue(context.getBeansOfType(OkxWsClient.class).isEmpty());
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
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        if (profile != null) {
            context.getEnvironment().setActiveProfiles(profile);
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
        if (live != null) {
            properties.put("nq.env-safety.live-enabled", live);
        }
        properties.putAll(overrides);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("readonly-diagnostics-test", properties));
        context.register(
                OkxPrivateReadOnlyDiagnosticsConfiguration.class,
                ExchangeAdapterConfiguration.class,
                Dependencies.class
        );
        context.refresh();
        return context;
    }

    private static void assertPrivateBeansAbsent(AnnotationConfigApplicationContext context) {
        assertTrue(context.getBeansOfType(OkxPrivateReadTransport.class).isEmpty());
        assertTrue(context.getBeansOfType(OkxPrivateCredentialExecutor.class).isEmpty());
        assertTrue(context.getBeansOfType(OkxPrivateReadonlyProbeService.class).isEmpty());
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
