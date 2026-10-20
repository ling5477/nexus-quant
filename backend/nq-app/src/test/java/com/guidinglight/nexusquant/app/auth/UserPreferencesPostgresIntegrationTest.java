package com.guidinglight.nexusquant.app.auth;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.auth.application.command.SeedUserCommand;
import com.guidinglight.nexusquant.auth.application.service.AuthSeedService;
import com.guidinglight.nexusquant.auth.domain.UserPreferences;
import com.guidinglight.nexusquant.auth.domain.WatchlistSymbol;
import com.guidinglight.nexusquant.auth.infra.jdbc.JdbcAuthUserRepository;
import com.guidinglight.nexusquant.auth.infra.jdbc.JdbcUserPreferencesRepository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** 沿用工程的隔离PG入口；fresh/升级、持久化、用户分隔及CAS竞争均在真实PG16上证明。 */
@EnabledIfSystemProperty(named = "nq.postgres.smoke.required", matches = "true")
class UserPreferencesPostgresIntegrationTest {
    @Test
    void freshAndUpgradePreserveUsersAndBoundPreferences() throws Exception {
        for (boolean upgrade : new boolean[] {false, true}) {
            withSchema(
                    (jdbc, admin, schema) -> {
                        if (upgrade) migrate(admin, schema, "1");
                        else migrate(admin, schema, null);
                        var seeds = new AuthSeedService(new JdbcAuthUserRepository(jdbc));
                        seeds.seedUsers(
                                List.of(
                                        new SeedUserCommand(
                                                "preferences-owner",
                                                "synthetic-test-hash",
                                                List.of("VIEWER"),
                                                true),
                                        new SeedUserCommand(
                                                "preferences-other",
                                                "synthetic-test-hash",
                                                List.of("VIEWER"),
                                                true)));
                        if (upgrade) migrate(admin, schema, null);
                        long owner =
                                jdbc.queryForObject(
                                        "SELECT id FROM users WHERE username='preferences-owner'",
                                        Long.class);
                        long other =
                                jdbc.queryForObject(
                                        "SELECT id FROM users WHERE username='preferences-other'",
                                        Long.class);
                        var repository =
                                new JdbcUserPreferencesRepository(jdbc, new ObjectMapper());
                        var market = new WatchlistSymbol("OKX", "SPOT", "BTC-USDT");
                        Instant at = Instant.parse("2026-10-01T02:03:04.123456Z");
                        var saved =
                                repository
                                        .save(
                                                owner,
                                                new UserPreferences(
                                                        0,
                                                        "en-US",
                                                        true,
                                                        market,
                                                        List.of(market),
                                                        at))
                                        .orElseThrow();
                        assertEquals(1, saved.version());
                        var reopened =
                                new JdbcUserPreferencesRepository(
                                        new JdbcTemplate(jdbc.getDataSource()), new ObjectMapper());
                        assertEquals(saved, reopened.find(owner).orElseThrow());
                        assertTrue(reopened.find(other).isEmpty());
                        assertTrue(reopened.save(owner, UserPreferences.defaults()).isEmpty());
                        assertEquals(saved, reopened.find(owner).orElseThrow());
                        assertEquals(
                                2L, jdbc.queryForObject("SELECT count(*) FROM users", Long.class));
                        // fresh和升级路径都应用当前候选V27；V5是偏好引入版本，不能冒充最终迁移版本。
                        assertEquals(
                                "27",
                                jdbc.queryForObject(
                                        "SELECT max(version::integer)::text FROM flyway_schema_history WHERE success",
                                        String.class));
                        assertThrows(
                                DataIntegrityViolationException.class,
                                () ->
                                        jdbc.update(
                                                "UPDATE user_preferences SET locale='invalid' WHERE user_id=?",
                                                owner));
                        assertThrows(
                                DataIntegrityViolationException.class,
                                () ->
                                        jdbc.update(
                                                "UPDATE user_preferences SET watchlist='{}'::jsonb WHERE user_id=?",
                                                owner));
                        assertEquals(saved, reopened.find(owner).orElseThrow());
                    });
        }
    }

    @Test
    void simultaneousInitialAndExistingSavesHaveOneWinner() throws Exception {
        withSchema(
                (jdbc, admin, schema) -> {
                    migrate(admin, schema, null);
                    new AuthSeedService(new JdbcAuthUserRepository(jdbc))
                            .seedUsers(
                                    List.of(
                                            new SeedUserCommand(
                                                    "preferences-owner",
                                                    "synthetic-test-hash",
                                                    List.of("VIEWER"),
                                                    true)));
                    long owner =
                            jdbc.queryForObject(
                                    "SELECT id FROM users WHERE username='preferences-owner'",
                                    Long.class);
                    var repository = new JdbcUserPreferencesRepository(jdbc, new ObjectMapper());
                    for (long version : new long[] {0, 1}) {
                        CountDownLatch ready = new CountDownLatch(2),
                                release = new CountDownLatch(1);
                        try (var executor = Executors.newFixedThreadPool(2)) {
                            final long expected = version;
                            var first =
                                    executor.submit(
                                            () -> {
                                                ready.countDown();
                                                assertTrue(release.await(10, TimeUnit.SECONDS));
                                                return repository
                                                        .save(
                                                                owner,
                                                                new UserPreferences(
                                                                        expected, "zh-CN", true,
                                                                        null, List.of(), null))
                                                        .isPresent();
                                            });
                            var second =
                                    executor.submit(
                                            () -> {
                                                ready.countDown();
                                                assertTrue(release.await(10, TimeUnit.SECONDS));
                                                return repository
                                                        .save(
                                                                owner,
                                                                new UserPreferences(
                                                                        expected, "en-US", false,
                                                                        null, List.of(), null))
                                                        .isPresent();
                                            });
                            assertTrue(ready.await(10, TimeUnit.SECONDS));
                            release.countDown();
                            assertNotEquals(
                                    first.get(10, TimeUnit.SECONDS),
                                    second.get(10, TimeUnit.SECONDS));
                            assertEquals(
                                    expected + 1, repository.find(owner).orElseThrow().version());
                            assertEquals(
                                    1L,
                                    jdbc.queryForObject(
                                            "SELECT count(*) FROM user_preferences WHERE user_id=?",
                                            Long.class,
                                            owner));
                        } finally {
                            release.countDown();
                        }
                    }
                });
    }

    @Test
    void upgradeKeepsUniqueDefaultsAndRequiresReselectionForAmbiguousScopes() throws Exception {
        withSchema(
                (jdbc, admin, schema) -> {
                    migrate(admin, schema, "2");
                    new AuthSeedService(new JdbcAuthUserRepository(jdbc))
                            .seedUsers(
                                    List.of(
                                            new SeedUserCommand(
                                                    "default-ambiguous",
                                                    "synthetic-test-hash",
                                                    List.of("VIEWER"),
                                                    true),
                                            new SeedUserCommand(
                                                    "default-unique",
                                                    "synthetic-test-hash",
                                                    List.of("VIEWER"),
                                                    true),
                                            new SeedUserCommand(
                                                    "default-disabled",
                                                    "synthetic-test-hash",
                                                    List.of("VIEWER"),
                                                    true)));
                    jdbc.update(
                            "INSERT INTO exchange_accounts(owner_user_id,exchange_code,trade_env,account_alias,is_default,status) "
                                    + "SELECT id,'OKX','SIM','old-okx',true,'ACTIVE' FROM users WHERE username='default-ambiguous'");
                    jdbc.update(
                            "INSERT INTO exchange_accounts(owner_user_id,exchange_code,trade_env,account_alias,is_default,status) "
                                    + "SELECT id,'BINANCE','SIM','old-binance',true,'ACTIVE' FROM users WHERE username='default-ambiguous'");
                    jdbc.update(
                            "INSERT INTO exchange_accounts(owner_user_id,exchange_code,trade_env,account_alias,is_default,status) "
                                    + "SELECT id,'OKX','SIM','unique-default',true,'ACTIVE' FROM users WHERE username='default-unique'");
                    jdbc.update(
                            "INSERT INTO exchange_accounts(owner_user_id,exchange_code,trade_env,account_alias,is_default,status) "
                                    + "SELECT id,'OKX','SIM','disabled-default',true,'DISABLED' FROM users WHERE username='default-disabled'");
                    var ids =
                            jdbc.queryForList(
                                    "SELECT exchange_account_id FROM exchange_accounts ORDER BY exchange_account_id",
                                    Long.class);
                    migrate(admin, schema, null);
                    assertEquals(
                            ids,
                            jdbc.queryForList(
                                    "SELECT exchange_account_id FROM exchange_accounts ORDER BY exchange_account_id",
                                    Long.class));
                    assertEquals(
                            1L,
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM exchange_accounts WHERE is_default",
                                    Long.class));
                    assertEquals(
                            "unique-default",
                            jdbc.queryForObject(
                                    "SELECT account_alias FROM exchange_accounts WHERE is_default",
                                    String.class));
                    assertThrows(
                            DataIntegrityViolationException.class,
                            () ->
                                    jdbc.update(
                                            "UPDATE exchange_accounts SET is_default=true WHERE owner_user_id=(SELECT id FROM users WHERE username='default-ambiguous')"));
                    assertEquals(3L, jdbc.queryForObject("SELECT count(*) FROM users", Long.class));
                });
    }

    private static void migrate(JdbcTemplate admin, String schema, String target) {
        var configuration =
                Flyway.configure()
                        .dataSource(admin.getDataSource())
                        .locations("classpath:db/migration");
        if (target != null) configuration.target(target);
        Flyway flyway = configuration.load();
        flyway.migrate();
        flyway.validate();
    }

    private static void withSchema(DatabaseProof proof) throws Exception {
        String url = System.getProperty("nq.postgres.smoke.url", ""),
                user = System.getProperty("nq.postgres.smoke.user", ""),
                password = System.getProperty("nq.postgres.smoke.password", "");
        assertTrue(
                url.startsWith("jdbc:postgresql://") && !user.isBlank() && !password.isBlank(),
                "isolated PostgreSQL required");
        URI uri = URI.create(url.substring("jdbc:".length()));
        assertTrue(
                Set.of("127.0.0.1", "localhost").contains(uri.getHost())
                        && uri.getRawQuery() == null
                        && uri.getFragment() == null
                        && uri.getPath().matches("/[A-Za-z0-9_]+"));
        String database = "user_preferences_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate control =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                url + "?connectTimeout=5&socketTimeout=30", user, password));
        assertEquals(
                16,
                control.queryForObject(
                        "SELECT current_setting('server_version_num')::integer / 10000",
                        Integer.class));
        assertTrue(database.matches("user_preferences_[0-9a-f]{32}"));
        control.execute("CREATE DATABASE \"" + database + "\"");
        String isolatedUrl = url.substring(0, url.lastIndexOf('/') + 1) + database;
        JdbcTemplate jdbc =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                isolatedUrl + "?connectTimeout=5&socketTimeout=30",
                                user,
                                password));
        // V1的函数明确使用public，故使用整个独立数据库，不改写历史迁移以适应测试schema。
        try {
            proof.run(jdbc, jdbc, database);
        } finally {
            control.execute("DROP DATABASE \"" + database + "\" WITH (FORCE)");
        }
    }

    private interface DatabaseProof {
        void run(JdbcTemplate jdbc, JdbcTemplate admin, String schema) throws Exception;
    }
}
