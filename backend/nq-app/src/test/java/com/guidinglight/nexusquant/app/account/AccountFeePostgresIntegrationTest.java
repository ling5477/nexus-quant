package com.guidinglight.nexusquant.app.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.application.command.ExchangeAccountCreateCommand;
import com.guidinglight.nexusquant.account.application.command.ExchangeAccountCredentialUpsertCommand;
import com.guidinglight.nexusquant.account.application.exception.AccountFeeSyncException;
import com.guidinglight.nexusquant.account.application.service.AccountFeeModelService;
import com.guidinglight.nexusquant.account.application.service.AccountFeeService;
import com.guidinglight.nexusquant.account.application.service.ExchangeAccountCommandService;
import com.guidinglight.nexusquant.account.application.service.ExchangeAccountCredentialCommandService;
import com.guidinglight.nexusquant.account.application.service.ExchangeAccountCredentialVerificationService;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialSummary;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.account.domain.fee.AccountFeeObservation;
import com.guidinglight.nexusquant.account.domain.fee.AccountFeeQuote;
import com.guidinglight.nexusquant.account.domain.fee.AccountFeeSourceIdentity;
import com.guidinglight.nexusquant.account.domain.port.AccountFeeReadPort;
import com.guidinglight.nexusquant.account.domain.port.AccountFeeRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.account.infra.binance.readonly.JdbcBinanceAccountFeeReadPort;
import com.guidinglight.nexusquant.account.infra.jdbc.JdbcAccountFeeRepository;
import com.guidinglight.nexusquant.adapter.binance.fee.BinanceCommissionHttpReader;
import com.guidinglight.nexusquant.app.NexusQuantApplication;
import com.guidinglight.nexusquant.app.config.account.AccountCredentialRuntimeProperties;
import com.guidinglight.nexusquant.auth.application.command.SeedUserCommand;
import com.guidinglight.nexusquant.auth.application.service.AuthSeedService;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
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

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** 正式JWT/服务/PG证明派生费率回执；唯一供应方替身仅在测试上下文装配，不解密或连接真实账户。 */
@EnabledIfSystemProperty(named = "nq.postgres.smoke.required", matches = "true")
@ActiveProfiles("local")
@SpringBootTest(
        classes = {
            NexusQuantApplication.class,
            AccountFeePostgresIntegrationTest.FixtureConfiguration.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
            "nq.env-safety.no-outbound=true",
            "nq.instrument.catalog-sync.enabled=false",
            "nq.okx.recovery.enabled=false",
            "nq.okx.ws.enabled=false",
            "nq.binance.ws.enabled=false",
            "nq.account.performance.sampling-enabled=false",
            "nq.okx.adapter.stub-on-bootstrap-failure=false"
        })
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestExecutionListeners(
        listeners = AccountFeePostgresIntegrationTest.Cleanup.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
class AccountFeePostgresIntegrationTest {
    private static final String PASSWORD = "synthetic-fee-test-login-password";
    private static JdbcTemplate control;
    private static String database, baseUrl, dbUser, dbPassword;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AuthSeedService seeds;
    @Autowired PasswordEncoder passwords;
    @Autowired AccountFeeService service;
    @Autowired AccountFeeRepository repository;
    @Autowired ExchangeAccountCommandService accounts;
    @Autowired ExchangeAccountCredentialCommandService credentials;
    @Autowired FixtureSource source;
    @Autowired ExchangeAccountCredentialVerificationService verification;
    @Autowired ExchangeAccountRepository accountRows;
    @Autowired ExchangeAccountCredentialRepository credentialRows;
    @Autowired AccountCredentialRuntimeProperties runtimeCredentials;
    @Autowired KillSwitchService kill;
    @Autowired AccountFeeModelService models;
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
                        "SELECT current_setting('server_version_num')::int/10000", Integer.class));
        database = "account_fees_" + UUID.randomUUID().toString().replace("-", "");
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
                                "fee-owner",
                                passwords.encode(PASSWORD),
                                List.of("OPERATOR", "VIEWER"),
                                true),
                        new SeedUserCommand(
                                "fee-other",
                                passwords.encode(PASSWORD),
                                List.of("OPERATOR", "VIEWER"),
                                true),
                        new SeedUserCommand(
                                "fee-viewer",
                                passwords.encode(PASSWORD),
                                List.of("VIEWER"),
                                true)));
        owner = jdbc.queryForObject("SELECT id FROM users WHERE username='fee-owner'", Long.class);
        other = jdbc.queryForObject("SELECT id FROM users WHERE username='fee-other'", Long.class);
        source.calls.set(0);
        source.enabled = true;
        source.fail = false;
        source.beforeReturn = () -> {};
        source.fixedAt = null;
        source.delegate = null;
    }

    private ExchangeAccountSummary account() {
        return accounts.create(
                owner,
                new ExchangeAccountCreateCommand(
                        "OKX", "SIM", "fee-fixture-" + UUID.randomUUID(), null));
    }

    private long credential(ExchangeAccountSummary account) {
        return credentials
                .upsert(
                        owner,
                        account.exchangeAccountId(),
                        new ExchangeAccountCredentialUpsertCommand(
                                "OKX_API_V5",
                                "synthetic-fee-key",
                                "synthetic-fee-secret",
                                "synthetic-fee-passphrase",
                                null),
                        1)
                .credentialId();
    }

    @Test
    void formalApiSnapshotReplayAndReopenedReadKeepOriginalPrecisionAndSignedRates()
            throws Exception {
        var account = account();
        long credential = credential(account);
        UUID id = UUID.randomUUID();
        String token = login("fee-owner");
        String path = path(account.exchangeAccountId(), id);
        String body =
                mapper.writeValueAsString(Map.of("credentialId", credential, "symbol", "BTC-USDT"));
        var first =
                mapper.readTree(
                        mvc.perform(
                                        post(path)
                                                .header("Authorization", "Bearer " + token)
                                                .contentType("application/json")
                                                .content(body))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        assertEquals(
                0, new BigDecimal("-0.0002").compareTo(first.path("makerCostRate").decimalValue()));
        assertEquals(
                0, new BigDecimal("0.001").compareTo(first.path("takerCostRate").decimalValue()));
        assertFalse(first.has("inputFingerprint"));
        assertFalse(first.has("ownerUserId"));
        source.fail = true;
        var replay =
                mapper.readTree(
                        mvc.perform(
                                        post(path)
                                                .header("Authorization", "Bearer " + token)
                                                .contentType("application/json")
                                                .content(body))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        var result =
                mapper.readTree(
                        mvc.perform(get(path).header("Authorization", "Bearer " + token))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        assertEquals(first, replay);
        assertEquals(first, result);
        assertEquals(1, source.calls.get());
        assertEquals(
                repository.findOperation(owner, id),
                new JdbcAccountFeeRepository(jdbc).findOperation(owner, id));
        assertEquals(
                1L,
                jdbc.queryForObject(
                        "SELECT count(*) FROM account_fee_observations WHERE operation_id=?",
                        Long.class,
                        id));
        economicsRemainEmpty();
    }

    /** 真实JWT/PG/密文/JIT/本地HTTP贯通；两种签名都由本地供应方验证，未取得真实私有联网授权。 */
    @Test
    void binanceHmacAndEd25519KeepCompleteTermsAndReplayWithoutRereading() throws Exception {
        for (boolean ed : List.of(false, true))
            try (var fixture = new BinanceFixture(ed)) {
                var account =
                        accounts.create(
                                owner,
                                new ExchangeAccountCreateCommand(
                                        "BINANCE",
                                        "LIVE",
                                        "native-fee-" + UUID.randomUUID(),
                                        null));
                var credential = fixture.credential(account);
                fixture.install(account, credential);
                UUID operation = UUID.randomUUID();
                String token = login("fee-owner");
                String body =
                        mapper.writeValueAsString(
                                Map.of(
                                        "credentialId",
                                        credential.credentialId(),
                                        "symbol",
                                        "BTC-USDT"));
                var response =
                        mapper.readTree(
                                mvc.perform(
                                                post(path(account.exchangeAccountId(), operation))
                                                        .header("Authorization", "Bearer " + token)
                                                        .contentType("application/json")
                                                        .content(body))
                                        .andExpect(status().isOk())
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsString());
                assertEquals("BINANCE_PRIVATE_SPOT_COMMISSION", response.path("source").asText());
                assertEquals("BINANCE_POSITIVE_COST_V1", response.path("rateConvention").asText());
                for (String missing :
                        List.of(
                                "makerProviderRate",
                                "takerProviderRate",
                                "makerCostRate",
                                "takerCostRate",
                                "tier")) assertTrue(response.path(missing).isNull());
                assertEquals("HTTP_RESPONSE_DATE", response.path("providerTimeBasis").asText());
                assertEquals(
                        "-0.000000000001",
                        response.path("commissionTerms").path("standard").path("maker").asText());
                assertEquals(
                        "0.000000000000",
                        response.path("commissionTerms").path("tax").path("taker").asText());
                assertEquals(2, fixture.signatures.get());
                assertEquals(3, fixture.paths.size());
                var saved = repository.findOperation(owner, operation).orElseThrow();
                assertEquals(saved.sourceSha256(), AccountFeeSourceIdentity.digest(saved));
                assertEquals(
                        saved,
                        new JdbcAccountFeeRepository(jdbc)
                                .findOperation(owner, operation)
                                .orElseThrow());
                source.enabled = false;
                var replay =
                        mapper.readTree(
                                mvc.perform(
                                                post(path(account.exchangeAccountId(), operation))
                                                        .header("Authorization", "Bearer " + token)
                                                        .contentType("application/json")
                                                        .content(body))
                                        .andExpect(status().isOk())
                                        .andReturn()
                                        .getResponse()
                                        .getContentAsString());
                assertEquals(response, replay);
                assertEquals(3, fixture.paths.size());
                assertFalse(
                        service.history(owner, account.exchangeAccountId(), "BTC-USDT", null, 20)
                                .synchronizationAvailable());
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                models.select(
                                        owner,
                                        saved.observationId(),
                                        account.exchangeAccountId(),
                                        "BINANCE",
                                        "LIVE",
                                        "BTC-USDT",
                                        Instant.now()));
                source.enabled = true;
                economicsRemainEmpty();
            }
    }

    @Test
    void binanceRemotePermissionFailureHasNoSuccessfulObservation() throws Exception {
        try (var fixture = new BinanceFixture(false)) {
            var account =
                    accounts.create(
                            owner,
                            new ExchangeAccountCreateCommand(
                                    "BINANCE", "LIVE", "permissions-" + UUID.randomUUID(), null));
            var credential = fixture.credential(account);
            fixture.install(account, credential);
            fixture.withdraw = true;
            UUID operation = UUID.randomUUID();
            var failure =
                    assertThrows(
                            AccountFeeSyncException.class,
                            () ->
                                    service.sync(
                                            owner,
                                            account.exchangeAccountId(),
                                            operation,
                                            credential.credentialId(),
                                            "BTC-USDT"));
            assertEquals("FEE_PRIVATE_READ_REMOTE_PERMISSION_DENIED", failure.getMessage());
            assertNull(failure.getCause());
            assertEquals(
                    List.of("/api/v3/time", "/sapi/v1/account/apiRestrictions"), fixture.paths);
            assertTrue(repository.findOperation(owner, operation).isEmpty());
            economicsRemainEmpty();
        }
    }

    @Test
    void binanceBoundaryChangeAndUnverifiedVersionStopWithoutRecordingSuccess() throws Exception {
        try (var fixture = new BinanceFixture(false)) {
            var account =
                    accounts.create(
                            owner,
                            new ExchangeAccountCreateCommand(
                                    "BINANCE", "LIVE", "boundary-" + UUID.randomUUID(), null));
            var credential = fixture.credential(account);
            fixture.install(account, credential);
            // 只改变本随机测试库的安全版本，状态始终ENGAGED；不解除任何已有运行的kill switch。
            fixture.beforeCommission =
                    () ->
                            jdbc.update(
                                    "UPDATE kill_switch_states SET version=version+1 WHERE scope='GLOBAL_TRADING'");
            UUID operation = UUID.randomUUID();
            assertEquals(
                    "FEE_KILL_STATE_CHANGED",
                    assertThrows(
                                    AccountFeeSyncException.class,
                                    () ->
                                            service.sync(
                                                    owner,
                                                    account.exchangeAccountId(),
                                                    operation,
                                                    credential.credentialId(),
                                                    "BTC-USDT"))
                            .getMessage());
            assertTrue(repository.findOperation(owner, operation).isEmpty());
            assertEquals(3, fixture.paths.size());
            fixture.paths.clear();
            jdbc.update(
                    "UPDATE exchange_account_credentials SET verification_status='PENDING' WHERE credential_id=?",
                    credential.credentialId());
            assertThrows(
                    AccountFeeSyncException.class,
                    () ->
                            service.sync(
                                    owner,
                                    account.exchangeAccountId(),
                                    UUID.randomUUID(),
                                    credential.credentialId(),
                                    "BTC-USDT"));
            assertTrue(fixture.paths.isEmpty());
            economicsRemainEmpty();
        }
    }

    @Test
    void binanceScopeAndTransactionRefuseWrongOwnerVersionEnvironmentBeforeNetwork()
            throws Exception {
        try (var fixture = new BinanceFixture(false)) {
            var account =
                    accounts.create(
                            owner,
                            new ExchangeAccountCreateCommand(
                                    "BINANCE", "LIVE", "scope-" + UUID.randomUUID(), null));
            var credential = fixture.credential(account);
            var bridge = fixture.install(account, credential);
            assertThrows(
                    AccountFeeSyncException.class,
                    () -> bridge.read(account, credential, "BTC-USDT"));
            assertThrows(
                    RuntimeException.class,
                    () ->
                            service.sync(
                                    other,
                                    account.exchangeAccountId(),
                                    UUID.randomUUID(),
                                    credential.credentialId(),
                                    "BTC-USDT"));
            assertThrows(
                    AccountFeeSyncException.class,
                    () ->
                            service.sync(
                                    owner,
                                    account.exchangeAccountId(),
                                    UUID.randomUUID(),
                                    credential.credentialId() + 1,
                                    "BTC-USDT"));
            var sim =
                    accounts.create(
                            owner,
                            new ExchangeAccountCreateCommand(
                                    "BINANCE", "SIM", "testnet-" + UUID.randomUUID(), null));
            assertFalse(bridge.supports(sim, "BTC-USDT"));
            assertFalse(bridge.supports(account, "ETH-USDT"));
            assertTrue(fixture.paths.isEmpty());
        }
    }

    /** SQL直接写入也不能绕过完整条件、方向或缺失口径；非法行用新UUID验证，不改不可变旧行。 */
    @Test
    void binanceDatabaseContractRejectsIncompleteUnknownNumericAndDisguisedScalarTerms()
            throws Exception {
        try (var fixture = new BinanceFixture(false)) {
            var account =
                    accounts.create(
                            owner,
                            new ExchangeAccountCreateCommand(
                                    "BINANCE", "LIVE", "sql-contract-" + UUID.randomUUID(), null));
            var credential = fixture.credential(account);
            fixture.install(account, credential);
            var row =
                    service.sync(
                            owner,
                            account.exchangeAccountId(),
                            UUID.randomUUID(),
                            credential.credentialId(),
                            "BTC-USDT");
            String original = row.quote().commissionTerms().snapshot(mapper).toString();
            for (String invalid :
                    List.of(
                            original.replace(
                                    "\"seller\":\"0.000000000000\"",
                                    "\"unknown\":\"0.000000000000\""),
                            original.replace("\"-0.000000000001\"", "\"-0.0000000000001\""),
                            original.replace("\"-0.000000000001\"", "-0.000000000001"),
                            original.replace("BTCUSDT", "ETHUSDT"),
                            "null")) {
                assertThrows(
                        DataIntegrityViolationException.class,
                        () ->
                                jdbc.update(
                                        """
                        INSERT INTO account_fee_observations(observation_id,owner_user_id,exchange_account_id,operation_id,credential_id,
                        exchange_code,trade_env,symbol,input_fingerprint,observed_at,expires_at,source,rate_convention,source_sha256,provider_time_basis,commission_terms)
                        VALUES(gen_random_uuid(),?,?,gen_random_uuid(),?,'BINANCE','LIVE','BTC-USDT',?,now(),now()+interval '5 minutes',
                        'BINANCE_PRIVATE_SPOT_COMMISSION','BINANCE_POSITIVE_COST_V1',?,'NOT_PROVIDED',?::jsonb)
                        """,
                                        owner,
                                        account.exchangeAccountId(),
                                        credential.credentialId(),
                                        "a".repeat(64),
                                        "b".repeat(64),
                                        invalid));
            }
            assertEquals(row, repository.findObservation(owner, row.observationId()).orElseThrow());
            economicsRemainEmpty();
        }
    }

    @Test
    void ownerRoleUuidAndCursorBoundariesAreEnforcedByFormalApi() throws Exception {
        var account = account();
        long credential = credential(account);
        UUID id = UUID.randomUUID();
        service.sync(owner, account.exchangeAccountId(), id, credential, "BTC-USDT");
        mvc.perform(get(path(account.exchangeAccountId(), id)))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        get(path(account.exchangeAccountId(), id))
                                .header("Authorization", "Bearer " + login("fee-other")))
                .andExpect(status().isNotFound());
        mvc.perform(
                        post(path(account.exchangeAccountId(), UUID.randomUUID()))
                                .header("Authorization", "Bearer " + login("fee-viewer"))
                                .contentType("application/json")
                                .content(
                                        mapper.writeValueAsString(
                                                Map.of(
                                                        "credentialId",
                                                        credential,
                                                        "symbol",
                                                        "BTC-USDT"))))
                .andExpect(status().isForbidden());
        mvc.perform(
                        get("/api/exchange-accounts/" + account.exchangeAccountId() + "/fees")
                                .param("symbol", "BTC-USDT")
                                .param("limit", "51")
                                .header("Authorization", "Bearer " + login("fee-owner")))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        get(path(account.exchangeAccountId(), id)
                                        .replace(id.toString(), "invalid-uuid"))
                                .header("Authorization", "Bearer " + login("fee-owner")))
                .andExpect(status().isBadRequest());
        assertEquals(1, source.calls.get());
    }

    @Test
    void identityCannotBeRetargetedToAnotherCredentialSymbolOrAccount() {
        var first = account();
        long credential = credential(first);
        UUID id = UUID.randomUUID();
        var receipt = service.sync(owner, first.exchangeAccountId(), id, credential, "BTC-USDT");
        var second = account();
        long secondCredential = credential(second);
        assertThrows(
                AccountFeeSyncException.class,
                () -> service.sync(owner, first.exchangeAccountId(), id, credential, "ETH-USDT"));
        assertThrows(
                AccountFeeSyncException.class,
                () ->
                        service.sync(
                                owner,
                                second.exchangeAccountId(),
                                id,
                                secondCredential,
                                "BTC-USDT"));
        assertEquals(receipt, repository.findOperation(owner, id).orElseThrow());
        assertEquals(1, source.calls.get());
    }

    // 测试源在同一事务内改变验证状态；失败须同时撤销该夹具变化与费率写入，不能留下成功回执。
    @Test
    void sourceFailureOrChangedCredentialLeavesNoSuccessfulSnapshot() {
        var account = account();
        long credential = credential(account);
        UUID failure = UUID.randomUUID();
        source.fail = true;
        assertThrows(
                AccountFeeSyncException.class,
                () ->
                        service.sync(
                                owner,
                                account.exchangeAccountId(),
                                failure,
                                credential,
                                "BTC-USDT"));
        assertTrue(service.result(owner, account.exchangeAccountId(), failure).isEmpty());
        source.fail = false;
        String original =
                jdbc.queryForObject(
                        "SELECT verification_status FROM exchange_account_credentials WHERE credential_id=?",
                        String.class,
                        credential);
        source.beforeReturn =
                () ->
                        jdbc.update(
                                "UPDATE exchange_account_credentials SET verification_status='FAILED' WHERE credential_id=?",
                                credential);
        UUID changed = UUID.randomUUID();
        assertThrows(
                AccountFeeSyncException.class,
                () ->
                        service.sync(
                                owner,
                                account.exchangeAccountId(),
                                changed,
                                credential,
                                "BTC-USDT"));
        assertTrue(service.result(owner, account.exchangeAccountId(), changed).isEmpty());
        assertEquals(
                original,
                jdbc.queryForObject(
                        "SELECT verification_status FROM exchange_account_credentials WHERE credential_id=?",
                        String.class,
                        credential));
        economicsRemainEmpty();
    }

    @Test
    void disablingSourceDoesNotHideHistoricalFactsOrFetchDuringRead() {
        var account = account();
        long credential = credential(account);
        UUID id = UUID.randomUUID();
        service.sync(owner, account.exchangeAccountId(), id, credential, "BTC-USDT");
        source.enabled = false;
        var history = service.history(owner, account.exchangeAccountId(), "BTC-USDT", null, 20);
        assertFalse(history.synchronizationAvailable());
        assertEquals(1, history.items().size());
        assertEquals(1, source.calls.get());
        assertThrows(
                AccountFeeSyncException.class,
                () ->
                        service.sync(
                                owner,
                                account.exchangeAccountId(),
                                UUID.randomUUID(),
                                credential,
                                "BTC-USDT"));
        assertEquals(1, source.calls.get());
    }

    @Test
    void boundedCursorDoesNotDuplicateTiedObservationTimesOrCrossAccountScope() {
        var account = account();
        long credential = credential(account);
        source.fixedAt = Instant.now().minusMillis(20);
        for (int i = 0; i < 5; i++)
            service.sync(
                    owner, account.exchangeAccountId(), UUID.randomUUID(), credential, "BTC-USDT");
        var one = service.history(owner, account.exchangeAccountId(), "BTC-USDT", null, 2);
        var two =
                service.history(
                        owner, account.exchangeAccountId(), "BTC-USDT", one.nextBefore(), 2);
        var three =
                service.history(
                        owner, account.exchangeAccountId(), "BTC-USDT", two.nextBefore(), 2);
        assertTrue(one.hasMore());
        assertTrue(two.hasMore());
        assertFalse(three.hasMore());
        assertEquals(
                5,
                Stream.of(one, two, three)
                        .flatMap(page -> page.items().stream())
                        .map(row -> row.observationId())
                        .distinct()
                        .count());
        var otherAccount = account();
        assertTrue(
                service.history(
                                owner,
                                otherAccount.exchangeAccountId(),
                                "BTC-USDT",
                                one.nextBefore(),
                                2)
                        .items()
                        .isEmpty());
        assertEquals(5, source.calls.get());
    }

    @Test
    void immutableAndOwnerForeignKeyConstraintsRejectTampering() {
        var account = account();
        long credential = credential(account);
        UUID operation = UUID.randomUUID();
        var row =
                service.sync(owner, account.exchangeAccountId(), operation, credential, "BTC-USDT");
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbc.update(
                                "UPDATE account_fee_observations SET maker_cost_rate=0 WHERE observation_id=?",
                                row.observationId()));
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbc.update(
                                "DELETE FROM account_fee_observations WHERE observation_id=?",
                                row.observationId()));
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbc.update(
                                """
                INSERT INTO account_fee_observations(observation_id,owner_user_id,exchange_account_id,operation_id,credential_id,
                exchange_code,trade_env,symbol,input_fingerprint,maker_provider_rate,taker_provider_rate,maker_cost_rate,taker_cost_rate,
                fee_tier,provider_at,observed_at,expires_at,source,rate_convention,source_sha256)
                SELECT gen_random_uuid(),?,exchange_account_id,gen_random_uuid(),credential_id,
                exchange_code,trade_env,symbol,input_fingerprint,maker_provider_rate,taker_provider_rate,maker_cost_rate,taker_cost_rate,
                fee_tier,provider_at,observed_at,expires_at,source,rate_convention,source_sha256 FROM account_fee_observations WHERE observation_id=?
                """,
                                other,
                                row.observationId()));
        assertEquals(row, repository.findObservation(owner, row.observationId()).orElseThrow());
    }

    // 闩锁确定性保留第一请求在途；第二请求锁超时后查询原身份仍未知，第一请求提交后只产生一份回执。
    @Test
    void deterministicInFlightLockTimeoutAndOriginalQueryConvergeToOneReceipt() throws Exception {
        var account = account();
        long credential = credential(account);
        UUID id = UUID.randomUUID();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        source.beforeReturn =
                () -> {
                    entered.countDown();
                    try {
                        assertTrue(release.await(10, TimeUnit.SECONDS));
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(ex);
                    }
                };
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first =
                    pool.submit(
                            () ->
                                    service.sync(
                                            owner,
                                            account.exchangeAccountId(),
                                            id,
                                            credential,
                                            "BTC-USDT"));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            try {
                var attempt =
                        pool.submit(
                                () ->
                                        service.sync(
                                                owner,
                                                account.exchangeAccountId(),
                                                id,
                                                credential,
                                                "BTC-USDT"));
                var failure =
                        assertThrows(
                                ExecutionException.class, () -> attempt.get(6, TimeUnit.SECONDS));
                assertInstanceOf(DataAccessException.class, failure.getCause());
                assertTrue(service.result(owner, account.exchangeAccountId(), id).isEmpty());
                assertEquals(1, source.calls.get());
            } finally {
                release.countDown();
            }
            var receipt = first.get(6, TimeUnit.SECONDS);
            assertEquals(
                    receipt, service.result(owner, account.exchangeAccountId(), id).orElseThrow());
            assertEquals(
                    receipt,
                    service.sync(owner, account.exchangeAccountId(), id, credential, "BTC-USDT"));
            assertEquals(1, source.calls.get());
        }
    }

    // 在独立临时库建立V6旧行再升级V7，比较旧事实不变；不改写验收库或已执行迁移。
    @Test
    void migrationUpgradeFromSixPreservesExistingAccountAndCredentialRows() {
        String upgrade = "account_fees_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        control.execute("CREATE DATABASE \"" + upgrade + "\"");
        try {
            var dataSource =
                    new DriverManagerDataSource(
                            baseUrl.substring(0, baseUrl.lastIndexOf('/') + 1) + upgrade,
                            dbUser,
                            dbPassword);
            var db = new JdbcTemplate(dataSource);
            var before =
                    Flyway.configure()
                            .dataSource(dataSource)
                            .locations("classpath:db/migration")
                            .target("6")
                            .load();
            before.migrate();
            before.validate();
            long user =
                    db.queryForObject(
                            "INSERT INTO users(username,password_hash) VALUES('fee-upgrade-fixture','synthetic-only') RETURNING id",
                            Long.class);
            long account =
                    db.queryForObject(
                            "INSERT INTO exchange_accounts(owner_user_id,exchange_code,trade_env,account_alias) VALUES(?,'OKX','SIM','fee-upgrade-fixture') RETURNING exchange_account_id",
                            Long.class,
                            user);
            db.update(
                    "INSERT INTO exchange_account_credentials(exchange_account_id,credential_type,encrypted_payload,key_version) VALUES(?,'OKX_API_V5',decode('00','hex'),1)",
                    account);
            var oldAccounts =
                    db.queryForList("SELECT * FROM exchange_accounts ORDER BY exchange_account_id");
            var oldCredentials =
                    db.queryForList(
                            "SELECT credential_id,exchange_account_id,key_version,credential_status FROM exchange_account_credentials ORDER BY credential_id");
            var upgraded =
                    Flyway.configure()
                            .dataSource(dataSource)
                            .locations("classpath:db/migration")
                            // 此用例固定证明V6到V10的历史迁移，不混入后续schema。
                            .target("10")
                            .load();
            upgraded.migrate();
            upgraded.validate();
            assertEquals("10", upgraded.info().current().getVersion().toString());
            assertEquals(
                    oldAccounts,
                    db.queryForList(
                            "SELECT * FROM exchange_accounts ORDER BY exchange_account_id"));
            assertEquals(
                    oldCredentials,
                    db.queryForList(
                            "SELECT credential_id,exchange_account_id,key_version,credential_status FROM exchange_account_credentials ORDER BY credential_id"));
            assertEquals(
                    0L,
                    db.queryForObject("SELECT count(*) FROM account_fee_observations", Long.class));
        } finally {
            control.execute("DROP DATABASE \"" + upgrade + "\" WITH (FORCE)");
        }
    }

    /** V9旧观察在V10升级后原值与V1来源摘要不变，Flyway不repair也不改写已执行migration。 */
    @Test
    void migrationUpgradeFromNinePreservesImmutableLegacyObservationAndDigest() {
        String upgrade = "native_fees_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        control.execute("CREATE DATABASE \"" + upgrade + "\"");
        try {
            var dataSource =
                    new DriverManagerDataSource(
                            baseUrl.substring(0, baseUrl.lastIndexOf('/') + 1) + upgrade,
                            dbUser,
                            dbPassword);
            var db = new JdbcTemplate(dataSource);
            var before =
                    Flyway.configure()
                            .dataSource(dataSource)
                            .locations("classpath:db/migration")
                            .target("9")
                            .load();
            before.migrate();
            before.validate();
            long user =
                    db.queryForObject(
                            "INSERT INTO users(username,password_hash) VALUES('legacy-source-fixture','synthetic-only') RETURNING id",
                            Long.class);
            long account =
                    db.queryForObject(
                            "INSERT INTO exchange_accounts(owner_user_id,exchange_code,trade_env,account_alias) VALUES(?,'OKX','SIM','legacy-source-fixture') RETURNING exchange_account_id",
                            Long.class,
                            user);
            long credential =
                    db.queryForObject(
                            "INSERT INTO exchange_account_credentials(exchange_account_id,credential_type,encrypted_payload,key_version) VALUES(?,'OKX_API_V5',decode('00','hex'),1) RETURNING credential_id",
                            Long.class,
                            account);
            Instant at = Instant.now().truncatedTo(ChronoUnit.MICROS);
            UUID observation = UUID.randomUUID(), operation = UUID.randomUUID();
            var quote =
                    new AccountFeeQuote(
                            new BigDecimal("0.000000000001"),
                            new BigDecimal("-0.001"),
                            "LegacyLv1",
                            at,
                            at);
            var unsigned =
                    new AccountFeeObservation(
                            observation,
                            operation,
                            user,
                            account,
                            credential,
                            "OKX",
                            "SIM",
                            "BTC-USDT",
                            "a".repeat(64),
                            quote,
                            at.plusSeconds(300),
                            "");
            String digest = AccountFeeSourceIdentity.digest(unsigned);
            db.update(
                    """
                    INSERT INTO account_fee_observations(observation_id,owner_user_id,exchange_account_id,operation_id,credential_id,
                    exchange_code,trade_env,symbol,input_fingerprint,maker_provider_rate,taker_provider_rate,maker_cost_rate,taker_cost_rate,
                    fee_tier,provider_at,observed_at,expires_at,source,rate_convention,source_sha256)
                    VALUES(?,?,?,?,?,'OKX','SIM','BTC-USDT',?,?,?,?,?,'LegacyLv1',?,?,?,'OKX_PRIVATE_SPOT_FEE_V5','OKX_NEGATIVE_COST_V1',?)
                    """,
                    observation,
                    user,
                    account,
                    operation,
                    credential,
                    "a".repeat(64),
                    quote.makerProviderRate(),
                    quote.takerProviderRate(),
                    quote.makerCostRate(),
                    quote.takerCostRate(),
                    Timestamp.from(at),
                    Timestamp.from(at),
                    Timestamp.from(at.plusSeconds(300)),
                    digest);
            String old =
                    db.queryForObject(
                            "SELECT to_jsonb(t)::text FROM account_fee_observations t WHERE observation_id=?",
                            String.class,
                            observation);
            var after =
                    Flyway.configure()
                            .dataSource(dataSource)
                            .locations("classpath:db/migration")
                            // 此用例固定证明V9到V10的历史观察完整性。
                            .target("10")
                            .load();
            after.migrate();
            after.validate();
            assertEquals("10", after.info().current().getVersion().toString());
            assertEquals(
                    old,
                    db.queryForObject(
                            "SELECT (to_jsonb(t)-'commission_terms'-'provider_time_basis')::text FROM account_fee_observations t WHERE observation_id=?",
                            String.class,
                            observation));
            var reopened =
                    new JdbcAccountFeeRepository(db)
                            .findObservation(user, observation)
                            .orElseThrow();
            assertEquals(digest, reopened.sourceSha256());
            assertEquals(digest, AccountFeeSourceIdentity.digest(reopened));
            assertEquals(quote, reopened.quote());
            assertThrows(
                    DataIntegrityViolationException.class,
                    () ->
                            db.update(
                                    "UPDATE account_fee_observations SET fee_tier='tamper' WHERE observation_id=?",
                                    observation));
        } finally {
            assertTrue(upgrade.matches("native_fees_upgrade_[0-9a-f]{32}"));
            control.execute("DROP DATABASE \"" + upgrade + "\" WITH (FORCE)");
        }
    }

    private void economicsRemainEmpty() {
        for (String table : List.of("orders", "trades", "ledger_entries"))
            assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class));
    }

    private static String path(long account, UUID id) {
        return "/api/exchange-accounts/" + account + "/fees/operations/" + id;
    }

    private String login(String username) throws Exception {
        return mapper.readTree(
                        mvc.perform(
                                        post("/api/auth/login")
                                                .contentType("application/json")
                                                .content(
                                                        mapper.writeValueAsString(
                                                                Map.of(
                                                                        "username",
                                                                        username,
                                                                        "password",
                                                                        PASSWORD))))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString())
                .path("accessToken")
                .asText();
    }

    /** 只在本测试上下文装配来源替身；正常产品装配没有此bean，也不取得任何私有联网授权。 */
    @TestConfiguration
    static class FixtureConfiguration {
        @Bean
        FixtureSource fixtureAccountFeeSource() {
            return new FixtureSource();
        }
    }

    static class FixtureSource implements AccountFeeReadPort {
        final AtomicInteger calls = new AtomicInteger();
        boolean enabled = true, fail;
        volatile Runnable beforeReturn = () -> {};
        Instant fixedAt;
        AccountFeeReadPort delegate;

        @Override
        public boolean supports(ExchangeAccountSummary account, String symbol) {
            return enabled
                    && ("BINANCE".equals(account.exchangeCode())
                            ? delegate != null && delegate.supports(account, symbol)
                            : "OKX".equals(account.exchangeCode())
                                    && "SIM".equals(account.tradeEnv())
                                    && Set.of("BTC-USDT", "ETH-USDT").contains(symbol));
        }

        @Override
        public AccountFeeQuote read(
                ExchangeAccountSummary account,
                ExchangeAccountCredentialSummary credential,
                String symbol) {
            calls.incrementAndGet();
            if (fail) throw new AccountFeeSyncException("FEE_FIXTURE_UNAVAILABLE");
            beforeReturn.run();
            Instant now = fixedAt == null ? Instant.now() : fixedAt;
            if ("BINANCE".equals(account.exchangeCode()))
                return delegate.read(account, credential, symbol);
            return new AccountFeeQuote(
                    new BigDecimal("0.0002"), new BigDecimal("-0.001"), "FixtureLv1", now, now);
        }
    }

    /** 只有当前测试的127.0.0.1 fixture；合成材料经正式加密/结构校验后再由JIT读取，不使用运行环境材料。 */
    private final class BinanceFixture implements AutoCloseable {
        final HttpServer server;
        final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
        final HttpClient client = BinanceCommissionHttpReader.directHttpClient();
        final List<String> paths = new CopyOnWriteArrayList<>();
        final AtomicInteger signatures = new AtomicInteger();
        final KeyPair keyPair;
        final boolean ed;
        boolean withdraw;
        Runnable beforeCommission = () -> {};
        private static final String API_KEY = "synthetic-binance-fee-key",
                SECRET = "synthetic-fee-hmac-secret";

        BinanceFixture(boolean ed) throws Exception {
            this.ed = ed;
            keyPair = ed ? KeyPairGenerator.getInstance("Ed25519").generateKeyPair() : null;
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(workers);
            server.createContext("/", this::respond);
            server.start();
        }

        ExchangeAccountCredentialSummary credential(ExchangeAccountSummary account) {
            String pem =
                    ed
                            ? "-----BEGIN %s-----\n".formatted("PRIVATE KEY")
                                    + Base64.getEncoder()
                                            .encodeToString(keyPair.getPrivate().getEncoded())
                                    + "\n-----END %s-----".formatted("PRIVATE KEY")
                            : null;
            var value =
                    credentials.upsert(
                            owner,
                            account.exchangeAccountId(),
                            new ExchangeAccountCredentialUpsertCommand(
                                    ed ? "BINANCE_ED25519" : "BINANCE_HMAC",
                                    API_KEY,
                                    ed ? null : SECRET,
                                    null,
                                    pem),
                            1);
            return verification.verifyVersion(
                    owner, account.exchangeAccountId(), value.credentialId());
        }

        JdbcBinanceAccountFeeReadPort install(
                ExchangeAccountSummary account, ExchangeAccountCredentialSummary credential) {
            var reader =
                    new BinanceCommissionHttpReader(
                            client,
                            URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                            BinanceCommissionHttpReader.Environment.ISOLATED_FIXTURE,
                            Duration.ofSeconds(4),
                            Clock.systemUTC());
            var bridge =
                    new JdbcBinanceAccountFeeReadPort(
                            jdbc,
                            runtimeCredentials.getMasterKey(),
                            reader,
                            accountRows,
                            credentialRows,
                            kill,
                            owner,
                            account.exchangeAccountId(),
                            credential.credentialId(),
                            credential.credentialType(),
                            Set.of("BTC-USDT"),
                            BinanceCommissionHttpReader.PermissionScope.READ_ONLY);
            source.delegate = bridge;
            return bridge;
        }

        void respond(HttpExchange request) throws IOException {
            try (request) {
                String path = request.getRequestURI().getPath();
                paths.add(path);
                if (!"GET".equals(request.getRequestMethod())
                        || !"/api/v3/time".equals(path) && !validSignature(request)) {
                    request.sendResponseHeaders(401, -1);
                    return;
                }
                String body;
                if ("/api/v3/time".equals(path))
                    body = "{\"serverTime\":" + System.currentTimeMillis() + "}";
                else if ("/sapi/v1/account/apiRestrictions".equals(path))
                    body =
                            "{\"ipRestrict\":true,\"enableReading\":true,\"enableWithdrawals\":"
                                    + withdraw
                                    + ",\"enableInternalTransfer\":false,\"enableSpotAndMarginTrading\":false,\"enableMargin\":false,\"enableFutures\":false,\"enableVanillaOptions\":false}";
                else {
                    beforeCommission.run();
                    body =
                            """
                            {"symbol":"BTCUSDT","standardCommission":{"maker":"-0.000000000001","taker":"0.001","buyer":"0.0002","seller":"0.0003"},
                            "taxCommission":{"maker":"0","taker":"0","buyer":"0","seller":"0"},
                            "specialCommission":{"maker":"0","taker":"0.00001","buyer":"0","seller":"0"},
                            "discount":{"enabledForAccount":true,"enabledForSymbol":true,"discountAsset":"BNB","discount":"0.25"}}
                            """;
                }
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                request.sendResponseHeaders(200, bytes.length);
                request.getResponseBody().write(bytes);
            }
        }

        boolean validSignature(HttpExchange request) {
            try {
                String raw = request.getRequestURI().getRawQuery();
                int split = raw.lastIndexOf("&signature=");
                if (!API_KEY.equals(request.getRequestHeaders().getFirst("X-MBX-APIKEY"))
                        || split < 0) return false;
                String query = raw.substring(0, split),
                        supplied =
                                URLDecoder.decode(
                                        raw.substring(split + 11), StandardCharsets.UTF_8);
                boolean valid;
                if (ed) {
                    var verifier = Signature.getInstance("Ed25519");
                    verifier.initVerify(keyPair.getPublic());
                    verifier.update(query.getBytes(StandardCharsets.UTF_8));
                    valid = verifier.verify(Base64.getDecoder().decode(supplied));
                } else {
                    var mac = Mac.getInstance("HmacSHA256");
                    mac.init(
                            new SecretKeySpec(
                                    SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                    valid =
                            HexFormat.of()
                                    .formatHex(mac.doFinal(query.getBytes(StandardCharsets.UTF_8)))
                                    .equals(supplied);
                }
                if (valid) signatures.incrementAndGet();
                return valid;
            } catch (Exception failure) {
                return false;
            }
        }

        @Override
        public void close() {
            server.stop(0);
            workers.shutdownNow();
            client.close();
        }
    }

    /** 测试上下文关闭后，仅删除本测试随机创建且名称匹配的临时库；不清理正式验收事实。 */
    public static class Cleanup extends AbstractTestExecutionListener {
        @Override
        public int getOrder() {
            return 999;
        }

        @Override
        public void afterTestClass(TestContext context) {
            if (control != null && database != null) {
                assertTrue(database.matches("account_fees_[0-9a-f]{32}"));
                control.execute("DROP DATABASE \"" + database + "\" WITH (FORCE)");
            }
        }
    }
}
