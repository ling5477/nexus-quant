package com.guidinglight.nexusquant.app.config;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class RetiredRuntimeConfigurationGuardTest {

    private final AtomicInteger instantiated = new AtomicInteger();
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(RetiredRuntimeConfigurationGuard.class, RuntimeProbe.class)
            .withBean(AtomicInteger.class, () -> instantiated);

    @ParameterizedTest
    @ValueSource(strings = {"nq.gatew.okx-venue-rules.enabled", "nq.gatew.okx-private-readonly.enabled",
            "nq.phase4.secret", "NQ_GATEW_OKX_PRIVATE_READONLY_ENABLED", "NQ_GATED_VERIFY_ENABLED"})
    void retiredKeysRejectBeforeRuntimeBeansWithoutReadingValues(String key) {
        runner.withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                new org.springframework.core.env.EnumerablePropertySource<Object>("names-only", new Object()) {
                    @Override
                    public String[] getPropertyNames() { return new String[]{key}; }
                    @Override
                    public Object getProperty(String name) {
                        if (key.equals(name)) {
                            throw new AssertionError("retired configuration value must not be read");
                        }
                        return null;
                    }
                })).run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining(
                            "RETIRED_RUNTIME_CONFIGURATION_KEY / key=" + key);
                    assertThat(instantiated).hasValue(0);
                });
    }

    @org.junit.jupiter.api.Test
    void systemEnvironmentRelaxedKeyRejectsEvenWhenShadowedByStableConfiguration() {
        runner.withPropertyValues("nq.okx.private-readonly-diagnostics.enabled=true")
                .withInitializer(context -> context.getEnvironment().getPropertySources().addLast(
                        new org.springframework.core.env.SystemEnvironmentPropertySource("legacy-env",
                                java.util.Map.of("NQ_GATEW_OKX_PRIVATE_READONLY_ENABLED", "false"))))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining(
                            "RETIRED_RUNTIME_CONFIGURATION_KEY / key=NQ_GATEW_OKX_PRIVATE_READONLY_ENABLED");
                    assertThat(instantiated).hasValue(0);
                });
    }

    @org.junit.jupiter.api.Test
    void nestedPropertySourcesCannotHideRetiredKeys() {
        runner.withInitializer(context -> {
            var composite = new org.springframework.core.env.CompositePropertySource("nested");
            composite.addPropertySource(new org.springframework.core.env.MapPropertySource("retired",
                    java.util.Map.of("nq.gatew.any-key", "sensitive-value")));
            context.getEnvironment().getPropertySources().addLast(composite);
        }).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("key=nq.gatew.any-key")
                    .hasStackTraceContaining("RETIRED_RUNTIME_CONFIGURATION_KEY");
            assertThat(context.getStartupFailure().getMessage()).doesNotContain("sensitive-value");
            assertThat(instantiated).hasValue(0);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"gatea", "gate-d-verify", "gated-verify", "gatew", "gatew-okx-readonly-soak",
            "gatey-readonly-qualification", "gatez-pilot", "phase4-qualification", "freeze", "freeze-release"})
    void retiredProfilesRejectBeforeAnyRuntimeBean(String profile) {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles(profile)).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("RETIRED_RUNTIME_PROFILE");
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
