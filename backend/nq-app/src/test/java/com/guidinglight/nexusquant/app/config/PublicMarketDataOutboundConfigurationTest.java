package com.guidinglight.nexusquant.app.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.adapter.api.publicmarketdata.transport.DisabledPublicMarketDataOutboundClient;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.transport.JdkPublicMarketDataOutboundClient;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.port.PublicMarketDataOutboundClient;
import com.guidinglight.nexusquant.adapter.okx.marketdata.OkxClosedBarMarketFeed;

import java.net.URI;
import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * PublicMarketDataOutboundConfigurationTest 固化 GateO O-1 manual profile / feature flag 装配边界。
 *
 * <p>Why: 默认 local/test/CI/paper/freeze 不得构造真实 HTTP client；只有
 * public-marketdata-manual profile 且 flag=true 才可构造受 policy 保护的 JDK client。测试不访问真实
 * 交易所、不读取 credential、不启用 LIVE/RealClient/real provider。</p>
 */
class PublicMarketDataOutboundConfigurationTest {

    private ApplicationContextRunner configuredFeedRunner(Map<String, Object> environment) {
        return new ApplicationContextRunner()
                .withUserConfiguration(PublicMarketDataOutboundConfiguration.class, OkxClosedBarMarketFeed.class)
                .withInitializer(context -> {
                    context.getEnvironment().setActiveProfiles("public-marketdata-manual");
                    context.getEnvironment().getPropertySources().addFirst(
                            new SystemEnvironmentPropertySource("fixture-env", environment));
                    try {
                        var loader = new YamlPropertySourceLoader();
                        for (String name : new String[]{"application.yml", "application-public-marketdata-manual.yml"}) {
                            for (var source : loader.load(name, new ClassPathResource(name))) {
                                context.getEnvironment().getPropertySources().addLast(source);
                            }
                        }
                    } catch (IOException failure) {
                        throw new IllegalStateException("配置夹具读取失败", failure);
                    }
                })
                .withPropertyValues("nq.public-marketdata.outbound.enabled=true");
    }

    @Test
    void shippedYamlPreservesOfficialFeedDefaultWithoutAddingOutboundAddress() {
        configuredFeedRunner(Map.of()).run(context -> {
            assertEquals(null, context.getEnvironment().getProperty("nq.public-marketdata.outbound.base-url"));
            assertEquals(URI.create("https://www.okx.com"),
                    ReflectionTestUtils.getField(context.getBean(OkxClosedBarMarketFeed.class), "origin"));
        });
    }

    @Test
    void canonicalEnvironmentOriginWinsAndLegacyEnvironmentRemainsFallback() {
        configuredFeedRunner(Map.of("NQ_PUBLIC_MARKETDATA_OUTBOUND_BASE_URL", "http://127.0.0.1:32123",
                "NQ_PUBLIC_MARKETDATA_BASE_URL", "http://localhost:32124")).run(context ->
                assertEquals(URI.create("http://127.0.0.1:32123"),
                        ReflectionTestUtils.getField(context.getBean(OkxClosedBarMarketFeed.class), "origin")));
        configuredFeedRunner(Map.of("NQ_PUBLIC_MARKETDATA_BASE_URL", "http://localhost:32124")).run(context -> {
            assertEquals(URI.create("http://localhost:32124"),
                    ReflectionTestUtils.getField(context.getBean(OkxClosedBarMarketFeed.class), "origin"));
            assertEquals(URI.create("http://localhost:32124"),
                    ReflectionTestUtils.getField(context.getBean(PublicMarketDataOutboundClient.class), "baseUri"));
        });
    }

    @Test
    void malformedConfiguredOriginNeverAppearsInStartupExceptionChain() {
        String value = "https://user:REDACTION_SENTINEL@invalid host?secret=REDACTION_SENTINEL";
        contextRunner
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("public-marketdata-manual"))
                .withPropertyValues("nq.public-marketdata.outbound.enabled=true",
                        "nq.public-marketdata.outbound.base-url=" + value)
                .run(context -> {
                    Throwable failure = context.getStartupFailure();
                    assertTrue(failure != null);
                    boolean classified = false;
                    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                        assertFalse(cause.toString().contains("REDACTION_SENTINEL"));
                        assertFalse(cause.toString().contains(value));
                        classified |= cause.toString().contains("PUBLIC_MARKETDATA_ORIGIN_INVALID");
                    }
                    assertTrue(classified);
                });
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PublicMarketDataOutboundConfiguration.class);

    @Test
    void defaultConfigurationShouldUseDisabledFallbackClient() {
        contextRunner.run(context -> {
            assertTrue(context.containsBean("disabledPublicMarketDataOutboundClient"));
            assertInstanceOf(
                    DisabledPublicMarketDataOutboundClient.class,
                    context.getBean(PublicMarketDataOutboundClient.class));
            assertFalse(context.containsBean("publicMarketDataOutboundClient"));
        });
    }

    @Test
    void manualProfileWithFlagFalseShouldStillUseDisabledFallbackClient() {
        contextRunner
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("public-marketdata-manual"))
                .withPropertyValues("nq.public-marketdata.outbound.enabled=false")
                .run(context -> {
                    assertTrue(context.containsBean("disabledPublicMarketDataOutboundClient"));
                    assertInstanceOf(
                            DisabledPublicMarketDataOutboundClient.class,
                            context.getBean(PublicMarketDataOutboundClient.class));
                    assertFalse(context.containsBean("publicMarketDataOutboundClient"));
                });
    }

    @Test
    void flagTrueWithoutManualProfileShouldNotConstructAnyOutboundClient() {
        contextRunner
                .withPropertyValues("nq.public-marketdata.outbound.enabled=true")
                .run(context -> assertThrows(
                        NoSuchBeanDefinitionException.class,
                        () -> context.getBean(PublicMarketDataOutboundClient.class)));
    }

    @Test
    void manualProfileWithFlagTrueShouldConstructJdkHttpClientOnly() {
        contextRunner
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("public-marketdata-manual"))
                .withPropertyValues(
                        "nq.public-marketdata.outbound.enabled=true",
                        "nq.public-marketdata.outbound.base-url=http://127.0.0.1:65535",
                        "nq.public-marketdata.outbound.connect-timeout=PT3S",
                        "nq.public-marketdata.outbound.read-timeout=PT5S",
                        "nq.public-marketdata.outbound.total-request-timeout=PT8S",
                        "nq.public-marketdata.outbound.max-retries=2")
                .run(context -> {
                    assertTrue(context.containsBean("publicMarketDataOutboundClient"));
                    assertInstanceOf(
                            JdkPublicMarketDataOutboundClient.class,
                            context.getBean(PublicMarketDataOutboundClient.class));
                    assertFalse(context.containsBean("disabledPublicMarketDataOutboundClient"));
                });
    }
}
