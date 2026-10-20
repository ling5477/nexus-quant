package com.guidinglight.nexusquant.app.account;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.application.service.AccountCommandService;
import com.guidinglight.nexusquant.account.application.service.ExchangeAccountCommandService;
import com.guidinglight.nexusquant.account.domain.AccountCommand;
import com.guidinglight.nexusquant.account.domain.AccountCommandReceipt;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.account.domain.port.AccountCommandReceiptRepository;
import com.guidinglight.nexusquant.account.infra.jdbc.JdbcAccountCommandReceiptRepository;
import com.guidinglight.nexusquant.app.NexusQuantApplication;
import com.guidinglight.nexusquant.auth.application.command.SeedUserCommand;
import com.guidinglight.nexusquant.auth.application.service.AuthSeedService;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 隔离PG16/JWT/正式账户服务证明命令回执的原子性和恢复，不构造经济事实。
 */
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
            "nq.account.performance.sampling-enabled=false",
            "nq.okx.adapter.stub-on-bootstrap-failure=false"
        })
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestExecutionListeners(
        listeners = AccountCommandPostgresIntegrationTest.Cleanup.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
class AccountCommandPostgresIntegrationTest {
    private static final String PASSWORD = "synthetic-account-command-fixture-password";
    private static JdbcTemplate control;
    private static String database, baseUrl, dbUser, dbPassword;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthSeedService seeds;
    @Autowired PasswordEncoder passwords;
    @Autowired AccountCommandService service;
    @Autowired ExchangeAccountCommandService accounts;
    @Autowired AccountCommandReceiptRepository receipts;
    @Autowired PlatformTransactionManager transactions;
    private long owner, other;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        baseUrl = System.getProperty("nq.postgres.smoke.url", "");
        dbUser = System.getProperty("nq.postgres.smoke.user", "");
        dbPassword = System.getProperty("nq.postgres.smoke.password", "");
        assertTrue(
                baseUrl.startsWith("jdbc:postgresql://")
                        && !dbUser.isBlank()
                        && !dbPassword.isBlank());
        URI uri = URI.create(baseUrl.substring("jdbc:".length()));
        assertTrue(
                Set.of("127.0.0.1", "localhost").contains(uri.getHost())
                        && uri.getRawQuery() == null
                        && uri.getFragment() == null
                        && uri.getPath().matches("/[A-Za-z0-9_]+"));
        control =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                baseUrl + "?connectTimeout=5&socketTimeout=30",
                                dbUser,
                                dbPassword));
        assertEquals(
                16,
                control.queryForObject(
                        "SELECT current_setting('server_version_num')::integer/10000",
                        Integer.class));
        database = "account_command_" + UUID.randomUUID().toString().replace("-", "");
        control.execute("CREATE DATABASE \"" + database + "\"");
        registry.add(
                "spring.datasource.url",
                () -> baseUrl.substring(0, baseUrl.lastIndexOf('/') + 1) + database);
        registry.add("spring.datasource.username", () -> dbUser);
        registry.add("spring.datasource.password", () -> dbPassword);
    }

    @BeforeEach
    void identities() {
        seeds.seedUsers(
                List.of(
                        new SeedUserCommand(
                                "command-owner",
                                passwords.encode(PASSWORD),
                                List.of("OPERATOR", "VIEWER"),
                                true),
                        new SeedUserCommand(
                                "command-other",
                                passwords.encode(PASSWORD),
                                List.of("OPERATOR", "VIEWER"),
                                true),
                        new SeedUserCommand(
                                "command-viewer",
                                passwords.encode(PASSWORD),
                                List.of("VIEWER"),
                                true)));
        owner =
                jdbc.queryForObject(
                        "SELECT id FROM users WHERE username='command-owner'", Long.class);
        other =
                jdbc.queryForObject(
                        "SELECT id FROM users WHERE username='command-other'", Long.class);
    }

    private AccountCommand create(String alias) {
        return new AccountCommand(AccountCommand.Type.CREATE, null, "OKX", "SIM", alias, null);
    }

    private AccountCommand state(AccountCommand.Type type, Long id) {
        return new AccountCommand(type, id, null, null, null, null);
    }

    @Test
    void formalApiReplayAndDurableReadRetainOriginalResultWithoutReapplying() throws Exception {
        String token = login("command-owner");
        UUID id = UUID.randomUUID();
        String alias = "receipt-" + id;
        var request = create(alias);
        var original =
                mapper.readTree(
                        mvc.perform(
                                        post(path(id))
                                                .header("Authorization", "Bearer " + token)
                                                .contentType("application/json")
                                                .content(mapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        long account = original.path("result").path("exchangeAccountId").asLong();
        assertTrue(original.path("result").path("legacyAccountId").asLong() > 0);
        service.execute(
                owner,
                UUID.randomUUID(),
                new AccountCommand(
                        AccountCommand.Type.UPDATE, account, null, null, alias + "-edited", null));
        var replay =
                mapper.readTree(
                        mvc.perform(
                                        post(path(id))
                                                .header("Authorization", "Bearer " + token)
                                                .contentType("application/json")
                                                .content(mapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        assertEquals(original, replay);
        assertEquals(
                alias + "-edited",
                jdbc.queryForObject(
                        "SELECT account_alias FROM exchange_accounts WHERE exchange_account_id=?",
                        String.class,
                        account));
        var reopened =
                new JdbcAccountCommandReceiptRepository(
                        new JdbcTemplate(jdbc.getDataSource()), mapper);
        assertEquals(alias, reopened.find(owner, id).orElseThrow().result().accountAlias());
        mvc.perform(get(path(id)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"));
        mvc.perform(
                        post(path(id))
                                .header("Authorization", "Bearer " + token)
                                .contentType("application/json")
                                .content(mapper.writeValueAsString(create(alias + "-different"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_COMMAND_IDENTITY_CONFLICT"));
        assertEquals(
                1L,
                jdbc.queryForObject(
                        "SELECT count(*) FROM account_command_receipts WHERE owner_user_id=? AND command_id=?",
                        Long.class,
                        owner,
                        id));
    }

    @Test
    void concurrentSameIdentityWaitsOnRealPgLockAndCreatesOneAccountAndReceipt() throws Exception {
        UUID id = UUID.randomUUID();
        var request = create("concurrent-" + id);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2);
            var release = new CountDownLatch(1);
            var first =
                    executor.submit(
                            () -> {
                                ready.countDown();
                                assertTrue(release.await(5, TimeUnit.SECONDS));
                                return service.execute(owner, id, request);
                            });
            var second =
                    executor.submit(
                            () -> {
                                ready.countDown();
                                assertTrue(release.await(5, TimeUnit.SECONDS));
                                return service.execute(owner, id, request);
                            });
            try {
                new TransactionTemplate(transactions)
                        .executeWithoutResult(
                                transaction -> {
                                    receipts.lock(owner, id);
                                    try {
                                        assertTrue(ready.await(5, TimeUnit.SECONDS));
                                        release.countDown();
                                        long deadline =
                                                System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                                        int waiting;
                                        do {
                                            waiting =
                                                    jdbc.queryForObject(
                                                            "SELECT count(*) FROM pg_locks l JOIN pg_stat_activity a ON a.pid=l.pid WHERE l.locktype='advisory' AND NOT l.granted AND a.datname=current_database()",
                                                            Integer.class);
                                            if (waiting >= 2) break;
                                            new CountDownLatch(1).await(10, TimeUnit.MILLISECONDS);
                                        } while (System.nanoTime() < deadline);
                                        assertEquals(
                                                2,
                                                waiting,
                                                "both workers must be blocked on the held database lock");
                                    } catch (InterruptedException failure) {
                                        Thread.currentThread().interrupt();
                                        throw new AssertionError(failure);
                                    }
                                });
                assertEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
                assertEquals(
                        1L,
                        jdbc.queryForObject(
                                "SELECT count(*) FROM exchange_accounts WHERE account_alias=?",
                                Long.class,
                                request.accountAlias()));
                assertEquals(
                        1L,
                        jdbc.queryForObject(
                                "SELECT count(*) FROM account_command_receipts WHERE owner_user_id=? AND command_id=?",
                                Long.class,
                                owner,
                                id));
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void receiptStorageFailureRollsBackAccountAndCanonicalSimIdentity() {
        var id = UUID.randomUUID();
        var request = create("rollback-" + id);
        long before = jdbc.queryForObject("SELECT count(*) FROM accounts", Long.class);
        AccountCommandReceiptRepository broken =
                new AccountCommandReceiptRepository() {
                    public void lock(Long user, UUID command) {
                        receipts.lock(user, command);
                    }

                    public Optional<AccountCommandReceipt> find(Long user, UUID command) {
                        return receipts.find(user, command);
                    }

                    public void save(AccountCommandReceipt receipt) {
                        throw new IllegalStateException("synthetic receipt failure");
                    }
                };
        var failed = new AccountCommandService(broken, accounts);
        assertThrows(
                IllegalStateException.class,
                () ->
                        new TransactionTemplate(transactions)
                                .execute(transaction -> failed.execute(owner, id, request)));
        assertEquals(
                0L,
                jdbc.queryForObject(
                        "SELECT count(*) FROM exchange_accounts WHERE account_alias=?",
                        Long.class,
                        request.accountAlias()));
        assertEquals(before, jdbc.queryForObject("SELECT count(*) FROM accounts", Long.class));
        assertTrue(receipts.find(owner, id).isEmpty());
        assertThrows(IllegalStateException.class, () -> receipts.lock(owner, id));
        assertNotNull(service.execute(owner, id, request));
    }

    @Test
    void heldCommandLockTimesOutWithoutAnyBusinessWriteAndSameIdentityCanRecover()
            throws Exception {
        UUID id = UUID.randomUUID();
        var request = create("timeout-" + id);
        try (var executor = Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactions)
                    .executeWithoutResult(
                            transaction -> {
                                receipts.lock(owner, id);
                                var attempt =
                                        executor.submit(() -> service.execute(owner, id, request));
                                try {
                                    var failure =
                                            assertThrows(
                                                    ExecutionException.class,
                                                    () -> attempt.get(14, TimeUnit.SECONDS));
                                    assertTrue(
                                            failure.getCause() instanceof QueryTimeoutException,
                                            failure.getCause().getClass().getName());
                                    assertEquals(
                                            0L,
                                            jdbc.queryForObject(
                                                    "SELECT count(*) FROM exchange_accounts WHERE account_alias=?",
                                                    Long.class,
                                                    request.accountAlias()));
                                    assertTrue(receipts.find(owner, id).isEmpty());
                                } finally {
                                    attempt.cancel(true);
                                }
                            });
        }
        assertNotNull(service.execute(owner, id, request));
    }

    @Test
    void ownersRolesInvalidIdentitiesAndMissingReceiptRemainSeparate() throws Exception {
        UUID id = UUID.randomUUID();
        var original = service.execute(owner, id, create("owned-" + id));
        mvc.perform(get(path(id))).andExpect(status().isUnauthorized());
        mvc.perform(get(path(id)).header("Authorization", "Bearer " + login("command-other")))
                .andExpect(status().isNotFound());
        mvc.perform(
                        get(path(UUID.randomUUID()))
                                .header("Authorization", "Bearer " + login("command-owner")))
                .andExpect(status().isNotFound());
        mvc.perform(
                        get("/api/exchange-accounts/commands/invalid-id")
                                .header("Authorization", "Bearer " + login("command-owner")))
                .andExpect(status().isBadRequest());
        UUID forbidden = UUID.randomUUID();
        mvc.perform(
                        post(path(forbidden))
                                .header("Authorization", "Bearer " + login("command-viewer"))
                                .contentType("application/json")
                                .content(mapper.writeValueAsString(create("viewer-forbidden"))))
                .andExpect(status().isForbidden());
        assertEquals(
                0L,
                jdbc.queryForObject(
                        "SELECT count(*) FROM account_command_receipts WHERE command_id=?",
                        Long.class,
                        forbidden));
        mvc.perform(
                        post(path(UUID.randomUUID()))
                                .header("Authorization", "Bearer " + login("command-other"))
                                .contentType("application/json")
                                .content(
                                        mapper.writeValueAsString(
                                                state(
                                                        AccountCommand.Type.DISABLE,
                                                        original.result().exchangeAccountId()))))
                .andExpect(status().isNotFound());
        assertEquals(
                "ACTIVE", accounts.enable(owner, original.result().exchangeAccountId()).status());
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM orders", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM trades", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM ledger_entries", Long.class));
    }

    @Test
    void stateCommandsAreHistoricalReceiptsAndDatabaseRejectsMutationAndWrongOwner() {
        var account =
                service.execute(owner, UUID.randomUUID(), create("states-" + UUID.randomUUID()))
                        .result();
        var chosen =
                service.execute(
                        owner,
                        UUID.randomUUID(),
                        state(AccountCommand.Type.SET_DEFAULT, account.exchangeAccountId()));
        assertTrue(chosen.result().isDefault());
        var disabled =
                service.execute(
                        owner,
                        UUID.randomUUID(),
                        state(AccountCommand.Type.DISABLE, account.exchangeAccountId()));
        assertEquals("DISABLED", disabled.result().status());
        assertFalse(disabled.result().isDefault());
        service.execute(
                owner,
                UUID.randomUUID(),
                state(AccountCommand.Type.ENABLE, account.exchangeAccountId()));
        assertEquals(
                "DISABLED",
                service.find(owner, disabled.commandId()).orElseThrow().result().status());
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbc.update(
                                "UPDATE account_command_receipts SET command_type='ENABLE' WHERE owner_user_id=? AND command_id=?",
                                owner,
                                disabled.commandId()));
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbc.update(
                                "DELETE FROM account_command_receipts WHERE owner_user_id=? AND command_id=?",
                                owner,
                                disabled.commandId()));
        var wrongOwner =
                new AccountCommandReceipt(
                        other,
                        UUID.randomUUID(),
                        disabled.type(),
                        disabled.requestSha256(),
                        disabled.result(),
                        disabled.completedAt());
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        new TransactionTemplate(transactions)
                                .executeWithoutResult(transaction -> receipts.save(wrongOwner)));
        var result = disabled.result();
        var invalidSnapshot =
                new ExchangeAccountSummary(
                        result.exchangeAccountId(),
                        result.legacyAccountId(),
                        null,
                        result.exchangeCode(),
                        result.tradeEnv(),
                        result.accountAlias(),
                        result.externalAccountRef(),
                        result.isDefault(),
                        result.status());
        var corrupted =
                new AccountCommandReceipt(
                        owner,
                        UUID.randomUUID(),
                        disabled.type(),
                        disabled.requestSha256(),
                        invalidSnapshot,
                        disabled.completedAt());
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        new TransactionTemplate(transactions)
                                .executeWithoutResult(transaction -> receipts.save(corrupted)));
        assertEquals(
                "ACTIVE",
                jdbc.queryForObject(
                        "SELECT status FROM exchange_accounts WHERE exchange_account_id=?",
                        String.class,
                        account.exchangeAccountId()));
    }

    @Test
    void upgradeFromV4PreservesExistingMetadataAndAddsOnlyNewReceiptCapability() {
        String target = "account_command_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        assertTrue(target.matches("account_command_upgrade_[0-9a-f]{32}"));
        control.execute("CREATE DATABASE \"" + target + "\"");
        var source =
                new DriverManagerDataSource(
                        baseUrl.substring(0, baseUrl.lastIndexOf('/') + 1) + target,
                        dbUser,
                        dbPassword);
        var upgrade = new JdbcTemplate(source);
        try {
            Flyway.configure()
                    .dataSource(source)
                    .locations("classpath:db/migration")
                    .target("4")
                    .load()
                    .migrate();
            upgrade.update(
                    "INSERT INTO users(username,password_hash) VALUES('upgrade-account-owner','synthetic-test-hash')");
            upgrade.update(
                    "INSERT INTO exchange_accounts(owner_user_id,exchange_code,trade_env,account_alias,is_default) SELECT id,'OKX','SIM','existing-default',true FROM users WHERE username='upgrade-account-owner'");
            var before =
                    upgrade.queryForList(
                            "SELECT * FROM exchange_accounts ORDER BY exchange_account_id");
            var flyway =
                    Flyway.configure()
                            .dataSource(source)
                            .locations("classpath:db/migration")
                            .target("5")
                            .load();
            flyway.migrate();
            flyway.validate();
            assertEquals(
                    before,
                    upgrade.queryForList(
                            "SELECT * FROM exchange_accounts ORDER BY exchange_account_id"));
            assertEquals("5", flyway.info().current().getVersion().toString());
            assertEquals(
                    0L,
                    upgrade.queryForObject(
                            "SELECT count(*) FROM account_command_receipts", Long.class));
        } finally {
            control.execute("DROP DATABASE \"" + target + "\" WITH (FORCE)");
        }
    }

    private static String path(UUID id) {
        return "/api/exchange-accounts/commands/" + id;
    }

    private String login(String name) throws Exception {
        var response =
                mvc.perform(
                                post("/api/auth/login")
                                        .contentType("application/json")
                                        .content(
                                                mapper.writeValueAsString(
                                                        Map.of(
                                                                "username",
                                                                name,
                                                                "password",
                                                                PASSWORD))))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return mapper.readTree(response).path("accessToken").asText();
    }

    public static class Cleanup extends AbstractTestExecutionListener {
        @Override
        public int getOrder() {
            return 999;
        }

        @Override
        public void afterTestClass(TestContext context) {
            if (control != null && database != null) {
                assertTrue(database.matches("account_command_[0-9a-f]{32}"));
                control.execute("DROP DATABASE \"" + database + "\" WITH (FORCE)");
            }
        }
    }
}
