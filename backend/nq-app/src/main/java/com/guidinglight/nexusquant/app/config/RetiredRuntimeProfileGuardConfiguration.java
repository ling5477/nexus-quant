package com.guidinglight.nexusquant.app.config;

import java.util.Arrays;
import java.util.regex.Pattern;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Rejects development-stage identities before runtime beans are instantiated.
 * Retiring a profile must not silently select the ordinary adapter composition.
 * This guard neither selects a profile nor changes production configuration validation.
 */
@Configuration(proxyBeanMethods = false)
public class RetiredRuntimeProfileGuardConfiguration {

    private static final Pattern STAGE_PROFILE = Pattern.compile(
            "(?i)^(?:gate[-_]?(?:[a-z]|audit)(?:[-_0-9].*)?|phase[-_]?[0-9].*|freeze(?:[-_].*)?)$");

    @Bean
    static BeanFactoryPostProcessor retiredRuntimeProfileGuard(Environment environment) {
        return beanFactory -> {
            String[] active = environment.getActiveProfiles();
            String[] effective = active.length == 0 ? environment.getDefaultProfiles() : active;
            if (Arrays.stream(effective).anyMatch(profile -> STAGE_PROFILE.matcher(profile).matches())) {
                throw new IllegalStateException("RETIRED_STAGE_RUNTIME_PROFILE / select a supported runtime mode");
            }
        };
    }
}
