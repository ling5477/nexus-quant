package com.guidinglight.nexusquant.demo.registration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class PublicDemoConfigurationTest {
    @Test void rejectsBootstrapLivePrivateProvidersAndLocalIdentityRegardlessOfRegistrationSwitch() {
        for(String key:java.util.List.of("nq.auth.bootstrap-admin.enabled","nq.env-safety.live-enabled",
                "nq.env-safety.real-provider-enabled","nq.runtime.minimal-live-pilot.enabled",
                "nq.runtime.minimal-live-pilot.order-submission-enabled","nq.live-control.exact-pilot-binding.enabled",
                "nq.okx.ws.enabled","nq.runtime.provider-observation.enabled","nq.okx.private-readonly-diagnostics.enabled")) {
            var env=new MockEnvironment().withProperty(key,"true");
            assertThrows(IllegalStateException.class,()->check(env),key);
        }
        var env=new MockEnvironment();env.setActiveProfiles("local");
        assertThrows(IllegalStateException.class,()->check(env));
        assertDoesNotThrow(()->check(new MockEnvironment()));
        assertThrows(IllegalStateException.class,()->PublicDemoConfiguration.demoDistributionGuard(
                new MockEnvironment().withProperty("nq.env-safety.live-enabled","false"),
                java.util.Map.of("NQ_OKX_ENV","real")).postProcessBeanFactory(new DefaultListableBeanFactory()));
        for(String venue:java.util.List.of("NQ_OKX_ENV","NQ_BINANCE_ENV")) {
            assertThrows(IllegalStateException.class,()->PublicDemoConfiguration.demoDistributionGuard(
                    new MockEnvironment(),java.util.Map.of(venue," REAL ")).postProcessBeanFactory(new DefaultListableBeanFactory()));
        }
        for(String key:java.util.List.of("NQ_OKX_API_KEY","NQ_OKX_API_SECRET","NQ_OKX_API_PASSPHRASE",
                "NQ_BINANCE_API_KEY","NQ_BINANCE_API_SECRET")) {
            assertThrows(IllegalStateException.class,()->PublicDemoConfiguration.demoDistributionGuard(
                    new MockEnvironment(),java.util.Map.of(key,"synthetic-test-only")).postProcessBeanFactory(new DefaultListableBeanFactory()));
        }
    }

    @Test void requiresStableSecretAndLocalMailFacilityAndSafeVerificationUrl() {
        var env=new MockEnvironment().withProperty("nq.demo.registration-enabled","true");
        assertThrows(IllegalStateException.class,()->check(env));
        env.setProperty("nq.demo.token-secret","synthetic-only-secret-that-is-long-enough");
        env.setProperty("nq.demo.verification-base-url","http://127.0.0.1:5173/verify-email");
        assertDoesNotThrow(()->check(env));
        env.setProperty("nq.demo.mail-dispatch-enabled","true");env.setProperty("spring.mail.host","smtp.example.test");
        assertThrows(IllegalStateException.class,()->check(env));
        env.setProperty("spring.mail.host","127.0.0.1");
        env.setProperty("nq.demo.verification-base-url","http://external.example.test/verify-email");
        assertThrows(IllegalStateException.class,()->check(env));
    }

    @Test void outboxCanRebuildTokensAcrossRestartButDatabaseNonceCannotAuthenticateAlone() {
        var id=java.util.UUID.randomUUID();
        var before=new DemoTokenCodec("synthetic-only-secret-that-is-long-enough");
        var after=new DemoTokenCodec("synthetic-only-secret-that-is-long-enough");
        assertEquals(before.token(id),after.token(id));
        assertNotEquals(before.token(id),new DemoTokenCodec("a-different-independent-synthetic-secret").token(id));
        assertTrue(DemoTokenCodec.digest(before.token(id)).matches("[0-9a-f]{64}"));
        assertFalse(DemoTokenCodec.same(before.token(id),id.toString()));
    }
    private static void check(MockEnvironment env){PublicDemoConfiguration.demoDistributionGuard(env,java.util.Map.of()).postProcessBeanFactory(new DefaultListableBeanFactory());}
}
