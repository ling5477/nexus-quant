package com.guidinglight.nexusquant.app.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.adapter.api.publicmarketdata.DisabledPublicMarketDataOutboundClient;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.JdkPublicMarketDataOutboundClient;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.PublicMarketDataOutboundClient;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * PublicMarketDataOutboundConfigurationTest 固化 GateO O-1 manual profile / feature flag 装配边界。
 *
 * <p>Why: 默认 local/test/CI/paper/freeze 不得构造真实 HTTP client；只有
 * public-marketdata-manual profile 且 flag=true 才可构造受 policy 保护的 JDK client。测试不访问真实
 * 交易所、不读取 credential、不启用 LIVE/RealClient/real provider。</p>
 */
class PublicMarketDataOutboundConfigurationTest {

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
