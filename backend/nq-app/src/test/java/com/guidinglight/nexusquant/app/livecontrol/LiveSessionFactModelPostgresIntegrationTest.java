package com.guidinglight.nexusquant.app.livecontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guidinglight.nexusquant.livecontrol.application.AuthenticatedLiveControlActor;
import com.guidinglight.nexusquant.livecontrol.application.LiveSessionControlService;
import com.guidinglight.nexusquant.livecontrol.application.OperatorApprovalCommand;
import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSession;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSessionEvent;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSessionState;
import com.guidinglight.nexusquant.livecontrol.domain.OperatorApproval;
import com.guidinglight.nexusquant.livecontrol.domain.RiskLimitSet;
import com.guidinglight.nexusquant.livecontrol.infra.jdbc.JdbcLiveControlAuthorization;
import com.guidinglight.nexusquant.livecontrol.infra.jdbc.JdbcLiveControlRepository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * GateY-2 disposable PostgreSQL integration：回放 V1→V39，验证真实 FK/trigger/JDBC/并发。
 * 测试只写随机 schema 和脱敏 fixture，不启动 adapter、credential access 或任何交易路径。
 */
class LiveSessionFactModelPostgresIntegrationTest {

    private static final String REQUIRED_PROPERTY = "nq.postgres.smoke.required";
    private static final String URL_PROPERTY = "nq.postgres.smoke.url";
    private static final String USER_PROPERTY = "nq.postgres.smoke.user";
    private static final String PASSWORD_PROPERTY = "nq.postgres.smoke.password";
    private static final Instant NOW = Instant.parse("2026-08-12T02:00:00Z");
    private static final String DIGEST_A = "a".repeat(64);
    private static final String DIGEST_B = "b".repeat(64);

    @Test
    void shouldMigrateAndEnforceFactModelRepositoryAndConcurrency() throws Exception {
        SmokeConfig config = SmokeConfig.fromSystemProperties();
        if (!config.required()) {
            assumeTrue(config.configured(), "PostgreSQL GateY-2 integration is disabled");
        }
        assertTrue(config.configured(), "Missing required nq.postgres.smoke.* properties");

        String schema = "gatey2_" + UUID.randomUUID().toString().replace("-", "");
        Flyway throughV38 = flyway(config, schema, "38");
        throughV38.migrate();
        JdbcTemplate jdbc = jdbc(config, schema);
        ExistingFixture existing = seedExistingFacts(jdbc);
        String historicalFingerprint = historicalFingerprint(jdbc);

        Flyway latest = flyway(config, schema, null);
        latest.migrate();
        latest.validate();
        try {
            assertEquals("39", latest.info().current().getVersion().getVersion());
            assertEquals(historicalFingerprint, historicalFingerprint(jdbc));
            assertSixTablesAndContracts(jdbc);

            JdbcLiveControlRepository repository = new JdbcLiveControlRepository(jdbc);
            JdbcLiveControlAuthorization authorization = new JdbcLiveControlAuthorization(jdbc);
            TransactionTemplate transactions = new TransactionTemplate(
                    new DataSourceTransactionManager(jdbc.getDataSource()));
            RiskLimitSet risk = risk(existing.creatorId());
            LiveSessionControlService service = new LiveSessionControlService(repository, authorization);
            transactions.executeWithoutResult(status -> service.createRiskLimitSet(
                    new AuthenticatedLiveControlActor(existing.creatorId()), risk));
            LiveSession first = session(existing, risk, UUID.randomUUID(), NOW);
            assertSessionReferenceValidation(jdbc, repository, existing, first, risk);
            assertSessionCreationRequiresAuthenticatedCreator(service, transactions, first, risk, existing);
            transactions.executeWithoutResult(status -> service.createSession(
                    new AuthenticatedLiveControlActor(existing.creatorId()),
                    first, risk, createdEvent(first, existing.creatorId())));
            LiveSession persisted = repository.findSession(first.id()).orElseThrow();
            assertEquals(first.id(), persisted.id());
            assertEquals(first.approvalScopeHash(), persisted.approvalScopeHash());
            assertEquals(2L, persisted.nextEventSequence());
            assertEquals(risk, repository.findRiskLimitSet(risk.id()).orElseThrow());

            assertDirectSqlGuards(jdbc, existing, first, risk);
            assertSingleActiveSession(jdbc, existing, risk);
            assertApprovalRequiresCurrentDatabaseRole(
                    jdbc, service, transactions, first, existing);
            assertConcurrentApproval(repository, authorization, transactions, first, existing.approverId());
            assertApprovalVsReject(jdbc, authorization, transactions, existing);
            assertApprovalRequiresExactCurrentScope(repository, first);
            assertTransactionRollback(jdbc, service, transactions, first, existing);
            assertConcurrentEventSequence(repository, transactions, first);
            assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L), jdbc.queryForList(
                    "SELECT sequence_no FROM live_session_events WHERE session_id = ? ORDER BY sequence_no",
                    Long.class,
                    first.id()
            ));
            assertEquals(11L, jdbc.queryForObject(
                    "SELECT next_event_sequence FROM live_sessions WHERE session_id = ?", Long.class, first.id()));
        } finally {
            latest.clean();
        }
    }

    private static Flyway flyway(SmokeConfig config, String schema, String target) {
        var configuration = Flyway.configure()
                .dataSource(config.url(), config.user(), config.password())
                .locations("filesystem:../nq-infra/src/main/resources/db/migration")
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .cleanDisabled(false);
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private static JdbcTemplate jdbc(SmokeConfig config, String schema) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(withCurrentSchema(config.url(), schema));
        dataSource.setUsername(config.user());
        dataSource.setPassword(config.password());
        return new JdbcTemplate(dataSource);
    }

    private static String withCurrentSchema(String url, String schema) {
        return url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema;
    }

    private static ExistingFixture seedExistingFacts(JdbcTemplate jdbc) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        long creator = jdbc.queryForObject(
                "INSERT INTO users(username,password_hash) VALUES (?, 'fixture') RETURNING id",
                Long.class, "gatey_creator_" + suffix);
        long approver = jdbc.queryForObject(
                "INSERT INTO users(username,password_hash) VALUES (?, 'fixture') RETURNING id",
                Long.class, "gatey_approver_" + suffix);
        long operatorRole = jdbc.queryForObject(
                "SELECT id FROM roles WHERE role_code = 'OPERATOR'", Long.class);
        jdbc.update("INSERT INTO user_roles(user_id,role_id) VALUES (?,?)", creator, operatorRole);
        long legacyAccount = jdbc.queryForObject(
                "INSERT INTO accounts(account_code,venue,status) VALUES (?, 'OKX', 'ACTIVE') RETURNING account_id",
                Long.class, "gatey-account-" + suffix);
        long exchangeAccount = jdbc.queryForObject("""
                INSERT INTO exchange_accounts(
                    owner_user_id, exchange_code, trade_env, account_alias, legacy_account_id, status
                ) VALUES (?, 'OKX', 'LIVE', ?, ?, 'ACTIVE') RETURNING exchange_account_id
                """, Long.class, creator, "gatey-live-" + suffix, legacyAccount);
        long credential = jdbc.queryForObject("""
                INSERT INTO exchange_account_credentials(
                    exchange_account_id, credential_type, encrypted_payload, key_version, cipher_suite,
                    verification_status, is_active
                ) VALUES (?, 'OKX_API_V5', ?, 1, 'PGP_SYM_AES256', 'VERIFIED', TRUE)
                RETURNING credential_id
                """, Long.class, exchangeAccount, new byte[]{1, 2, 3});
        String releaseId = seedRelease(jdbc, suffix, legacyAccount);
        jdbc.update("""
                UPDATE strategy_release_admission_state
                SET release_artifact_digest = ?, manifest_fingerprint = ?,
                    manifest_schema_version = 'strategy-release-manifest.v1', identity_bound_at = ?
                WHERE publish_record_id = ?
                """, DIGEST_A, DIGEST_B, Timestamp.from(NOW.minusSeconds(60)), releaseId);
        assertEquals(1L, jdbc.queryForObject(
                "SELECT admission_revision FROM strategy_release_admission_state WHERE publish_record_id = ?",
                Long.class, releaseId));
        return new ExistingFixture(creator, approver, legacyAccount, exchangeAccount, credential, releaseId);
    }

    private static String seedRelease(JdbcTemplate jdbc, String suffix, long legacyAccount) {
        String strategyId = "gatey-strategy-" + suffix;
        String strategyCode = "GATEY_" + suffix.toUpperCase(java.util.Locale.ROOT);
        String versionId = "gatey-version-" + suffix;
        String researchId = "gatey-research-" + suffix;
        String configId = "gatey-config-" + suffix;
        String runId = "gatey-run-" + suffix;
        String evalId = "gatey-eval-" + suffix;
        String publishId = "gatey-publish-" + suffix;
        UUID datasetId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO strategy_definitions(
                    strategy_id,strategy_code,strategy_name,strategy_type,exchange_code,account_id,
                    trade_env,enabled,config_snapshot,version
                ) VALUES (?,?,'GateY fixture','CONTROL_PLANE','OKX',?,'SIM',FALSE,'{}'::jsonb,1)
                """, strategyId, strategyCode, legacyAccount);
        jdbc.update("INSERT INTO research_configs(research_config_id,source_strategy_id,name,strategy_snapshot) "
                + "VALUES (?,?,'GateY fixture','{}'::jsonb)", researchId, strategyId);
        jdbc.update("INSERT INTO backtest_configs(backtest_config_id,research_config_id,name) "
                + "VALUES (?,?,'GateY fixture')", configId, researchId);
        jdbc.update("""
                INSERT INTO marketdata_datasets(
                    dataset_id,dataset_name,exchange_code,market_type,symbol,"interval",start_time,end_time,
                    status,quality_status,source,created_by
                ) VALUES (?,?,'OKX','SPOT','BTC-USDT','1m',?,?,'READY','OK','GATEY2_TEST','test')
                """, datasetId, "gatey-dataset-" + suffix, Timestamp.from(NOW), Timestamp.from(NOW.plusSeconds(60)));
        jdbc.update("""
                INSERT INTO strategy_versions(
                    strategy_version_id,strategy_code,version,version_name,status,checksum,created_by
                ) VALUES (?,?,1,'GateY fixture','ACTIVE',?,'test')
                """, versionId, strategyCode, DIGEST_A);
        jdbc.update("""
                INSERT INTO backtest_runs(
                    backtest_run_id,backtest_config_id,research_config_id,source_strategy_id,status,
                    strategy_snapshot,strategy_version_id,backtest_config_snapshot,dataset_snapshot_json,requested_at
                ) VALUES (?,?,?,?,'SUCCEEDED','{}'::jsonb,?,'{}'::jsonb,
                          jsonb_build_object('datasetId',?),?)
                """, runId, configId, researchId, strategyId, versionId, datasetId.toString(), Timestamp.from(NOW));
        jdbc.update("INSERT INTO backtest_eval_reports(eval_report_id,backtest_run_id,evaluation_status,evaluated_at) "
                + "VALUES (?,?,'SUCCEEDED',?)", evalId, runId, Timestamp.from(NOW));
        jdbc.update("""
                INSERT INTO backtest_publish_records(
                    publish_record_id,backtest_run_id,research_config_id,backtest_config_id,source_strategy_id,
                    eval_report_id,strategy_version_id,publish_status,publish_name,
                    artifact_storage_key,manifest_storage_key
                ) VALUES (?,?,?,?,?,?,?,'SUCCEEDED','GateY fixture',?,?)
                """, publishId, runId, researchId, configId, strategyId, evalId, versionId,
                "artifact_" + suffix, "manifest_" + suffix);
        return publishId;
    }

    private static String historicalFingerprint(JdbcTemplate jdbc) {
        return jdbc.queryForObject("""
                SELECT md5(concat_ws('|',
                    (SELECT count(*) FROM users),
                    (SELECT count(*) FROM roles),
                    (SELECT count(*) FROM user_roles),
                    (SELECT count(*) FROM exchange_accounts),
                    (SELECT count(*) FROM exchange_account_credentials),
                    (SELECT count(*) FROM backtest_publish_records),
                    (SELECT count(*) FROM strategy_release_admission_state),
                    (SELECT string_agg(publish_record_id || ':' || admission_revision, ',' ORDER BY publish_record_id)
                       FROM strategy_release_admission_state)))
                """, String.class);
    }

    private static void assertSixTablesAndContracts(JdbcTemplate jdbc) {
        assertEquals(6, jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = current_schema() AND table_name IN (
                    'risk_limit_sets','live_sessions','live_session_events','operator_approvals',
                    'execution_intents','execution_receipts')
                """, Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM risk_limit_sets", Integer.class));
        assertEquals(4, jdbc.queryForObject("""
                SELECT count(*) FROM pg_trigger
                WHERE NOT tgisinternal AND tgname IN (
                    'trg_risk_limit_sets_immutable','trg_live_session_events_append_only',
                    'trg_operator_approvals_append_only','trg_execution_receipts_append_only')
                """, Integer.class));
        assertTrue(jdbc.queryForObject("""
                SELECT count(*) > 0 FROM pg_description d
                JOIN pg_class c ON c.oid=d.objoid
                WHERE c.relname='live_sessions' AND d.objsubid > 0 AND d.description IS NOT NULL
                """, Boolean.class));
    }

    private static void assertDirectSqlGuards(
            JdbcTemplate jdbc,
            ExistingFixture existing,
            LiveSession session,
            RiskLimitSet risk
    ) {
        assertSqlState23514(() -> jdbc.update(
                "UPDATE risk_limit_sets SET max_open_orders = 4 WHERE risk_limit_set_id = ?", risk.id()));
        assertSqlState23514(() -> jdbc.update(
                "DELETE FROM risk_limit_sets WHERE risk_limit_set_id = ?", risk.id()));

        assertSqlState23514(() -> jdbc.update(
                "UPDATE live_session_events SET reason_code='MUTATED' WHERE session_id=?", session.id()));
        assertSqlState23514(() -> jdbc.update(
                "DELETE FROM live_session_events WHERE session_id=?", session.id()));

        UUID approvalId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO operator_approvals(
                    approval_id,session_id,scope_hash,release_digest,risk_limit_set_digest,
                    approver_id,approver_role,decision,reason,approved_at,expires_at
                ) VALUES (?,?,?,?,?,?,'LIVE_APPROVER','REJECTED','fixture',?,?)
                """, approvalId, session.id(), session.approvalScopeHash(), session.releaseDigest(),
                session.riskLimitSetDigest(), existing.approverId(), Timestamp.from(NOW), Timestamp.from(NOW.plusSeconds(10)));
        assertSqlState23514(() -> jdbc.update(
                "UPDATE operator_approvals SET reason='changed' WHERE approval_id=?", approvalId));
        assertSqlState23514(() -> jdbc.update(
                "DELETE FROM operator_approvals WHERE approval_id=?", approvalId));

        String orderId = "gatey-order-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("""
                INSERT INTO orders(
                    order_id,account_id,venue,exchange_code,trade_env,symbol,client_order_id,
                    side,type,price,qty,status,trace_id
                ) VALUES (?,?,'OKX','OKX','SIM','BTC-USDT',?,'BUY','LIMIT',10,1,'CREATED','gatey-test')
                """, orderId, existing.legacyAccountId(), "client-" + orderId);
        UUID intentId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO execution_intents(
                    intent_id,session_id,sequence,action,symbol,side,order_type,quantity,limit_price,
                    payload_hash_schema_version,payload_hash,client_order_id,local_order_id,state
                ) VALUES (?,?,1,'PLACE','BTC-USDT','BUY','LIMIT',1,10,
                          'execution-intent-payload.v1',?,?,?,'CREATED')
                """, intentId, session.id(), "c".repeat(64), "client-" + orderId, orderId);
        UUID receiptId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO execution_receipts(
                    receipt_id,intent_id,attempt_no,outcome,received_at,payload_digest,payload_digest_schema_version
                ) VALUES (?,?,1,'UNKNOWN',?,?,'execution-receipt-envelope.v1')
                """, receiptId, intentId, Timestamp.from(NOW), "d".repeat(64));
        assertSqlState23514(() -> jdbc.update(
                "UPDATE execution_receipts SET outcome='TIMEOUT' WHERE receipt_id=?", receiptId));
        assertSqlState23514(() -> jdbc.update(
                "DELETE FROM execution_receipts WHERE receipt_id=?", receiptId));
        assertSqlState23514(() -> jdbc.update(
                "UPDATE execution_intents SET symbol='ETH-USDT',version=2 WHERE intent_id=?", intentId));
        assertSqlState23514(() -> jdbc.update(
                "UPDATE execution_intents SET state='SEND_SUCCEEDED',version=2 WHERE intent_id=?", intentId));
        assertSqlState23514(() -> jdbc.update(
                "INSERT INTO execution_intents(intent_id,session_id,sequence,action,symbol,side,order_type,quantity," +
                        "limit_price,payload_hash_schema_version,payload_hash,client_order_id,local_order_id,state) " +
                        "VALUES (?,?,-1,'PLACE','BTC-USDT','BUY','LIMIT',1,10," +
                        "'execution-intent-payload.v1',?,?,?,'CREATED')",
                UUID.randomUUID(), session.id(), "e".repeat(64), "negative-" + orderId, orderId));
        assertSqlState23514(() -> jdbc.update(
                "UPDATE execution_intents SET state='CLAIMED',version=2,claimed_by=NULL,claim_token=NULL," +
                        "claimed_at=NULL,lease_expires_at=NULL WHERE intent_id=?", intentId));
        UUID firstToken = UUID.randomUUID();
        jdbc.update("UPDATE execution_intents SET state='CLAIMED',version=2,claimed_by='worker-a',claim_token=?," +
                        "claimed_at=CURRENT_TIMESTAMP - INTERVAL '2 minutes'," +
                        "lease_expires_at=CURRENT_TIMESTAMP - INTERVAL '1 second' " +
                        "WHERE intent_id=?", firstToken, intentId);
        UUID reclaimedToken = UUID.randomUUID();
        jdbc.update("UPDATE execution_intents SET version=3,claimed_by='worker-b',claim_token=?," +
                        "claimed_at=CURRENT_TIMESTAMP,lease_expires_at=CURRENT_TIMESTAMP + INTERVAL '5 minutes' " +
                        "WHERE intent_id=?", reclaimedToken, intentId);
        assertSqlState23514(() -> jdbc.update(
                "UPDATE execution_intents SET version=4,claimed_by='worker-c',claim_token=?," +
                        "claimed_at=CURRENT_TIMESTAMP,lease_expires_at=CURRENT_TIMESTAMP + INTERVAL '5 minutes' " +
                        "WHERE intent_id=?", UUID.randomUUID(), intentId));
        assertSqlState23514(() -> jdbc.update(
                "UPDATE execution_intents SET version=4,claimed_by='worker-c',claim_token=?," +
                        "claimed_at=CURRENT_TIMESTAMP,lease_expires_at=CURRENT_TIMESTAMP + INTERVAL '10 minutes' " +
                        "WHERE intent_id=?", UUID.randomUUID(), intentId));
        jdbc.update("UPDATE execution_intents SET state='SEND_STARTED',version=4,send_started_at=CURRENT_TIMESTAMP " +
                "WHERE intent_id=?", intentId);
        assertSqlState23514(() -> jdbc.update(
                "UPDATE execution_intents SET state='UNKNOWN',version=5,claimed_by='worker-c' WHERE intent_id=?",
                intentId));
        for (String outcome : List.of("QUERY_CONFIRMED", "QUERY_NOT_FOUND")) {
            jdbc.update("""
                    INSERT INTO execution_receipts(
                        receipt_id,intent_id,attempt_no,outcome,received_at,payload_digest,payload_digest_schema_version
                    ) VALUES (?,?,?,?,?,?,'execution-receipt-envelope.v1')
                    """, UUID.randomUUID(), intentId, outcome.equals("QUERY_CONFIRMED") ? 2 : 3, outcome,
                    Timestamp.from(NOW), outcome.equals("QUERY_CONFIRMED") ? "6".repeat(64) : "7".repeat(64));
        }
        assertSqlState23514(() -> jdbc.update(
                "UPDATE live_sessions SET capital_cap=capital_cap+1,approval_scope_hash=?,version=version+1 " +
                        "WHERE session_id=?", "8".repeat(64), session.id()));
        assertSqlState23514(() -> jdbc.update(
                "UPDATE live_sessions SET state='LIVE_ACTIVE',version=version+1 WHERE session_id=?", session.id()));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO execution_receipts(
                    receipt_id,intent_id,attempt_no,outcome,received_at,payload_digest,payload_digest_schema_version
                ) VALUES (?,?,1,'UNKNOWN',?,?,'execution-receipt-envelope.v1')
                """, UUID.randomUUID(), intentId, Timestamp.from(NOW), "e".repeat(64)));
    }

    private static void assertSessionReferenceValidation(
            JdbcTemplate jdbc,
            JdbcLiveControlRepository repository,
            ExistingFixture existing,
            LiveSession session,
            RiskLimitSet risk
    ) {
        assertTrue(repository.lockAndValidateSessionReferences(session));
        assertTrue(!repository.lockAndValidateSessionReferences(copySession(
                session, existing.approverId(), session.credentialReference(), session.strategyReleaseId(),
                session.releaseDigest(), session.releaseAdmissionRevision(), session.riskLimitSetId(),
                session.riskLimitSetDigest(), session.approvalScopeHash())));
        assertTrue(!repository.lockAndValidateSessionReferences(copySession(
                session, session.ownerId(), Long.MAX_VALUE, session.strategyReleaseId(),
                session.releaseDigest(), session.releaseAdmissionRevision(), session.riskLimitSetId(),
                session.riskLimitSetDigest(), session.approvalScopeHash())));
        assertTrue(!repository.lockAndValidateSessionReferences(copySession(
                session, session.ownerId(), session.credentialReference(), session.strategyReleaseId(),
                DIGEST_B, session.releaseAdmissionRevision(), session.riskLimitSetId(),
                session.riskLimitSetDigest(), session.approvalScopeHash())));
        assertTrue(!repository.lockAndValidateSessionReferences(copySession(
                session, session.ownerId(), session.credentialReference(), session.strategyReleaseId(),
                session.releaseDigest(), session.releaseAdmissionRevision() + 1, session.riskLimitSetId(),
                session.riskLimitSetDigest(), session.approvalScopeHash())));
        assertTrue(!repository.lockAndValidateSessionReferences(copySession(
                session, session.ownerId(), session.credentialReference(), session.strategyReleaseId(),
                session.releaseDigest(), session.releaseAdmissionRevision(), risk.id(), DIGEST_B,
                session.approvalScopeHash())));

        jdbc.update("UPDATE exchange_accounts SET trade_env='SIM' WHERE exchange_account_id=?",
                existing.exchangeAccountId());
        assertTrue(!repository.lockAndValidateSessionReferences(session));
        jdbc.update("UPDATE exchange_accounts SET trade_env='LIVE', exchange_code='BINANCE' WHERE exchange_account_id=?",
                existing.exchangeAccountId());
        assertTrue(!repository.lockAndValidateSessionReferences(session));
        jdbc.update("UPDATE exchange_accounts SET exchange_code='OKX' WHERE exchange_account_id=?",
                existing.exchangeAccountId());
        assertTrue(repository.lockAndValidateSessionReferences(session));
    }

    private static void assertApprovalRequiresExactCurrentScope(
            JdbcLiveControlRepository repository,
            LiveSession session
    ) {
        assertTrue(repository.findValidApproval(session, NOW.plusSeconds(10)).isPresent());
        assertTrue(repository.findValidApproval(copySession(
                session, session.ownerId(), session.credentialReference(), session.strategyReleaseId(),
                session.releaseDigest(), session.releaseAdmissionRevision(), session.riskLimitSetId(),
                session.riskLimitSetDigest(), "9".repeat(64)), NOW.plusSeconds(10)).isEmpty());
        assertTrue(repository.findValidApproval(copySession(
                session, session.ownerId(), session.credentialReference(), session.strategyReleaseId(),
                DIGEST_B, session.releaseAdmissionRevision(), session.riskLimitSetId(),
                session.riskLimitSetDigest(), session.approvalScopeHash()), NOW.plusSeconds(10)).isEmpty());
        assertTrue(repository.findValidApproval(copySession(
                session, session.ownerId(), session.credentialReference(), session.strategyReleaseId(),
                session.releaseDigest(), session.releaseAdmissionRevision(), session.riskLimitSetId(),
                DIGEST_B, session.approvalScopeHash()), NOW.plusSeconds(10)).isEmpty());
    }

    private static void assertSingleActiveSession(
            JdbcTemplate jdbc,
            ExistingFixture existing,
            RiskLimitSet risk
    ) {
        LiveSession duplicate = session(existing, risk, UUID.randomUUID(), NOW.plusSeconds(1));
        JdbcLiveControlRepository repository = new JdbcLiveControlRepository(jdbc);
        assertThrows(DataIntegrityViolationException.class, () -> repository.createSession(duplicate));
    }

    private static void assertConcurrentApproval(
            JdbcLiveControlRepository repository,
            JdbcLiveControlAuthorization authorization,
            TransactionTemplate transactions,
            LiveSession session,
            long approverId
    ) throws Exception {
        LiveSessionControlService service = new LiveSessionControlService(repository, authorization);
        AuthenticatedLiveControlActor actor = new AuthenticatedLiveControlActor(approverId);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Object>> results = List.of(1, 2).stream().map(index -> executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(10, TimeUnit.SECONDS));
                try {
                    return transactions.execute(status -> service.decideIdempotently(actor, new OperatorApprovalCommand(
                            UUID.randomUUID(), session.id(), session.version(), session.approvalScopeHash(),
                            OperatorApproval.Decision.APPROVED,
                            "approved", NOW.plusSeconds(index), NOW.plusSeconds(120),
                            "request-" + index, "trace-" + index, "approval-" + index, "f".repeat(64)
                    )));
                } catch (LiveControlException ex) {
                    return ex.code();
                }
            })).toList();
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<Object> values = List.of(results.get(0).get(10, TimeUnit.SECONDS), results.get(1).get(10, TimeUnit.SECONDS));
            assertEquals(1, values.stream().filter(OperatorApproval.class::isInstance).count());
            assertEquals(1, values.stream().filter(value -> "APPROVAL_STATE_CONFLICT".equals(value)
                    || "APPROVAL_STALE_SCOPE".equals(value)).count());
        }
    }

    private static void assertApprovalVsReject(
            JdbcTemplate jdbc,
            JdbcLiveControlAuthorization authorization,
            TransactionTemplate transactions,
            ExistingFixture existing
    ) throws Exception {
        ExistingFixture raceFixture = seedExistingFacts(jdbc);
        long liveApproverRole = jdbc.queryForObject(
                "SELECT id FROM roles WHERE role_code='LIVE_APPROVER'", Long.class);
        jdbc.update("INSERT INTO user_roles(user_id,role_id) VALUES (?,?)",
                raceFixture.approverId(), liveApproverRole);
        JdbcLiveControlRepository repository = new JdbcLiveControlRepository(jdbc);
        RiskLimitSet risk = risk(raceFixture.creatorId(), 2);
        repository.createRiskLimitSet(risk);
        LiveSession session = session(raceFixture, risk, UUID.randomUUID(), NOW);
        repository.createSession(session);
        repository.appendSessionEvent(createdEvent(session, raceFixture.creatorId()));
        LiveSessionControlService service = new LiveSessionControlService(repository, authorization);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Object>> results = List.of(
                    OperatorApproval.Decision.APPROVED, OperatorApproval.Decision.REJECTED).stream()
                    .map(decision -> executor.submit(() -> {
                        ready.countDown();
                        assertTrue(start.await(10, TimeUnit.SECONDS));
                        try {
                            return transactions.execute(status -> service.decide(
                                    new AuthenticatedLiveControlActor(raceFixture.approverId()),
                                    new OperatorApprovalCommand(
                                            UUID.randomUUID(), session.id(), session.version(),
                                            session.approvalScopeHash(), decision, decision.name(), NOW,
                                            NOW.plusSeconds(120), "race-" + decision, "trace-" + decision,
                                            "approval-race-" + decision, "5".repeat(64))));
                        } catch (LiveControlException ex) {
                            return ex.code();
                        }
                    })).toList();
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<Object> values = List.of(results.get(0).get(10, TimeUnit.SECONDS),
                    results.get(1).get(10, TimeUnit.SECONDS));
            assertEquals(1, values.stream().filter(OperatorApproval.class::isInstance).count());
            assertEquals(1, values.stream().filter(value -> "APPROVAL_STATE_CONFLICT".equals(value)
                    || "APPROVAL_STALE_SCOPE".equals(value)).count());
        }
    }

    private static void assertTransactionRollback(
            JdbcTemplate jdbc,
            LiveSessionControlService service,
            TransactionTemplate transactions,
            LiveSession template,
            ExistingFixture existing
    ) {
        RiskLimitSet rollbackRisk = risk(existing.creatorId());
        LiveSession badCreatedEventSession = session(existing, rollbackRisk, UUID.randomUUID(), NOW);
        assertThrows(DataIntegrityViolationException.class, () -> transactions.executeWithoutResult(status -> {
            service.createRiskLimitSet(
                    new AuthenticatedLiveControlActor(existing.creatorId()), rollbackRisk);
            service.createSession(
                    new AuthenticatedLiveControlActor(existing.creatorId()),
                    badCreatedEventSession,
                    rollbackRisk,
                    new LiveSessionEvent(
                            UUID.randomUUID(), badCreatedEventSession.id(), 1, null,
                            LiveSessionState.APPROVAL_PENDING, "CREATE", existing.creatorId(),
                            "rollback-request", "rollback-trace", "SESSION_CREATED",
                            "rollback-created", "1".repeat(64), "{}", NOW));
            jdbc.update("INSERT INTO live_session_events(event_id,session_id,sequence_no,to_state,command," +
                    "request_id,trace_id,reason_code,idempotency_key,command_payload_hash," +
                    "command_payload_schema_version,metadata) VALUES (?,?,1,'APPROVAL_PENDING','CREATED'," +
                    "'duplicate','duplicate','CREATED','duplicate',?,'live-session-command.v1','{}'::jsonb)",
                    UUID.randomUUID(), badCreatedEventSession.id(), "2".repeat(64));
        }));
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM live_sessions WHERE session_id=?", Integer.class, badCreatedEventSession.id()));
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM risk_limit_sets WHERE risk_limit_set_id=?", Integer.class, rollbackRisk.id()));

        ExistingFixture approvalRollbackFixture = seedExistingFacts(jdbc);
        long liveApproverRole = jdbc.queryForObject(
                "SELECT id FROM roles WHERE role_code='LIVE_APPROVER'", Long.class);
        jdbc.update("INSERT INTO user_roles(user_id,role_id) VALUES (?,?)",
                approvalRollbackFixture.approverId(), liveApproverRole);
        RiskLimitSet approvalRollbackRisk = risk(approvalRollbackFixture.creatorId(), 3);
        LiveSession approvalRollbackSession = session(
                approvalRollbackFixture, approvalRollbackRisk, UUID.randomUUID(), NOW.plusSeconds(1));
        transactions.executeWithoutResult(status -> {
            service.createRiskLimitSet(
                    new AuthenticatedLiveControlActor(approvalRollbackFixture.creatorId()), approvalRollbackRisk);
            service.createSession(
                    new AuthenticatedLiveControlActor(approvalRollbackFixture.creatorId()),
                    approvalRollbackSession,
                    approvalRollbackRisk,
                    createdEvent(approvalRollbackSession, approvalRollbackFixture.creatorId()));
        });

        UUID approvalId = UUID.randomUUID();
        assertThrows(DataIntegrityViolationException.class, () -> transactions.executeWithoutResult(status -> {
            service.decide(
                    new AuthenticatedLiveControlActor(approvalRollbackFixture.approverId()),
                    new OperatorApprovalCommand(
                            approvalId, approvalRollbackSession.id(), approvalRollbackSession.version(),
                            approvalRollbackSession.approvalScopeHash(),
                            OperatorApproval.Decision.APPROVED, "rollback approval", NOW,
                            NOW.plusSeconds(120), "rollback-approval", "rollback-trace",
                            "rollback-approval", "4".repeat(64)));
            jdbc.update("INSERT INTO live_session_events(event_id,session_id,sequence_no,to_state,command," +
                    "request_id,trace_id,reason_code,idempotency_key,command_payload_hash," +
                    "command_payload_schema_version,metadata) VALUES (?,?,1,'APPROVED','APPROVE'," +
                    "'duplicate','duplicate','APPROVED','duplicate',?,'live-session-command.v1','{}'::jsonb)",
                    UUID.randomUUID(), approvalRollbackSession.id(), "3".repeat(64));
        }));
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM operator_approvals WHERE approval_id=?", Integer.class, approvalId));
        assertEquals("APPROVAL_PENDING", jdbc.queryForObject(
                "SELECT state FROM live_sessions WHERE session_id=?", String.class, approvalRollbackSession.id()));
    }

    private static void assertApprovalRequiresCurrentDatabaseRole(
            JdbcTemplate jdbc,
            LiveSessionControlService service,
            TransactionTemplate transactions,
            LiveSession session,
            ExistingFixture existing
    ) {
        long approverId = existing.approverId();
        AuthenticatedLiveControlActor actor = new AuthenticatedLiveControlActor(approverId);
        OperatorApprovalCommand command = new OperatorApprovalCommand(
                UUID.randomUUID(), session.id(), session.version(), session.approvalScopeHash(),
                OperatorApproval.Decision.APPROVED, "authorization regression", NOW,
                NOW.plusSeconds(120), "rbac-request", "rbac-trace", "rbac-approval", "f".repeat(64)
        );
        LiveControlException missingRole = assertThrows(LiveControlException.class,
                () -> transactions.execute(status -> service.decide(actor, command)));
        assertEquals("LIVE_APPROVER_ROLE_REQUIRED", missingRole.code());
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM operator_approvals WHERE approval_id = ?", Integer.class, command.approvalId()));

        long roleId = jdbc.queryForObject("""
                INSERT INTO roles(role_code,description)
                VALUES ('LIVE_APPROVER','GateY LIVE 审批者')
                RETURNING id
                """, Long.class);
        jdbc.update("INSERT INTO user_roles(user_id,role_id) VALUES (?,?)", approverId, roleId);

        OperatorApprovalCommand stale = new OperatorApprovalCommand(
                UUID.randomUUID(), session.id(), session.version() + 1, session.approvalScopeHash(),
                OperatorApproval.Decision.APPROVED, "stale regression", NOW,
                NOW.plusSeconds(120), "stale-request", "stale-trace", "stale-approval", "f".repeat(64)
        );
        LiveControlException staleVersion = assertThrows(LiveControlException.class,
                () -> transactions.execute(status -> service.decide(actor, stale)));
        assertEquals("APPROVAL_STALE_SCOPE", staleVersion.code());

        jdbc.update("INSERT INTO user_roles(user_id,role_id) VALUES (?,?)", existing.creatorId(), roleId);
        OperatorApprovalCommand selfApproval = new OperatorApprovalCommand(
                UUID.randomUUID(), session.id(), session.version(), session.approvalScopeHash(),
                OperatorApproval.Decision.APPROVED, "self approval regression", NOW,
                NOW.plusSeconds(120), "self-request", "self-trace", "self-approval", "f".repeat(64)
        );
        LiveControlException self = assertThrows(LiveControlException.class,
                () -> transactions.execute(status -> service.decide(
                        new AuthenticatedLiveControlActor(existing.creatorId()), selfApproval)));
        assertEquals("SELF_APPROVAL_FORBIDDEN", self.code());
        jdbc.update("DELETE FROM user_roles WHERE user_id=? AND role_id=?", existing.creatorId(), roleId);
        jdbc.update("DELETE FROM user_roles WHERE user_id=? AND role_id=?", approverId, roleId);
        LiveControlException revoked = assertThrows(LiveControlException.class,
                () -> transactions.execute(status -> service.decide(actor, new OperatorApprovalCommand(
                        UUID.randomUUID(), session.id(), session.version(), session.approvalScopeHash(),
                        OperatorApproval.Decision.APPROVED, "revoked regression", NOW,
                        NOW.plusSeconds(120), "revoked-request", "revoked-trace",
                        "revoked-approval", "f".repeat(64)))));
        assertEquals("LIVE_APPROVER_ROLE_REQUIRED", revoked.code());
        jdbc.update("INSERT INTO user_roles(user_id,role_id) VALUES (?,?)", approverId, roleId);
        jdbc.update("UPDATE users SET enabled=FALSE WHERE id=?", approverId);
        LiveControlException disabled = assertThrows(LiveControlException.class,
                () -> transactions.execute(status -> service.decide(actor, new OperatorApprovalCommand(
                        UUID.randomUUID(), session.id(), session.version(), session.approvalScopeHash(),
                        OperatorApproval.Decision.APPROVED, "disabled regression", NOW,
                        NOW.plusSeconds(120), "disabled-request", "disabled-trace",
                        "disabled-approval", "f".repeat(64)))));
        assertEquals("LIVE_APPROVER_ROLE_REQUIRED", disabled.code());
        jdbc.update("UPDATE users SET enabled=TRUE WHERE id=?", approverId);
    }

    private static void assertSessionCreationRequiresAuthenticatedCreator(
            LiveSessionControlService service,
            TransactionTemplate transactions,
            LiveSession session,
            RiskLimitSet risk,
            ExistingFixture existing
    ) {
        LiveControlException forgedCreator = assertThrows(LiveControlException.class,
                () -> transactions.execute(status -> service.createSession(
                        new AuthenticatedLiveControlActor(existing.approverId()),
                        session, risk, createdEvent(session, existing.approverId()))));
        assertEquals("LIVE_SESSION_CREATOR_IDENTITY_MISMATCH", forgedCreator.code());
    }

    private static void assertConcurrentEventSequence(
            JdbcLiveControlRepository repository,
            TransactionTemplate transactions,
            LiveSession session
    ) throws Exception {
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            List<Future<LiveSessionEvent>> results = java.util.stream.IntStream.rangeClosed(1, 8)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        assertTrue(start.await(10, TimeUnit.SECONDS));
                        return transactions.execute(status -> repository.appendSessionEvent(new LiveSessionEvent(
                                UUID.randomUUID(), session.id(), 1, null, LiveSessionState.APPROVAL_PENDING,
                                "TEST_" + index, null, "request-" + index, "trace-" + index,
                                "TEST", "event-" + index, "1".repeat(64), "{}", NOW.plusSeconds(index)
                        )));
                    })).toList();
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            for (Future<LiveSessionEvent> result : results) {
                assertNotNull(result.get(10, TimeUnit.SECONDS));
            }
        }
    }

    private static void appendEventSql(JdbcTemplate jdbc, LiveSession session, long actorId, long sequence) {
        jdbc.update("""
                INSERT INTO live_session_events(
                    event_id,session_id,sequence_no,from_state,to_state,command,actor_id,request_id,trace_id,
                    reason_code,idempotency_key,command_payload_hash,command_payload_schema_version,metadata
                ) VALUES (?,?,?,NULL,'APPROVAL_PENDING','CREATED',?,'request','trace','CREATED',?,?,
                          'live-session-command.v1','{}'::jsonb)
                """, UUID.randomUUID(), session.id(), sequence, actorId, "created-" + sequence, "2".repeat(64));
    }

    private static RiskLimitSet risk(long createdBy) {
        return risk(createdBy, 1);
    }

    private static RiskLimitSet risk(long createdBy, int version) {
        return new RiskLimitSet(
                UUID.randomUUID(), version, decimal("100"), decimal("10"), decimal("50"), decimal("5"), decimal("8"),
                3, 20, List.of("BTC-USDT", "ETH-USDT"), 600, decimal("15"), decimal("20"),
                1000, 9900, createdBy, NOW.minusSeconds(120)
        );
    }

    private static LiveSession session(
            ExistingFixture fixture,
            RiskLimitSet risk,
            UUID sessionId,
            Instant createdAt
    ) {
        return LiveSession.create(
                sessionId, fixture.creatorId(), fixture.exchangeAccountId(), fixture.releaseId(), DIGEST_A, 1,
                risk.id(), risk.canonicalDigest(), fixture.credentialId(), List.of("BTC-USDT"), decimal("25"),
                NOW, NOW.plusSeconds(300), fixture.creatorId(), createdAt
        );
    }

    private static LiveSession copySession(
            LiveSession source,
            long ownerId,
            long credentialReference,
            String releaseId,
            String releaseDigest,
            long releaseRevision,
            UUID riskLimitSetId,
            String riskDigest,
            String scopeHash
    ) {
        return new LiveSession(
                source.id(), ownerId, source.exchangeAccountId(), source.venue(), releaseId, releaseDigest,
                releaseRevision, riskLimitSetId, riskDigest, credentialReference, source.symbolAllowlist(),
                source.capitalCap(), source.executionWindowStart(), source.executionWindowEnd(), source.state(),
                source.version(), scopeHash, source.nextEventSequence(), source.createdBy(), source.createdAt(),
                source.updatedAt()
        );
    }

    private static LiveSessionEvent createdEvent(LiveSession session, long actorId) {
        return new LiveSessionEvent(
                UUID.randomUUID(), session.id(), 1, null, LiveSessionState.APPROVAL_PENDING,
                "CREATE", actorId, "create-request", "create-trace", "SESSION_CREATED",
                "create-session", "2".repeat(64), "{}", NOW
        );
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private static void assertSqlState23514(Runnable action) {
        DataIntegrityViolationException failure = assertThrows(DataIntegrityViolationException.class, action::run);
        Throwable cause = failure;
        while (cause != null && !(cause instanceof PSQLException)) {
            cause = cause.getCause();
        }
        assertNotNull(cause);
        assertEquals("23514", ((PSQLException) cause).getSQLState());
    }

    private record ExistingFixture(
            long creatorId,
            long approverId,
            long legacyAccountId,
            long exchangeAccountId,
            long credentialId,
            String releaseId
    ) {
    }

    private record SmokeConfig(String url, String user, String password, boolean required) {
        static SmokeConfig fromSystemProperties() {
            return new SmokeConfig(
                    property(URL_PROPERTY), property(USER_PROPERTY), property(PASSWORD_PROPERTY),
                    Boolean.parseBoolean(property(REQUIRED_PROPERTY))
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
