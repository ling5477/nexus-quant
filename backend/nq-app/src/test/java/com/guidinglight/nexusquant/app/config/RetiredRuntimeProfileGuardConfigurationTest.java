package com.guidinglight.nexusquant.app.config;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class RetiredRuntimeProfileGuardConfigurationTest {

    private final AtomicInteger instantiated = new AtomicInteger();
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(RetiredRuntimeProfileGuardConfiguration.class, RuntimeProbe.class)
            .withBean(AtomicInteger.class, () -> instantiated);

    @ParameterizedTest
    @ValueSource(strings = {"gatea", "gate-d-verify", "gated-verify", "gatew", "gatew-okx-readonly-soak",
            "gatey-readonly-qualification", "gatez-pilot", "phase4-qualification", "freeze", "freeze-release"})
    void retiredProfilesRejectBeforeAnyRuntimeBean(String profile) {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles(profile)).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("RETIRED_STAGE_RUNTIME_PROFILE");
            assertThat(instantiated).hasValue(0);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"gatea", "gatey-readonly-qualification", "freeze"})
    void defaultProfilesCannotBypassRetirement(String profile) {
        runner.withInitializer(context -> context.getEnvironment().setDefaultProfiles(profile)).run(context -> {
            assertThat(context).hasFailed();
            assertThat(instantiated).hasValue(0);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "test", "ci", "prod", "paper", "public-marketdata-manual",
            "okx-private-readonly-diagnostics", "scoped-okx-private-readonly"})
    void capabilityAndEnvironmentProfilesKeepTheirComposition(String profile) {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles(profile)).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(instantiated).hasValue(1);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class RuntimeProbe {
        @Bean
        Object runtimeSideEffectProbe(AtomicInteger instantiated) {
            instantiated.incrementAndGet();
            return new Object();
        }
    }
}
