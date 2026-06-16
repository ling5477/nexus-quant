package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verifyNoInteractions;

import com.guidinglight.nexusquant.adapter.binance.service.BinanceExchangeAdapter;
import com.guidinglight.nexusquant.adapter.binance.ws.BinanceWsClient;
import com.guidinglight.nexusquant.adapter.okx.service.OkxExchangeAdapter;
import com.guidinglight.nexusquant.adapter.okx.service.OkxWsClient;
import com.guidinglight.nexusquant.app.NexusQuantApplication;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Batch 2D CI-only nq-app context smoke against a Flyway-migrated PostgreSQL service database.
 *
 * <p>Why: this smoke proves the Spring composition root can start after the Batch 2A/2B/2C
 * PostgreSQL/Flyway baseline without using local/test profiles, seed runners, web controllers,
 * scheduler business execution, real exchange adapters, .env files, or credential material.
 */
@EnabledIfSystemProperty(named = "nq.app.context.smoke.required", matches = "true")
@ActiveProfiles("ci-app-smoke")
@SpringBootTest(classes = NexusQuantApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "spring.main.web-application-type=none",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=never",
        "spring.task.scheduling.enabled=false",
        "nq.auth.bootstrap-admin.enabled=false",
        "nq.instrument.catalog-sync.enabled=false",
        "nq.okx.recovery.enabled=false",
        "nq.okx.ws.enabled=false",
        "nq.okx.ws.smoke.force-reconnect-ms=0",
        "nq.binance.ws.enabled=false",
        "nq.binance.ws.smoke.force-reconnect-ms=0",
        "nq.okx.adapter.stub-on-bootstrap-failure=false",
        "nq.account.credentials.verification-mode=STRUCTURAL",
        "nq.account.credentials.master-key=ci-app-smoke-master-key-change-me-123456",
        "nq.security.issuer=nexus-quant-ci-app-smoke",
        "nq.security.secret=ci-app-smoke-change-me-change-me-123456",
        "nq.security.access-token-ttl=PT30M"
})
class NqAppContextPostgresSmokeTest {

    private static final String URL_PROPERTY = "nq.app.context.smoke.url";
    private static final String USER_PROPERTY = "nq.app.context.smoke.user";
    private static final String PASSWORD_PROPERTY = "nq.app.context.smoke.password";

    @Autowired
    private ApplicationContext applicationContext;

    @MockitoBean
    private OkxExchangeAdapter okxExchangeAdapter;

    @MockitoBean
    private BinanceExchangeAdapter binanceExchangeAdapter;

    @MockitoBean
    private OkxWsClient okxWsClient;

    @MockitoBean
    private BinanceWsClient binanceWsClient;

    @DynamicPropertySource
    static void registerCiDatasource(DynamicPropertyRegistry registry) {
        SmokeConfig config = SmokeConfig.fromSystemProperties();
        if (!config.configured()) {
            throw new IllegalStateException(
                    "Missing required CI app context smoke properties: "
                            + URL_PROPERTY + ", " + USER_PROPERTY + ", " + PASSWORD_PROPERTY
            );
        }
        registry.add("spring.datasource.url", config::url);
        registry.add("spring.datasource.username", config::user);
        registry.add("spring.datasource.password", config::password);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Test
    void shouldLoadNqAppContextWithoutSeedOrExchangeSideEffects() {
        assertNotNull(applicationContext);
        verifyNoInteractions(okxExchangeAdapter, binanceExchangeAdapter, okxWsClient, binanceWsClient);
    }

    private record SmokeConfig(String url, String user, String password) {
        static SmokeConfig fromSystemProperties() {
            return new SmokeConfig(
                    property(URL_PROPERTY),
                    property(USER_PROPERTY),
                    property(PASSWORD_PROPERTY)
            );
        }

        boolean configured() {
            return !url.isBlank() && !user.isBlank() && !password.isBlank();
        }
    }

    private static String property(String name) {
        return System.getProperty(name, "").trim();
    }
}
