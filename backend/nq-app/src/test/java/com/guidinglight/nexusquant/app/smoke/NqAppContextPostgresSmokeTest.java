package com.guidinglight.nexusquant.app.smoke;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.guidinglight.nexusquant.adapter.binance.service.BinanceExchangeAdapter;
import com.guidinglight.nexusquant.adapter.binance.ws.BinanceWsClient;
import com.guidinglight.nexusquant.adapter.okx.service.OkxExchangeAdapter;
import com.guidinglight.nexusquant.adapter.okx.service.OkxWsClient;
import com.guidinglight.nexusquant.app.NexusQuantApplication;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
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
 * execution, real exchange adapters, .env files, credential material, or a started web server. It
 * uses {@code WebEnvironment.MOCK} so the full servlet web application context (including the Spring
 * Security filter chain) is wired exactly as in production, but no HTTP port is opened and no
 * controller is invoked.
 *
 * <p>First CI run (run 27590822405) failed while creating the production
 * {@code AdapterBackedTradingVenueGateway}: its constructor builds a venue-&gt;adapter routing map by
 * calling {@code adapter.venue()} on every {@code TradingAdapter} bean during context refresh. A bare
 * {@code @MockitoBean} for the OKX/Binance adapters returns a blank {@code venue()} at that moment, so
 * the gateway throws {@code IllegalArgumentException: venue must not be blank}. Stubbing a
 * {@code @MockitoBean} in a JUnit lifecycle hook is too late, because the eager singleton gateway is
 * already built before any {@code @BeforeEach} runs.
 *
 * <p>Fix: the two REST exchange adapters are supplied as pre-stubbed Mockito mocks from
 * {@link StubbedExchangeAdapterConfig}, so a non-blank, distinct, CI-only fake venue is available
 * exactly when the production gateway needs it — without constructing a real adapter, reading any
 * credential, or contacting an exchange. This only substitutes the outermost exchange I/O edges; the
 * datasource (bound to the Flyway-migrated CI PostgreSQL), repositories, domain services, security,
 * scheduler wiring, and the gateway itself remain the real production beans, so the smoke still
 * exercises the real composition root and does not mask app-context wiring risk.
 *
 * <p>Second CI run (run 27592872701) got past the gateway but failed creating {@code securityFilterChain}:
 * with {@code webEnvironment = NONE} the application is non-web, so {@code HttpSecurity} (provided only
 * by {@code @ConditionalOnWebApplication(type = SERVLET)}) is absent and the production
 * {@code SecurityConfiguration} cannot wire. NexusQuant is a servlet web app, so the composition root
 * must load as a servlet web context. The fix switches to {@code WebEnvironment.MOCK}, matching the
 * existing {@code local}-profile full-context tests, which loads the servlet web + security wiring
 * without starting a server.
 */
@EnabledIfSystemProperty(named = "nq.app.context.smoke.required", matches = "true")
@ActiveProfiles("ci-app-smoke")
@SpringBootTest(classes = NexusQuantApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(properties = {
        // Enable narrow bean override so StubbedExchangeAdapterConfig can replace the two named
        // ExchangeAdapterConfiguration adapter beans by name. Local full-context tests load the same
        // composition root WITHOUT this flag, so it cannot hide a real duplicate-definition problem;
        // it only enables the two intentional CI-only adapter substitutions below.
        "spring.main.allow-bean-definition-overriding=true",
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

    // CI-only fake venues. The gateway only needs a non-blank, distinct venue per TradingAdapter to
    // build its routing map; these are deliberately NOT "OKX"/"BINANCE" so the smoke can never resolve
    // a real exchange route to a stubbed adapter.
    private static final String CI_FAKE_OKX_VENUE = "CI-SMOKE-FAKE-OKX";
    private static final String CI_FAKE_BINANCE_VENUE = "CI-SMOKE-FAKE-BINANCE";

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private OkxExchangeAdapter okxExchangeAdapter;

    @Autowired
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

        // The production AdapterBackedTradingVenueGateway only reads venue() at startup to build its
        // routing map. Booting the context must not place / cancel / query any order on an exchange
        // adapter, and must not connect any WebSocket client. venue() is a legitimate composition-time
        // call, so it is intentionally not asserted as "no interaction" here.
        verify(okxExchangeAdapter, never()).placeOrder(any());
        verify(okxExchangeAdapter, never()).cancelOrder(any());
        verify(okxExchangeAdapter, never()).getOrder(any());
        verify(binanceExchangeAdapter, never()).placeOrder(any());
        verify(binanceExchangeAdapter, never()).cancelOrder(any());
        verify(binanceExchangeAdapter, never()).getOrder(any());
        verifyNoInteractions(okxWsClient, binanceWsClient);
    }

    /**
     * Pre-stubbed CI-only exchange adapter mocks.
     *
     * <p>These override the two named {@code ExchangeAdapterConfiguration} adapter beans
     * ({@code okxTradingAdapter}, {@code binanceTradingAdapter}). A pre-stubbed mock is required
     * instead of a bare {@code @MockitoBean} because the gateway calls {@code venue()} during context
     * refresh, before any test lifecycle hook could stub a mock. No real adapter is constructed, no
     * credential is read, and no exchange endpoint is contacted.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class StubbedExchangeAdapterConfig {

        @Bean
        OkxExchangeAdapter okxTradingAdapter() {
            OkxExchangeAdapter adapter = mock(OkxExchangeAdapter.class);
            when(adapter.venue()).thenReturn(CI_FAKE_OKX_VENUE);
            return adapter;
        }

        @Bean
        BinanceExchangeAdapter binanceTradingAdapter() {
            BinanceExchangeAdapter adapter = mock(BinanceExchangeAdapter.class);
            when(adapter.venue()).thenReturn(CI_FAKE_BINANCE_VENUE);
            return adapter;
        }
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
