package com.guidinglight.nexusquant.app.config.strategyrelease;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.strategy.infra.artifact.ServerControlledStrategyArtifactBindingResolver;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseArtifactBindingResolver;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Strategy Release server-controlled artifact binding 的 composition-root 装配。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StrategyReleaseArtifactProperties.class)
public class StrategyReleaseArtifactConfiguration {

    @Bean
    public StrategyReleaseArtifactBindingResolver strategyReleaseArtifactBindingResolver(
            StrategyReleaseArtifactProperties properties,
            ObjectMapper objectMapper
    ) {
        return new ServerControlledStrategyArtifactBindingResolver(properties.getTrustedRoot(), objectMapper);
    }
}
