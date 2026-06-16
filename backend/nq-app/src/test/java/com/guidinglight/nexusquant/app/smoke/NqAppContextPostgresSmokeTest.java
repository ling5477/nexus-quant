package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verifyNoInteractions;

import com.guidinglight.nexusquant.adapter.binance.ws.BinanceWsClient;
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
 * PostgreSQL/Flyway baseline without using local/test profiles, seed runners, scheduler business
 * execution, .env files, credential material, or a started web server. It uses
 * {@code WebEnvironment.MOCK} so the full servlet web application context (including the Spring
 * Security filter chain) is wired exactly as in production, but no HTTP port is opened and no
 * controller, scheduler job, or exchange adapter method is invoked.
 *
 * <p>Design evolution across the first three CI runs:
 * <ul>
 *   <li>Run 27590822405 failed creating the production {@code AdapterBackedTradingVenueGateway}: its
 *       constructor builds a venue-&gt;adapter routing map by calling {@code adapter.venue()} on every
 *       {@code TradingAdapter} bean during context refresh, and a bare {@code @MockitoBean} adapter
 *       returns a blank {@code venue()} at that moment ({@code IllegalArgumentException: venue must
 *       not be blank}). Stubbing a {@code @MockitoBean} in a JUnit lifecycle hook is too late because
 *       the eager singleton gateway is built before any {@code @BeforeEach} runs.</li>
 *   <li>Run 27592872701 got past the gateway but failed creating {@code securityFilterChain}:
 *       {@code webEnvironment = NONE} makes the application non-web, so {@code HttpSecurity} (only
 *       provided under {@code @ConditionalOnWebApplication(type = SERVLET)}) is absent and the
 *       production {@code SecurityConfiguration} cannot wire. Fixed by {@code WebEnvironment.MOCK}.</li>
 *   <li>Run 27596768301 then started the full context successfully (security included), but the test
 *       body failed with {@code NotAMock}: a {@code @TestConfiguration} bean override of the named
 *       {@code okxTradingAdapter}/{@code binanceTradingAdapter} beans is registration-order fragile and
 *       lost to the component-scanned real beans, so {@code @Autowired} resolved the real adapters and
 *       {@code verify(realAdapter, ...)} is illegal.</li>
 * </ul>
 *
 * <p>Final design: this smoke keeps exactly the composition that run 27596768301 proved loads — the
 * real OKX/Binance REST adapter beans (whose constructors perform no outbound and read no real
 * credential; the CI runner has none, and {@code OkxBootstrapNoOutboundLocalContextTest} proves
 * bootstrap stays offline) plus mocked WS clients. The smoke asserts the context loads and that no
 * WebSocket client is touched at startup. Adapter-level "no order placement" interception is not a
 * context-load concern and is deferred to the Batch 3 no-outbound guard; here it holds by construction
 * because scheduling, OKX recovery, catalog sync, and both WS bridges are disabled, so no business
 * code runs during context load. The datasource (bound to the Flyway-migrated CI PostgreSQL),
 * repositories, domain services, security, scheduler wiring, gateway, and adapters are all the real
 * production beans, so the smoke exercises the real composition root and does not mask wiring risk.
 */
@EnabledIfSystemProperty(named = "nq.app.context.smoke.required", matches = "true")
@ActiveProfiles("ci-app-smoke")
@SpringBootTest(classes = NexusQuantApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(properties = {
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

    // WS clients are mocked so the composition root constructs no real WebSocket client and opens no
    // socket at startup. The gateway does not consume these beans, so the override is reliable and a
    // blank-return mock cannot break context refresh (unlike the REST adapters, which the gateway reads
    // venue() from at refresh time).
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
        // The full servlet web composition root must start against the Flyway-migrated CI PostgreSQL
        // database, and no WebSocket client may connect while the context boots.
        assertNotNull(applicationContext);
        verifyNoInteractions(okxWsClient, binanceWsClient);
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
