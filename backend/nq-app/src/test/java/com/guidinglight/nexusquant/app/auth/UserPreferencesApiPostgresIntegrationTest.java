package com.guidinglight.nexusquant.app.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.application.command.ExchangeAccountCreateCommand;
import com.guidinglight.nexusquant.account.application.service.ExchangeAccountCommandService;
import com.guidinglight.nexusquant.app.NexusQuantApplication;
import com.guidinglight.nexusquant.auth.application.command.SeedUserCommand;
import com.guidinglight.nexusquant.auth.application.service.AuthSeedService;
import com.guidinglight.nexusquant.auth.domain.UserPreferences;
import com.guidinglight.nexusquant.auth.domain.WatchlistSymbol;
import com.guidinglight.nexusquant.marketdata.infra.jdbc.JdbcBarProvenanceReadPort;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** 经过真实JWT过滤器和应用服务验证自身偏好写入，不给Viewer任何业务写权限。 */
@EnabledIfSystemProperty(named = "nq.postgres.smoke.required", matches = "true")
@ActiveProfiles("local")
@SpringBootTest(
        classes = NexusQuantApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
            "nq.instrument.catalog-sync.enabled=false",
            "nq.okx.recovery.enabled=false",
            "nq.okx.ws.enabled=false",
            "nq.binance.ws.enabled=false",
            "nq.env-safety.no-outbound=true",
            "nq.okx.adapter.stub-on-bootstrap-failure=false"
        })
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestExecutionListeners(
        listeners = UserPreferencesApiPostgresIntegrationTest.OwnedDatabaseCleanup.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
class UserPreferencesApiPostgresIntegrationTest {
    private static JdbcTemplate control;
    private static String database;
    private static final String FIXTURE_PASSWORD = "isolated-preferences-fixture-password";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AuthSeedService seeds;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;
    @Autowired ExchangeAccountCommandService accountCommands;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getProperty("nq.postgres.smoke.url", ""),
                user = System.getProperty("nq.postgres.smoke.user", ""),
                password = System.getProperty("nq.postgres.smoke.password", "");
        assertTrue(url.startsWith("jdbc:postgresql://") && !user.isBlank() && !password.isBlank());
        URI uri = URI.create(url.substring("jdbc:".length()));
        assertTrue(
                Set.of("127.0.0.1", "localhost").contains(uri.getHost())
                        && uri.getRawQuery() == null
                        && uri.getFragment() == null
                        && uri.getPath().matches("/[A-Za-z0-9_]+"));
        database = "preferences_api_" + UUID.randomUUID().toString().replace("-", "");
        control =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                url + "?connectTimeout=5&socketTimeout=30", user, password));
        assertEquals(
                16,
                control.queryForObject(
                        "SELECT current_setting('server_version_num')::integer / 10000",
                        Integer.class));
        control.execute("CREATE DATABASE \"" + database + "\"");
        String isolatedUrl = url.substring(0, url.lastIndexOf('/') + 1) + database;
        registry.add("spring.datasource.url", () -> isolatedUrl);
        registry.add("spring.datasource.username", () -> user);
        registry.add("spring.datasource.password", () -> password);
    }

    @BeforeEach
    void seedOnlySyntheticAuthIdentities() {
        seeds.seedUsers(
                List.of(
                        new SeedUserCommand(
                                "preferences-viewer",
                                passwords.encode(FIXTURE_PASSWORD),
                                List.of("VIEWER"),
                                true),
                        new SeedUserCommand(
                                "preferences-other",
                                passwords.encode(FIXTURE_PASSWORD),
                                List.of("VIEWER"),
                                true),
                        new SeedUserCommand(
                                "preferences-password-required",
                                passwords.encode(FIXTURE_PASSWORD),
                                List.of("VIEWER"),
                                true)));
        jdbc.update(
                "UPDATE users SET must_change_password=true WHERE username='preferences-password-required'");
    }

    @Test
    void viewerWritesOnlyOwnPreferencesAndStillCannotCreateBusinessAccount() throws Exception {
        String first = login("preferences-viewer"), other = login("preferences-other");
        String body =
                "{\"version\":0,\"locale\":\"en-US\",\"hideLoginDisclosures\":true,\"defaultMarket\":null,\"watchlist\":[],\"notificationsReadAt\":null,\"userId\":999999}";
        mvc.perform(
                        put("/api/user/preferences")
                                .header("Authorization", "Bearer " + first)
                                .contentType("application/json")
                                .content(body))
                .andExpect(status().isOk());
        var own =
                mapper.readTree(
                        mvc.perform(
                                        get("/api/user/preferences")
                                                .header("Authorization", "Bearer " + first))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        var peer =
                mapper.readTree(
                        mvc.perform(
                                        get("/api/user/preferences")
                                                .header("Authorization", "Bearer " + other))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        assertTrue(own.path("hideLoginDisclosures").asBoolean());
        assertEquals("en-US", own.path("locale").asText());
        assertFalse(peer.path("hideLoginDisclosures").asBoolean());
        assertEquals(0, peer.path("version").asInt());
        assertEquals(
                0L,
                jdbc.queryForObject(
                        "SELECT count(*) FROM user_preferences WHERE user_id=999999", Long.class));
        long accountCount =
                jdbc.queryForObject("SELECT count(*) FROM exchange_accounts", Long.class);
        mvc.perform(
                        post("/api/exchange-accounts")
                                .header("Authorization", "Bearer " + first)
                                .contentType("application/json")
                                .content(
                                        "{\"exchangeCode\":\"OKX\",\"tradeEnv\":\"SIM\",\"accountAlias\":\"not-authorized\"}"))
                .andExpect(status().isForbidden());
        assertEquals(
                accountCount,
                jdbc.queryForObject("SELECT count(*) FROM exchange_accounts", Long.class));
    }

    @Test
    void passwordChangeRequirementAndUnauthenticatedWritesRemainBlocked() throws Exception {
        mvc.perform(put("/api/user/preferences").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        String token = login("preferences-password-required");
        mvc.perform(get("/api/user/preferences").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(
                        put("/api/user/preferences")
                                .header("Authorization", "Bearer " + token)
                                .contentType("application/json")
                                .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void watchlistUsesTheFormalCatalogAndPersistsOrderAcrossSessions() throws Exception {
        seeds.seedUsers(
                List.of(
                        new SeedUserCommand(
                                "preferences-markets",
                                passwords.encode(FIXTURE_PASSWORD),
                                List.of("VIEWER"),
                                true)));
        jdbc.update(
                "INSERT INTO instrument_catalog(exchange_code,instrument_type,exchange_symbol,internal_symbol,base_asset,quote_asset,status,source,synced_at) "
                        + "VALUES ('OKX','SPOT','BTC-USDT','BTC-USDT','BTC','USDT','LIVE','ISOLATED_TEST',now()),"
                        + "('BINANCE','SPOT','ETHUSDT','ETH-USDT','ETH','USDT','TRADING','ISOLATED_TEST',now())");
        var btc = new WatchlistSymbol("OKX", "SPOT", "BTC-USDT");
        var eth = new WatchlistSymbol("BINANCE", "SPOT", "ETH-USDT");
        String token = login("preferences-markets");
        var initial = new UserPreferences(0, "zh-CN", false, eth, List.of(eth, btc), null);
        mvc.perform(
                        put("/api/user/preferences")
                                .header("Authorization", "Bearer " + token)
                                .contentType("application/json")
                                .content(mapper.writeValueAsString(initial)))
                .andExpect(status().isOk());
        String freshToken = login("preferences-markets");
        var reloaded =
                mapper.readValue(
                        mvc.perform(
                                        get("/api/user/preferences")
                                                .header("Authorization", "Bearer " + freshToken))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString(),
                        UserPreferences.class);
        assertEquals(List.of(eth, btc), reloaded.watchlist());
        assertEquals(eth, reloaded.defaultMarket());
        mvc.perform(
                        put("/api/user/preferences")
                                .header("Authorization", "Bearer " + freshToken)
                                .contentType("application/json")
                                .content(mapper.writeValueAsString(initial)))
                .andExpect(status().isConflict());
        var unsupported =
                new UserPreferences(
                        reloaded.version(),
                        "zh-CN",
                        false,
                        btc,
                        List.of(btc, new WatchlistSymbol("OKX", "SPOT", "INVALID-USDT")),
                        null);
        mvc.perform(
                        put("/api/user/preferences")
                                .header("Authorization", "Bearer " + freshToken)
                                .contentType("application/json")
                                .content(mapper.writeValueAsString(unsupported)))
                .andExpect(status().isBadRequest());
        jdbc.update(
                "UPDATE instrument_catalog SET status='SUSPENDED' WHERE internal_symbol='ETH-USDT'");
        var removal =
                new UserPreferences(reloaded.version(), "zh-CN", false, null, List.of(btc), null);
        mvc.perform(
                        put("/api/user/preferences")
                                .header("Authorization", "Bearer " + freshToken)
                                .contentType("application/json")
                                .content(mapper.writeValueAsString(removal)))
                .andExpect(status().isOk());
        var persisted =
                mapper.readValue(
                        mvc.perform(
                                        get("/api/user/preferences")
                                                .header("Authorization", "Bearer " + freshToken))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString(),
                        UserPreferences.class);
        assertNull(persisted.defaultMarket());
        assertEquals(List.of(btc), persisted.watchlist());
    }

    @Test
    void barProvenanceComesFromTheSameMarketAndPersistedBarIdentity() throws Exception {
        jdbc.update(
                "INSERT INTO marketdata_bars(exchange_code,market_type,symbol,\"interval\",open_time,close_time,open_price,high_price,low_price,close_price,volume,source,quality_status,available_at) "
                        + "VALUES ('OKX','SPOT','PROVENANCE-USDT','1h','2026-10-01T00:00:00Z','2026-10-01T00:59:59.999Z',1234567890.12345678,1234567891.12345678,1234567889.12345678,1234567890.12345678,0,'FIXTURE','OK','2026-10-01T01:02:03Z'),"
                        + "('BINANCE','SPOT','PROVENANCE-USDT','1h','2026-10-01T00:00:00Z','2026-10-01T00:59:59.999Z',1,1,1,1,0,'IMPORT','OK','2026-10-01T01:20:00Z')");
        String response =
                mvc.perform(
                                get("/api/marketdata/bars")
                                        .header(
                                                "Authorization",
                                                "Bearer " + login("preferences-viewer"))
                                        .param("exchangeCode", "OKX")
                                        .param("marketType", "SPOT")
                                        .param("symbol", "PROVENANCE-USDT")
                                        .param("interval", "1h")
                                        .param("startTime", "2026-10-01T00:00:00Z")
                                        .param("endTime", "2026-10-01T01:00:00Z"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        var bars = mapper.readTree(response);
        assertEquals(1, bars.size());
        assertEquals("FIXTURE", bars.get(0).path("source").asText());
        assertEquals("2026-10-01T01:02:03Z", bars.get(0).path("availableAt").asText());
        assertTrue(response.contains("1234567890.12345678"));
        var provenance = new JdbcBarProvenanceReadPort(jdbc);
        assertTrue(
                provenance
                        .read(
                                "OKX",
                                "SPOT",
                                "MISSING-USDT",
                                "1h",
                                List.of(Instant.parse("2026-10-01T00:00:00Z")))
                        .isEmpty());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        provenance.read(
                                "OKX",
                                "SPOT",
                                "PROVENANCE-USDT",
                                "1h",
                                Collections.nCopies(501, Instant.EPOCH)));
    }

    @Test
    void defaultAccountIsUniquePerOwnerAcrossVenuesAndSurvivesRelogin() throws Exception {
        seeds.seedUsers(
                List.of(
                        new SeedUserCommand(
                                "default-owner",
                                passwords.encode(FIXTURE_PASSWORD),
                                List.of("OPERATOR"),
                                true),
                        new SeedUserCommand(
                                "default-peer",
                                passwords.encode(FIXTURE_PASSWORD),
                                List.of("OPERATOR"),
                                true)));
        String owner = login("default-owner"), peer = login("default-peer");
        long first = createAccount(owner, "OKX", "default-first"),
                next = createAccount(owner, "BINANCE", "default-next"),
                untouched = createAccount(peer, "OKX", "default-peer");
        setDefault(owner, first);
        setDefault(peer, untouched);
        setDefault(owner, next);
        var profile =
                mapper.readTree(
                        mvc.perform(
                                        get("/api/auth/me")
                                                .header(
                                                        "Authorization",
                                                        "Bearer " + login("default-owner")))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        assertEquals(next, profile.path("defaultExchangeAccountId").asLong());
        assertEquals(
                1L,
                jdbc.queryForObject(
                        "SELECT count(*) FROM exchange_accounts WHERE owner_user_id=(SELECT id FROM users WHERE username='default-owner') AND is_default",
                        Long.class));
        assertTrue(
                jdbc.queryForObject(
                        "SELECT is_default FROM exchange_accounts WHERE exchange_account_id=?",
                        Boolean.class,
                        untouched));
        mvc.perform(
                        post("/api/exchange-accounts/" + untouched + "/set-default")
                                .header("Authorization", "Bearer " + owner))
                .andExpect(status().isNotFound());
        assertTrue(
                jdbc.queryForObject(
                        "SELECT is_default FROM exchange_accounts WHERE exchange_account_id=?",
                        Boolean.class,
                        next));
        mvc.perform(
                        post("/api/exchange-accounts/" + next + "/disable")
                                .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk());
        profile =
                mapper.readTree(
                        mvc.perform(
                                        get("/api/auth/me")
                                                .header(
                                                        "Authorization",
                                                        "Bearer " + login("default-owner")))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        assertTrue(profile.path("defaultExchangeAccountId").isNull());
        assertFalse(
                jdbc.queryForObject(
                        "SELECT is_default FROM exchange_accounts WHERE exchange_account_id=?",
                        Boolean.class,
                        first));
    }

    @Test
    void competingDefaultCommandsBothCompleteWithOnePersistedDefault() throws Exception {
        seeds.seedUsers(
                List.of(
                        new SeedUserCommand(
                                "default-concurrent",
                                passwords.encode(FIXTURE_PASSWORD),
                                List.of("OPERATOR"),
                                true)));
        long owner =
                jdbc.queryForObject(
                        "SELECT id FROM users WHERE username='default-concurrent'", Long.class);
        long first =
                accountCommands
                        .create(
                                owner,
                                new ExchangeAccountCreateCommand(
                                        "OKX", "SIM", "concurrent-first", null))
                        .exchangeAccountId();
        long second =
                accountCommands
                        .create(
                                owner,
                                new ExchangeAccountCreateCommand(
                                        "BINANCE", "SIM", "concurrent-second", null))
                        .exchangeAccountId();
        var ready = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a =
                    executor.submit(
                            () -> {
                                ready.countDown();
                                assertTrue(release.await(10, TimeUnit.SECONDS));
                                return accountCommands.setDefault(owner, first);
                            });
            var b =
                    executor.submit(
                            () -> {
                                ready.countDown();
                                assertTrue(release.await(10, TimeUnit.SECONDS));
                                return accountCommands.setDefault(owner, second);
                            });
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            release.countDown();
            assertTrue(a.get(10, TimeUnit.SECONDS).isDefault());
            assertTrue(b.get(10, TimeUnit.SECONDS).isDefault());
            assertEquals(
                    1L,
                    jdbc.queryForObject(
                            "SELECT count(*) FROM exchange_accounts WHERE owner_user_id=? AND is_default",
                            Long.class,
                            owner));
        } finally {
            release.countDown();
        }
        accountCommands.disable(owner, second);
        var before =
                jdbc.queryForObject(
                        "SELECT count(*) FROM exchange_accounts WHERE owner_user_id=? AND is_default",
                        Long.class,
                        owner);
        assertThrows(IllegalStateException.class, () -> accountCommands.setDefault(owner, second));
        assertEquals(
                before,
                jdbc.queryForObject(
                        "SELECT count(*) FROM exchange_accounts WHERE owner_user_id=? AND is_default",
                        Long.class,
                        owner));
    }

    private long createAccount(String token, String exchange, String name) throws Exception {
        String response =
                mvc.perform(
                                post("/api/exchange-accounts")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType("application/json")
                                        .content(
                                                mapper.writeValueAsString(
                                                        Map.of(
                                                                "exchangeCode",
                                                                exchange,
                                                                "tradeEnv",
                                                                "SIM",
                                                                "accountAlias",
                                                                name))))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return mapper.readTree(response).path("exchangeAccountId").asLong();
    }

    private void setDefault(String token, long id) throws Exception {
        mvc.perform(
                        post("/api/exchange-accounts/" + id + "/set-default")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private String login(String username) throws Exception {
        var response =
                mvc.perform(
                                post("/api/auth/login")
                                        .contentType("application/json")
                                        .content(
                                                mapper.writeValueAsString(
                                                        Map.of(
                                                                "username",
                                                                username,
                                                                "password",
                                                                FIXTURE_PASSWORD))))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return mapper.readTree(response).path("accessToken").asText();
    }

    public static final class OwnedDatabaseCleanup extends AbstractTestExecutionListener {
        @Override
        public int getOrder() {
            return 999;
        }

        @Override
        public void afterTestClass(TestContext context) {
            // after回调逆序执行：DirtiesContext先释放调度器与连接池，最后才删除所属数据库。
            if (control != null && database != null) {
                assertTrue(database.matches("preferences_api_[0-9a-f]{32}"));
                control.execute("DROP DATABASE \"" + database + "\" WITH (FORCE)");
            }
        }
    }
}
