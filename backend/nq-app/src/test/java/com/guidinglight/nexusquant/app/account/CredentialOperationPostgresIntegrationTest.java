package com.guidinglight.nexusquant.app.account;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.application.command.ExchangeAccountCreateCommand;
import com.guidinglight.nexusquant.account.application.exception.CredentialOperationIdentityException;
import com.guidinglight.nexusquant.account.application.service.CredentialOperationService;
import com.guidinglight.nexusquant.account.application.service.ExchangeAccountCommandService;
import com.guidinglight.nexusquant.account.application.service.ExchangeAccountCredentialCommandService;
import com.guidinglight.nexusquant.account.application.service.ExchangeAccountCredentialVerificationService;
import com.guidinglight.nexusquant.account.domain.CredentialOperation;
import com.guidinglight.nexusquant.account.domain.CredentialOperationReceipt;
import com.guidinglight.nexusquant.account.domain.port.CredentialOperationRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.account.infra.jdbc.JdbcCredentialOperationRepository;
import com.guidinglight.nexusquant.account.infra.jdbc.JdbcExchangeAccountRepository;
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
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** 隔离PG16和合成凭据证明正式版本链、回执、权限及UNKNOWN恢复；不访问真实交易所。 */
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
        listeners = CredentialOperationPostgresIntegrationTest.Cleanup.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
class CredentialOperationPostgresIntegrationTest {
    private static final String PASSWORD = "synthetic-account-command-fixture-password";
    private static JdbcTemplate control;
    private static String database, baseUrl, dbUser, dbPassword;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthSeedService seeds;
    @Autowired PasswordEncoder passwords;
    @Autowired CredentialOperationService service;
    @Autowired ExchangeAccountCredentialCommandService credentialCommands;
    @Autowired ExchangeAccountCredentialVerificationService verification;
    @Autowired ExchangeAccountCredentialRepository credentials;
    @Autowired ExchangeAccountCommandService accounts;
    @Autowired CredentialOperationRepository receipts;
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
        database = "credential_operation_" + UUID.randomUUID().toString().replace("-", "");
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
                                "credential-owner",
                                passwords.encode(PASSWORD),
                                List.of("OPERATOR", "VIEWER"),
                                true),
                        new SeedUserCommand(
                                "credential-other",
                                passwords.encode(PASSWORD),
                                List.of("OPERATOR", "VIEWER"),
                                true),
                        new SeedUserCommand(
                                "credential-viewer",
                                passwords.encode(PASSWORD),
                                List.of("VIEWER"),
                                true)));
        owner =
                jdbc.queryForObject(
                        "SELECT id FROM users WHERE username='credential-owner'", Long.class);
        other =
                jdbc.queryForObject(
                        "SELECT id FROM users WHERE username='credential-other'", Long.class);
    }

    private long account() {
        return accounts.create(
                        owner,
                        new ExchangeAccountCreateCommand(
                                "OKX", "SIM", "synthetic-credential-" + UUID.randomUUID(), null))
                .exchangeAccountId();
    }

    private CredentialOperation create(String suffix) {
        return new CredentialOperation(
                CredentialOperation.Type.CREATE,
                null,
                "OKX_API_V5",
                "synthetic-key-" + suffix,
                "synthetic-secret-" + suffix,
                "synthetic-pass-" + suffix,
                null,
                null);
    }

    private CredentialOperation state(CredentialOperation.Type type, long credential) {
        return new CredentialOperation(
                type,
                credential,
                "OKX_API_V5",
                null,
                null,
                null,
                null,
                type == CredentialOperation.Type.VERIFY ? null : "isolated lifecycle validation");
    }

    private CredentialOperation rotate(long credential, String suffix) {
        var r = create(suffix);
        return new CredentialOperation(
                CredentialOperation.Type.ROTATE,
                credential,
                r.credentialType(),
                r.apiKey(),
                r.secretKey(),
                r.passphrase(),
                null,
                "isolated rotation validation");
    }

    private CredentialOperationReceipt execute(long account, CredentialOperation r) {
        return service.execute(owner, account, UUID.randomUUID(), r, "credential-owner");
    }

    private String path(long account, UUID operation) {
        return "/api/exchange-accounts/" + account + "/credentials/operations/" + operation;
    }

    @Test
    void canonicalApiReceiptsAreHistoricalAndNeverExposeSensitiveInput() throws Exception {
        long a = account();
        UUID id = UUID.randomUUID();
        var request = create("api");
        String token = login("credential-owner");
        String response =
                mvc.perform(
                                post(path(a, id))
                                        .header("Authorization", "Bearer " + token)
                                        .contentType("application/json")
                                        .content(mapper.writeValueAsString(request)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        var original = mapper.readTree(response);
        long credential = original.path("result").path("credentialId").asLong();
        assertTrue(credential > 0);
        assertFalse(response.contains(request.secretKey()));
        assertFalse(response.contains(request.passphrase()));
        assertFalse(response.contains("inputHmac"));
        assertFalse(response.contains("fingerprintKeyId"));
        execute(a, state(CredentialOperation.Type.DISABLE, credential));
        var replay =
                mapper.readTree(
                        mvc.perform(
                                        post(path(a, id))
                                                .header("Authorization", "Bearer " + token)
                                                .contentType("application/json")
                                                .content(mapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        assertEquals(original, replay);
        assertEquals("DISABLED", credentialStatus(credential));
        mvc.perform(get(path(a, id)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.credentialStatus").value("ACTIVE"));
        mvc.perform(
                        post(path(a, id))
                                .header("Authorization", "Bearer " + token)
                                .contentType("application/json")
                                .content(mapper.writeValueAsString(create("changed"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREDENTIAL_OPERATION_IDENTITY_CONFLICT"));
        assertEquals(
                2L,
                jdbc.queryForObject(
                        "SELECT count(*) FROM credential_operation_receipts WHERE account_id=?",
                        Long.class,
                        a));
        var reopened =
                new JdbcCredentialOperationRepository(
                        new JdbcTemplate(jdbc.getDataSource()),
                        mapper,
                        "synthetic-key-after-rotation");
        assertEquals(
                "ACTIVE", reopened.find(owner, a, id).orElseThrow().result().credentialStatus());
        var changedKey =
                new CredentialOperationService(
                        reopened, jdbcAccounts(), credentials, credentialCommands, verification, 1);
        assertThrows(
                CredentialOperationIdentityException.class,
                () ->
                        new TransactionTemplate(transactions)
                                .execute(
                                        tx ->
                                                changedKey.execute(
                                                        owner,
                                                        a,
                                                        id,
                                                        request,
                                                        "credential-owner")));
    }

    private ExchangeAccountRepository jdbcAccounts() {
        return new JdbcExchangeAccountRepository(jdbc);
    }

    private String credentialStatus(long id) {
        return jdbc.queryForObject(
                "SELECT credential_status FROM exchange_account_credentials WHERE credential_id=?",
                String.class,
                id);
    }

    @Test
    void allLifecycleOperationsRetainVersionsAndExactVerification() {
        long a = account();
        var first = execute(a, create("lifecycle")).result();
        long id = first.credentialId();
        var verified = execute(a, state(CredentialOperation.Type.VERIFY, id)).result();
        assertEquals("VERIFIED", verified.verificationStatus());
        assertEquals("NOT_PROBED", verified.permissionProbeStatus());
        execute(a, state(CredentialOperation.Type.DISABLE, id));
        assertEquals("DISABLED", credentialStatus(id));
        assertThrows(
                IllegalStateException.class,
                () -> execute(a, state(CredentialOperation.Type.VERIFY, id)));
        execute(a, state(CredentialOperation.Type.ENABLE, id));
        assertEquals("ACTIVE", credentialStatus(id));
        var next = execute(a, rotate(id, "replacement")).result();
        assertEquals(id, next.rotatedFromCredentialId());
        assertEquals("ROTATED", credentialStatus(id));
        assertThrows(
                IllegalStateException.class,
                () -> execute(a, state(CredentialOperation.Type.ENABLE, id)));
        execute(a, state(CredentialOperation.Type.EXPIRE, next.credentialId()));
        assertEquals("EXPIRED", credentialStatus(next.credentialId()));
        execute(a, state(CredentialOperation.Type.REVOKE, next.credentialId()));
        assertEquals("REVOKED", credentialStatus(next.credentialId()));
        assertThrows(
                IllegalStateException.class,
                () -> execute(a, state(CredentialOperation.Type.ENABLE, next.credentialId())));
        assertEquals(
                2L,
                jdbc.queryForObject(
                        "SELECT count(*) FROM exchange_account_credentials WHERE exchange_account_id=?",
                        Long.class,
                        a));
        assertTrue(
                jdbc.queryForObject(
                                "SELECT count(*) FROM credential_audit_logs WHERE exchange_account_id=?",
                                Long.class,
                                a)
                        >= 6);
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM orders", Long.class));
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM ledger_entries", Long.class));
    }

    @Test
    void concurrentSameIdentityWaitsOnPgLocksAndCreatesOneEncryptedVersion() throws Exception {
        long a = account();
        UUID id = UUID.randomUUID();
        var request = create("concurrent");
        try (var workers = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2);
            var release = new CountDownLatch(1);
            var first =
                    workers.submit(
                            () -> {
                                ready.countDown();
                                assertTrue(release.await(5, TimeUnit.SECONDS));
                                return service.execute(owner, a, id, request, "credential-owner");
                            });
            var second =
                    workers.submit(
                            () -> {
                                ready.countDown();
                                assertTrue(release.await(5, TimeUnit.SECONDS));
                                return service.execute(owner, a, id, request, "credential-owner");
                            });
            try {
                new TransactionTemplate(transactions)
                        .executeWithoutResult(
                                tx -> {
                                    receipts.lock(owner, a, id);
                                    try {
                                        assertTrue(ready.await(5, TimeUnit.SECONDS));
                                        release.countDown();
                                        awaitBlocked(2);
                                    } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                        throw new AssertionError(e);
                                    }
                                });
                assertEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
                assertEquals(
                        1L,
                        jdbc.queryForObject(
                                "SELECT count(*) FROM credential_operation_receipts WHERE account_id=?",
                                Long.class,
                                a));
                assertEquals(
                        1L,
                        jdbc.queryForObject(
                                "SELECT count(*) FROM exchange_account_credentials WHERE exchange_account_id=?",
                                Long.class,
                                a));
            } finally {
                release.countDown();
            }
        }
    }

    private void awaitBlocked(int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        int waiting = 0;
        do {
            waiting =
                    jdbc.queryForObject(
                            "SELECT count(*) FROM pg_locks l JOIN pg_stat_activity a ON a.pid=l.pid WHERE NOT l.granted AND a.datname=current_database()",
                            Integer.class);
            if (waiting >= expected) break;
            new CountDownLatch(1).await(10, TimeUnit.MILLISECONDS);
        } while (System.nanoTime() < deadline);
        assertTrue(waiting >= expected, "workers must actually wait on held PG locks");
    }

    @Test
    void rotationWinsBeforeVerificationAndOldVersionIsNeverReplacedByNewTarget() throws Exception {
        long a = account(), old = execute(a, create("verify-race")).result().credentialId();
        UUID rotateId = UUID.randomUUID(), verifyId = UUID.randomUUID();
        try (var worker = Executors.newSingleThreadExecutor()) {
            var future = new AtomicReference<Future<CredentialOperationReceipt>>();
            var next =
                    new TransactionTemplate(transactions)
                            .execute(
                                    tx -> {
                                        receipts.lock(owner, a, rotateId);
                                        future.set(
                                                worker.submit(
                                                        () ->
                                                                service.execute(
                                                                        owner,
                                                                        a,
                                                                        verifyId,
                                                                        state(
                                                                                CredentialOperation
                                                                                        .Type
                                                                                        .VERIFY,
                                                                                old),
                                                                        "credential-owner")));
                                        try {
                                            awaitBlocked(1);
                                        } catch (InterruptedException e) {
                                            Thread.currentThread().interrupt();
                                            throw new AssertionError(e);
                                        }
                                        return service.execute(
                                                owner,
                                                a,
                                                rotateId,
                                                rotate(old, "verify-race-new"),
                                                "credential-owner");
                                    });
            var failure =
                    assertThrows(
                            ExecutionException.class, () -> future.get().get(10, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof IllegalStateException);
            assertTrue(service.find(owner, a, verifyId).isEmpty());
            assertEquals(
                    "PENDING",
                    credentials
                            .findByCredentialIdForOwner(owner, a, next.result().credentialId())
                            .orElseThrow()
                            .verificationStatus());
            assertEquals("ROTATED", credentialStatus(old));
        }
    }

    @Test
    void receiptFailureRollsBackRotationAndItsAuditAndOriginalIdentityCanRecover() {
        long a = account(), old = execute(a, create("rollback")).result().credentialId();
        UUID id = UUID.randomUUID();
        var r = rotate(old, "rollback-new");
        long before =
                jdbc.queryForObject(
                        "SELECT count(*) FROM credential_audit_logs WHERE exchange_account_id=?",
                        Long.class,
                        a);
        CredentialOperationRepository broken =
                new CredentialOperationRepository() {
                    public void lock(Long o, Long ac, UUID op) {
                        receipts.lock(o, ac, op);
                    }

                    public Optional<CredentialOperationReceipt> find(Long o, Long ac, UUID op) {
                        return receipts.find(o, ac, op);
                    }

                    public String fingerprint(byte[] input) {
                        return receipts.fingerprint(input);
                    }

                    public String fingerprintKeyId() {
                        return receipts.fingerprintKeyId();
                    }

                    public void save(CredentialOperationReceipt receipt) {
                        throw new IllegalStateException("synthetic receipt failure");
                    }
                };
        var failed =
                new CredentialOperationService(
                        broken, jdbcAccounts(), credentials, credentialCommands, verification, 1);
        assertThrows(
                IllegalStateException.class,
                () ->
                        new TransactionTemplate(transactions)
                                .execute(
                                        tx -> failed.execute(owner, a, id, r, "credential-owner")));
        assertEquals("ACTIVE", credentialStatus(old));
        assertEquals(
                1L,
                jdbc.queryForObject(
                        "SELECT count(*) FROM exchange_account_credentials WHERE exchange_account_id=?",
                        Long.class,
                        a));
        assertEquals(
                before,
                jdbc.queryForObject(
                        "SELECT count(*) FROM credential_audit_logs WHERE exchange_account_id=?",
                        Long.class,
                        a));
        assertTrue(service.find(owner, a, id).isEmpty());
        assertNotNull(service.execute(owner, a, id, r, "credential-owner"));
        assertThrows(IllegalStateException.class, () -> receipts.lock(owner, a, UUID.randomUUID()));
    }

    @Test
    void heldOperationLockTimesOutWithoutWritingAndKeepsOriginalRecoveryIdentity()
            throws Exception {
        long a = account();
        UUID id = UUID.randomUUID();
        var request = create("timeout");
        try (var worker = Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactions)
                    .executeWithoutResult(
                            tx -> {
                                receipts.lock(owner, a, id);
                                var attempt =
                                        worker.submit(
                                                () ->
                                                        service.execute(
                                                                owner,
                                                                a,
                                                                id,
                                                                request,
                                                                "credential-owner"));
                                try {
                                    var failure =
                                            assertThrows(
                                                    ExecutionException.class,
                                                    () -> attempt.get(14, TimeUnit.SECONDS));
                                    assertTrue(failure.getCause() instanceof QueryTimeoutException);
                                    assertTrue(receipts.find(owner, a, id).isEmpty());
                                    assertEquals(
                                            0L,
                                            jdbc.queryForObject(
                                                    "SELECT count(*) FROM exchange_account_credentials WHERE exchange_account_id=?",
                                                    Long.class,
                                                    a));
                                } finally {
                                    attempt.cancel(true);
                                }
                            });
        }
        assertNotNull(service.execute(owner, a, id, request, "credential-owner"));
    }

    @Test
    void fingerprintIncludesEachSensitiveFieldAndRotationReason() {
        long a = account();
        UUID id = UUID.randomUUID();
        var original = create("fingerprint");
        var receipt = service.execute(owner, a, id, original, "credential-owner");
        for (var changed :
                List.of(
                        new CredentialOperation(
                                original.type(),
                                null,
                                original.credentialType(),
                                original.apiKey() + "-changed",
                                original.secretKey(),
                                original.passphrase(),
                                null,
                                null),
                        new CredentialOperation(
                                original.type(),
                                null,
                                original.credentialType(),
                                original.apiKey(),
                                original.secretKey() + "-changed",
                                original.passphrase(),
                                null,
                                null),
                        new CredentialOperation(
                                original.type(),
                                null,
                                original.credentialType(),
                                original.apiKey(),
                                original.secretKey(),
                                original.passphrase() + "-changed",
                                null,
                                null)))
            assertThrows(
                    CredentialOperationIdentityException.class,
                    () -> service.execute(owner, a, id, changed, "credential-owner"));
        UUID rotateId = UUID.randomUUID();
        var rotation = rotate(receipt.result().credentialId(), "fingerprint-rotate");
        service.execute(owner, a, rotateId, rotation, "credential-owner");
        var changedReason =
                new CredentialOperation(
                        rotation.type(),
                        rotation.credentialId(),
                        rotation.credentialType(),
                        rotation.apiKey(),
                        rotation.secretKey(),
                        rotation.passphrase(),
                        null,
                        "different isolated reason");
        assertThrows(
                CredentialOperationIdentityException.class,
                () -> service.execute(owner, a, rotateId, changedReason, "credential-owner"));
        assertEquals(
                2L,
                jdbc.queryForObject(
                        "SELECT count(*) FROM credential_operation_receipts WHERE account_id=?",
                        Long.class,
                        a));
    }

    @Test
    void malformedUnicodeCannotCollideWithOriginalInputThroughJsonApiOrService() throws Exception {
        long a = account();
        UUID id = UUID.randomUUID();
        var original = create("unicode?");
        var recorded = service.execute(owner, a, id, original, "credential-owner");
        String token = login("credential-owner");
        for (String invalid : List.of("\\ud800", "\\udc00")) {
            String json =
                    mapper.writeValueAsString(original)
                            .replace(
                                    "synthetic-secret-unicode?",
                                    "synthetic-secret-unicode" + invalid);
            mvc.perform(
                            post(path(a, id))
                                    .header("Authorization", "Bearer " + token)
                                    .contentType("application/json")
                                    .content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        }
        for (String invalid : List.of("\uD800", "\uDC00")) {
            var changed =
                    new CredentialOperation(
                            original.type(),
                            null,
                            original.credentialType(),
                            original.apiKey(),
                            "synthetic-secret-unicode" + invalid,
                            original.passphrase(),
                            null,
                            null);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> service.execute(owner, a, id, changed, "credential-owner"));
        }
        assertEquals(recorded, service.find(owner, a, id).orElseThrow());
        assertEquals(
                1L,
                jdbc.queryForObject(
                        "SELECT count(*) FROM exchange_account_credentials WHERE exchange_account_id=?",
                        Long.class,
                        a));
        assertEquals(
                1L,
                jdbc.queryForObject(
                        "SELECT count(*) FROM credential_operation_receipts WHERE account_id=?",
                        Long.class,
                        a));
    }

    @Test
    void ordinaryUnicodeSupplementaryCharactersAndReplacementCharacterRemainDistinct()
            throws Exception {
        long a = account();
        UUID id = UUID.randomUUID();
        var original =
                new CredentialOperation(
                        CredentialOperation.Type.CREATE,
                        null,
                        "OKX_API_V5",
                        "synthetic-unicode-api",
                        "synthetic-密钥-é-\uD83D\uDE00",
                        "synthetic-口令-\uD83C\uDF0C",
                        null,
                        null);
        var recorded = service.execute(owner, a, id, original, "credential-owner");
        assertEquals(recorded, service.execute(owner, a, id, original, "credential-owner"));
        var payload =
                credentials
                        .findActiveMaterial(owner, a, "OKX_API_V5")
                        .orElseThrow()
                        .decryptedPayloadJson();
        assertEquals(original.secretKey(), mapper.readTree(payload).path("secretKey").asText());
        assertEquals(original.passphrase(), mapper.readTree(payload).path("passphrase").asText());
        var changed =
                new CredentialOperation(
                        original.type(),
                        null,
                        original.credentialType(),
                        original.apiKey(),
                        "synthetic-密钥-é-\uFFFD",
                        original.passphrase(),
                        null,
                        null);
        assertThrows(
                CredentialOperationIdentityException.class,
                () -> service.execute(owner, a, id, changed, "credential-owner"));
        assertEquals(
                "VERIFIED",
                execute(a, state(CredentialOperation.Type.VERIFY, recorded.result().credentialId()))
                        .result()
                        .verificationStatus());
    }

    @Test
    void ownedHistoryIsBoundedAndPermissionsDoNotDecryptMaterial() throws Exception {
        long a = account();
        long current = execute(a, create("history-0")).result().credentialId();
        for (int i = 1; i < 25; i++)
            current = execute(a, rotate(current, "history-" + i)).result().credentialId();
        String endpoint = "/api/exchange-accounts/" + a + "/credentials",
                token = login("credential-owner");
        String raw =
                mvc.perform(get(endpoint).header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        var page = mapper.readTree(raw);
        assertEquals(20, page.path("items").size());
        assertTrue(page.path("hasMore").asBoolean());
        assertFalse(raw.contains("synthetic-secret"));
        assertFalse(raw.contains("encrypted_payload"));
        assertFalse(raw.contains("inputHmac"));
        var older =
                mapper.readTree(
                        mvc.perform(
                                        get(endpoint)
                                                .param("before", page.path("nextBefore").asText())
                                                .header("Authorization", "Bearer " + token))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        assertEquals(5, older.path("items").size());
        assertFalse(older.path("hasMore").asBoolean());
        mvc.perform(get(endpoint).param("limit", "51").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        mvc.perform(get(endpoint).param("before", "0").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        mvc.perform(get(endpoint)).andExpect(status().isUnauthorized());
        mvc.perform(get(endpoint).header("Authorization", "Bearer " + login("credential-other")))
                .andExpect(status().isNotFound());
        mvc.perform(
                        post(path(a, UUID.randomUUID()))
                                .header("Authorization", "Bearer " + login("credential-viewer"))
                                .contentType("application/json")
                                .content(mapper.writeValueAsString(create("forbidden"))))
                .andExpect(status().isForbidden());
        mvc.perform(get(path(a, UUID.randomUUID())).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(
                        get(endpoint + "/operations/invalid")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validationResponsesMustNotEchoRejectedSecretMaterial() throws Exception {
        long a = account();
        var request = create("oversized-secret");
        String secret = "synthetic-rejected-secret-marker" + "x".repeat(4096);
        var invalid =
                new CredentialOperation(
                        request.type(),
                        null,
                        request.credentialType(),
                        request.apiKey(),
                        secret,
                        request.passphrase(),
                        null,
                        null);
        var response =
                mvc.perform(
                                post(path(a, UUID.randomUUID()))
                                        .header(
                                                "Authorization",
                                                "Bearer " + login("credential-owner"))
                                        .contentType("application/json")
                                        .content(mapper.writeValueAsString(invalid)))
                        .andExpect(status().isBadRequest())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertFalse(
                response.contains("synthetic-rejected-secret-marker"),
                "validation response must redact rejected secret values");
        assertEquals(
                0L,
                jdbc.queryForObject(
                        "SELECT count(*) FROM exchange_account_credentials WHERE exchange_account_id=?",
                        Long.class,
                        a));
    }

    @Test
    void immutableReceiptsEnforceOwnerVersionAndNoExtraSensitiveFields() {
        long a = account();
        var r = execute(a, create("constraints"));
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbc.update(
                                "UPDATE credential_operation_receipts SET operation_type='DISABLE' WHERE account_id=?",
                                a));
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbc.update(
                                "DELETE FROM credential_operation_receipts WHERE account_id=?", a));
        var wrong =
                new CredentialOperationReceipt(
                        other,
                        a,
                        UUID.randomUUID(),
                        r.type(),
                        r.inputHmac(),
                        r.fingerprintKeyId(),
                        r.keyVersion(),
                        r.result(),
                        r.completedAt());
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        new TransactionTemplate(transactions)
                                .executeWithoutResult(tx -> receipts.save(wrong)));
        String json =
                "{\"credentialId\":"
                        + r.result().credentialId()
                        + ",\"exchangeAccountId\":"
                        + a
                        + ",\"secretKey\":\"synthetic-forbidden\"}";
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbc.update(
                                "INSERT INTO credential_operation_receipts(owner_user_id,account_id,operation_id,operation_type,input_hmac,fingerprint_key_id,key_version,credential_id,result_snapshot,completed_at) VALUES(?,?,'"
                                        + UUID.randomUUID()
                                        + "','CREATE',?,?,1,?,CAST(? AS jsonb),now())",
                                owner,
                                a,
                                r.inputHmac(),
                                r.fingerprintKeyId(),
                                r.result().credentialId(),
                                json));
        assertEquals(
                1L,
                jdbc.queryForObject(
                        "SELECT count(*) FROM credential_operation_receipts WHERE account_id=?",
                        Long.class,
                        a));
    }

    @Test
    void upgradeFromV5PreservesCredentialsAndReceiptsAndFlywayValidates() {
        String target = "credential_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        control.execute("CREATE DATABASE \"" + target + "\"");
        var source =
                new DriverManagerDataSource(
                        baseUrl.substring(0, baseUrl.lastIndexOf('/') + 1) + target,
                        dbUser,
                        dbPassword);
        var db = new JdbcTemplate(source);
        try {
            Flyway.configure()
                    .dataSource(source)
                    .locations("classpath:db/migration")
                    .target("5")
                    .load()
                    .migrate();
            db.update(
                    "INSERT INTO users(username,password_hash) VALUES('upgrade-credential-owner','synthetic-hash')");
            db.update(
                    "INSERT INTO exchange_accounts(owner_user_id,exchange_code,trade_env,account_alias) SELECT id,'OKX','SIM','existing-credential-account' FROM users WHERE username='upgrade-credential-owner'");
            db.update(
                    "INSERT INTO exchange_account_credentials(exchange_account_id,credential_type,encrypted_payload,key_version,masked_access_key) SELECT exchange_account_id,'OKX_API_V5',pgp_sym_encrypt('synthetic-upgrade-material','synthetic-upgrade-key'),1,'syn***de' FROM exchange_accounts");
            var before =
                    db.queryForList(
                            "SELECT credential_id,exchange_account_id,credential_status,encode(encrypted_payload,'hex') AS ciphertext,updated_at FROM exchange_account_credentials");
            var flyway =
                    Flyway.configure()
                            .dataSource(source)
                            .locations("classpath:db/migration")
                            .load();
            flyway.migrate();
            flyway.validate();
            assertEquals("6", flyway.info().current().getVersion().toString());
            assertEquals(
                    before,
                    db.queryForList(
                            "SELECT credential_id,exchange_account_id,credential_status,encode(encrypted_payload,'hex') AS ciphertext,updated_at FROM exchange_account_credentials"));
            assertEquals(
                    0L,
                    db.queryForObject(
                            "SELECT count(*) FROM credential_operation_receipts", Long.class));
            assertEquals(
                    0L,
                    db.queryForObject("SELECT count(*) FROM account_command_receipts", Long.class));
        } finally {
            control.execute("DROP DATABASE \"" + target + "\" WITH (FORCE)");
        }
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
                assertTrue(database.matches("credential_operation_[0-9a-f]{32}"));
                control.execute("DROP DATABASE \"" + database + "\" WITH (FORCE)");
            }
        }
    }
}
