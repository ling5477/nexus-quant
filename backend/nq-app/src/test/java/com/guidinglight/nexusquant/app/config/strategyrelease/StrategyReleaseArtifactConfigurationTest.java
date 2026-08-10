package com.guidinglight.nexusquant.app.config.strategyrelease;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseArtifactBindingResolver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Strategy Release artifact typed configuration 的缺省关闭与显式绑定回归。 */
class StrategyReleaseArtifactConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(StrategyReleaseArtifactConfiguration.class)
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void shouldRemainUnconfiguredByDefaultWithoutBlockingApplicationContext() {
        contextRunner.run(context -> {
            assertTrue(context.isRunning());
            assertNull(context.getBean(StrategyReleaseArtifactProperties.class).getTrustedRoot());
            assertTrue(context.containsBean("strategyReleaseArtifactBindingResolver"));
        });
    }

    @Test
    void shouldBindOnlyExplicitServerSideTrustedRoot() {
        contextRunner
                .withPropertyValues("nq.strategy-release.artifacts.trusted-root=C:/nq-artifacts")
                .run(context -> assertEquals(
                        "C:/nq-artifacts",
                        context.getBean(StrategyReleaseArtifactProperties.class).getTrustedRoot()
                ));
    }

    @Test
    void shouldExposeResolverThroughInternalPortOnly() {
        contextRunner.run(context -> assertEquals(
                1,
                context.getBeansOfType(StrategyReleaseArtifactBindingResolver.class).size()
        ));
    }
}
