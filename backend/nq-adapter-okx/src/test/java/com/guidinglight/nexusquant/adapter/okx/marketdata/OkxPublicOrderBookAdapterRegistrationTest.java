package com.guidinglight.nexusquant.adapter.okx.marketdata;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.okx.http.OkxHttpClient;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** 验证实际组件的注册条件；构造器使用无请求的受控client，不把注册成功当公开源联网证明。 */
class OkxPublicOrderBookAdapterRegistrationTest {
    @Test
    void onlyManualProfileAndBothExplicitTrueFlagsRegisterTheAdapter() {
        for (String profile :
                List.of(
                        "default",
                        "local",
                        "test",
                        "ci",
                        "paper",
                        "prod",
                        "scoped-okx-private-readonly",
                        "public-marketdata-manual")) {
            assertRegistration(profile, null, null, false);
            assertRegistration(profile, "true", null, false);
            assertRegistration(profile, null, "true", false);
            assertRegistration(profile, "false", "true", false);
            assertRegistration(profile, "true", "false", false);
            assertRegistration(profile, "invalid", "true", false);
            assertRegistration(profile, "true", "invalid", false);
            assertRegistration(profile, "true or true", "true", false);
            assertRegistration(profile, "true", "true", "public-marketdata-manual".equals(profile));
        }
    }

    private void assertRegistration(
            String profile, String outbound, String book, boolean expected) {
        var requests = new AtomicInteger();
        try (var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
                var context = new AnnotationConfigApplicationContext()) {
            var client =
                    new OkxHttpClient(
                            http, new ObjectMapper(), "http://127.0.0.1:0", Duration.ofSeconds(1)) {
                        @Override
                        public JsonNode getBounded(String path, String traceId, int maximumBytes) {
                            requests.incrementAndGet();
                            throw new AssertionError("组件注册不能发起行情请求");
                        }
                    };
            context.getEnvironment().setActiveProfiles(profile);
            var values = new HashMap<String, Object>();
            if (outbound != null) values.put("nq.public-marketdata.outbound.enabled", outbound);
            if (book != null) values.put("nq.public-marketdata.order-book.enabled", book);
            context.getEnvironment()
                    .getPropertySources()
                    .addFirst(new MapPropertySource("explicit-fixture", values));
            context.register(OkxPublicOrderBookAdapter.class);
            context.addBeanFactoryPostProcessor(
                    factory -> {
                        if (factory.containsBeanDefinition("okxPublicOrderBookAdapter")) {
                            var definition =
                                    (AbstractBeanDefinition)
                                            factory.getBeanDefinition("okxPublicOrderBookAdapter");
                            definition.setInstanceSupplier(
                                    () -> new OkxPublicOrderBookAdapter(client, Clock.systemUTC()));
                        }
                    });
            context.refresh();
            assertEquals(
                    expected ? 1 : 0,
                    context.getBeansOfType(OkxPublicOrderBookAdapter.class).size(),
                    profile + ":" + outbound + ":" + book);
            assertEquals(0, requests.get());
        }
    }
}
