package com.guidinglight.nexusquant.app.config.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.account.infra.gatew.OkxPrivateCredentialExecutor;
import com.guidinglight.nexusquant.account.infra.gatew.OkxPrivateReadonlyProbeService;
import com.guidinglight.nexusquant.adapter.api.service.TradingAdapter;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadTransport;
import com.guidinglight.nexusquant.adapter.okx.service.OkxWsClient;
import com.guidinglight.nexusquant.app.config.ExchangeAdapterConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class GateWOkxPrivateReadonlyConfigurationTest {

    @Test
    void defaultProfileDoesNotCreatePrivateTransportOrDecryptor() {
        try (AnnotationConfigApplicationContext context = context(null, false, false)) {
            assertPrivateBeansAbsent(context);
        }
    }

    @Test
    void explicitProfileWithFlagFalseDoesNotCreatePrivateTransportOrDecryptor() {
        try (AnnotationConfigApplicationContext context = context("gatew-okx-readonly", false, false)) {
            assertPrivateBeansAbsent(context);
        }
    }

    @Test
    void explicitProfileAndFlagCreateOnlyReadonlyComponentsWithoutStartupNetwork() {
        try (AnnotationConfigApplicationContext context = context("gatew-okx-readonly", true, false)) {
            assertFalse(context.getBeansOfType(OkxPrivateReadTransport.class).isEmpty());
            assertFalse(context.getBeansOfType(OkxPrivateCredentialExecutor.class).isEmpty());
            assertFalse(context.getBeansOfType(OkxPrivateReadonlyProbeService.class).isEmpty());
            assertTrue(context.getBeansOfType(TradingAdapter.class).isEmpty());
            assertTrue(context.getBeansOfType(OkxWsClient.class).isEmpty());
        }
    }

    @Test
    void liveTrueFailsClosedByNotCreatingPrivateComponents() {
        try (AnnotationConfigApplicationContext context = context("gatew-okx-readonly", true, true)) {
            assertPrivateBeansAbsent(context);
        }
    }

    @Test
    void missingOrInvalidLivePropertyFailsClosed() {
        try (AnnotationConfigApplicationContext missing = context("gatew-okx-readonly", true, null);
             AnnotationConfigApplicationContext invalid = context("gatew-okx-readonly", true, "invalid")) {
            assertPrivateBeansAbsent(missing);
            assertPrivateBeansAbsent(invalid);
        }
    }

    private static AnnotationConfigApplicationContext context(String profile, boolean enabled, Object live) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        if (profile != null) {
            context.getEnvironment().setActiveProfiles(profile);
        }
        Map<String, Object> properties = new HashMap<>();
        properties.put("nq.gatew.okx-private-readonly.enabled", enabled);
        if (live != null) {
            properties.put("nq.env-safety.live-enabled", live);
        }
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("gatew-test", properties));
        context.register(
                GateWOkxPrivateReadonlyConfiguration.class,
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
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean JdbcTemplate jdbcTemplate() { return mock(JdbcTemplate.class); }
        @Bean ExchangeAccountRepository exchangeAccountRepository() { return mock(ExchangeAccountRepository.class); }
        @Bean AccountCredentialRuntimeProperties accountCredentialRuntimeProperties() {
            AccountCredentialRuntimeProperties properties = new AccountCredentialRuntimeProperties();
            properties.setMasterKey("test-master-key");
            return properties;
        }
    }
}
