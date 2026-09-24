package com.guidinglight.nexusquant.adapter.okx.marketdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.okx.http.OkxHttpClient;
import com.guidinglight.nexusquant.adapter.okx.signing.OkxRequestSigner;
import com.guidinglight.nexusquant.adapter.okx.ws.OkxWsSmokeRunner;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OkxHistoricalKlineAdapterRegistrationTest {

    @Test
    void historicalAdapterRequiresManualPublicOutboundCapability() {
        for (String profile : List.of("default", "local", "test", "ci", "paper", "prod",
                "scoped-okx-private-readonly", "public-marketdata-manual")) {
            assertHistoricalAdapterRegistration(profile, null, false);
            assertHistoricalAdapterRegistration(profile, "false", false);
            assertHistoricalAdapterRegistration(profile, "invalid", false);
            assertHistoricalAdapterRegistration(profile, "true", "public-marketdata-manual".equals(profile));
        }
    }

    private void assertHistoricalAdapterRegistration(String profile, String enabled, boolean expected) {
        List<HttpClient> fixtureClients = new ArrayList<>();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles(profile);
            if (enabled != null) {
                context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("capability",
                        Map.of("nq.public-marketdata.outbound.enabled", enabled)));
            }
            context.scan("com.guidinglight.nexusquant.adapter.okx");
            // 保留真实组件与条件发现，并隔离构造过程；此夹具不会发送请求。
            context.addBeanFactoryPostProcessor(factory -> {
                if (factory.containsBeanDefinition("okxHistoricalKlineAdapter")) {
                    AbstractBeanDefinition definition = (AbstractBeanDefinition)
                            factory.getBeanDefinition("okxHistoricalKlineAdapter");
                    definition.setInstanceSupplier(() -> {
                        HttpClient client = HttpClient.newHttpClient();
                        fixtureClients.add(client);
                        return new OkxHistoricalKlineAdapter(new OkxHttpClient(client, new ObjectMapper(),
                                "http://127.0.0.1:0", Duration.ofSeconds(1), new OkxRequestSigner(),
                                () -> "fixture", null, false));
                    });
                }
            });
            context.refresh();

            assertEquals(expected ? 1 : 0, context.getBeansOfType(OkxHistoricalKlineAdapter.class).size(),
                    profile + ":" + enabled);
            assertTrue(context.getBeansOfType(OkxWsSmokeRunner.class).isEmpty());
            assertEquals(expected ? 1 : 0, fixtureClients.size());
        } finally {
            fixtureClients.forEach(HttpClient::close);
        }
    }
}
