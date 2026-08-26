package com.guidinglight.nexusquant.app.livecontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guidinglight.nexusquant.livecontrol.application.AuthenticatedLiveControlActor;
import com.guidinglight.nexusquant.livecontrol.application.LiveSessionControlService;
import com.guidinglight.nexusquant.livecontrol.application.OperatorPilotAuthorityService;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSession;
import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSessionAuthorityType;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSessionEvent;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSessionState;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSessionCommand;
import com.guidinglight.nexusquant.livecontrol.domain.OperatorPilotAuthority;
import com.guidinglight.nexusquant.livecontrol.infra.jdbc.JdbcLiveControlAuthorization;
import com.guidinglight.nexusquant.livecontrol.infra.jdbc.JdbcLiveControlRepository;
import com.guidinglight.nexusquant.livecontrol.infra.jdbc.JdbcOperatorPilotAuthorityRepository;
import com.guidinglight.nexusquant.livecontrol.infra.jdbc.JdbcPilotPrePlaceRecoveryRepository;
import com.guidinglight.nexusquant.account.infra.jdbc.CanonicalLegacyAccountBridgeService;
import com.guidinglight.nexusquant.account.infra.jdbc.JdbcExchangeAccountRepository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * V44 disposable PostgreSQL：operator authority、conditional session 与 lease lifecycle。
 */
class OperatorPilotAuthorityPostgresIntegrationTest {

    @Test
    void migratesExactV44ToV45AndValidates() {
        String url = System.getProperty("nq.postgres.smoke.url", "").trim();
        String user = System.getProperty("nq.postgres.smoke.user", "").trim();
        String password = System.getProperty("nq.postgres.smoke.password", "").trim();
        boolean required = Boolean.parseBoolean(System.getProperty("nq.postgres.smoke.required", "false"));
        if (!required) {
            assumeTrue(!url.isBlank() && !user.isBlank() && !password.isBlank(),
                    "PostgreSQL V44 to V45 integration is disabled");
        }
        String schema = "gatey45upgrade_" + UUID.randomUUID().toString().replace("-", "");
        String schemaUrl = url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema + ",public";
        Flyway throughV44 = Flyway.configure().dataSource(schemaUrl, user, password).schemas(schema)
                .defaultSchema(schema).createSchemas(true).cleanDisabled(false).target("44")
                .locations("filesystem:../nq-infra/src/main/resources/db/migration").load();
        throughV44.migrate();
        assertEquals("44", throughV44.info().current().getVersion().getVersion());
        Flyway latest = Flyway.configure().dataSource(schemaUrl, user, password).schemas(schema)
                .defaultSchema(schema).createSchemas(true).cleanDisabled(false)
                .locations("filesystem:../nq-infra/src/main/resources/db/migration").load();
        try {
            latest.migrate();
            latest.validate();
            assertEquals("45", latest.info().current().getVersion().getVersion());
        } finally {
            latest.clean();
        }
    }

    @Test
    void allowsOneConcurrentZeroIntentReplacementAndCanonicalLegacyBridge() throws Exception {
        String url = System.getProperty("nq.postgres.smoke.url", "").trim();
        String user = System.getProperty("nq.postgres.smoke.user", "").trim();
        String password = System.getProperty("nq.postgres.smoke.password", "").trim();
        boolean required = Boolean.parseBoolean(System.getProperty("nq.postgres.smoke.required", "false"));
        if (!required) {
            assumeTrue(!url.isBlank() && !user.isBlank() && !password.isBlank(),
                    "PostgreSQL V45 integration is disabled");
        }
        String schema = "gatey45_" + UUID.randomUUID().toString().replace("-", "");
        String schemaUrl = url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema + ",public";
        Flyway flyway = Flyway.configure().dataSource(schemaUrl, user, password).schemas(schema)
                .defaultSchema(schema).createSchemas(true).cleanDisabled(false)
                .locations("filesystem:../nq-infra/src/main/resources/db/migration").load();
        flyway.migrate();
        flyway.validate();
        try {
            DriverManagerDataSource dataSource = new DriverManagerDataSource(schemaUrl, user, password);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Fixture fixture = seedOperator(jdbc);
            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            jdbc.update("""
                    UPDATE exchange_account_credentials
                    SET permission_probe_status='SUCCEEDED',permission_scope='TRADE',withdraw_enabled=FALSE,
                        ip_allowlist_probe_status='PASSED',last_permission_probe_at=?
                    WHERE credential_id=?
                    """, Timestamp.from(now), fixture.credentialId());

            var accounts = new JdbcExchangeAccountRepository(jdbc);
            var account = accounts.findById(fixture.accountId()).orElseThrow();
            CanonicalLegacyAccountBridgeService bridge = new CanonicalLegacyAccountBridgeService(jdbc);
            long legacyId = bridge.resolveOrCreate(account, "trace-v45-bridge", now);
            assertEquals(legacyId, bridge.resolveOrCreate(
                    accounts.findById(fixture.accountId()).orElseThrow(), "trace-v45-bridge", now));
            assertEquals("nq-okx-live-" + fixture.accountId(), jdbc.queryForObject(
                    "SELECT account_code FROM accounts WHERE account_id=?", String.class, legacyId));

            JdbcLiveControlRepository sessionRepository = new JdbcLiveControlRepository(jdbc);
            JdbcLiveControlAuthorization authorization = new JdbcLiveControlAuthorization(jdbc);
            JdbcOperatorPilotAuthorityRepository authorityRepository =
                    new JdbcOperatorPilotAuthorityRepository(jdbc);
            JdbcPilotPrePlaceRecoveryRepository recoveries =
                    new JdbcPilotPrePlaceRecoveryRepository(jdbc);
            OperatorPilotAuthorityService authorityService = new OperatorPilotAuthorityService(
                    authorityRepository, authorization);
            LiveSessionControlService sessionService = new LiveSessionControlService(
                    sessionRepository, authorization, recoveries, authorityRepository);
            TransactionTemplate transactions = new TransactionTemplate(
                    new DataSourceTransactionManager(dataSource));
            var actor = new AuthenticatedLiveControlActor(fixture.ownerId());

            OperatorPilotAuthority firstAuthority = OperatorPilotAuthority.active(
                    UUID.randomUUID(), fixture.ownerId(), fixture.accountId(), fixture.credentialId(),
                    "BTC-USDT", OperatorPilotAuthority.Side.BUY, OperatorPilotAuthority.OrderType.LIMIT,
                    new BigDecimal("10.00000000"), now, now.plusSeconds(5), fixture.ownerId(), now);
            transactions.executeWithoutResult(status -> authorityService.materialize(actor, firstAuthority));
            LiveSession firstSession = LiveSession.createOperatorPilot(
                    UUID.randomUUID(), fixture.ownerId(), fixture.accountId(), firstAuthority.id(),
                    firstAuthority.canonicalDigest(), fixture.credentialId(), "BTC-USDT",
                    firstAuthority.maxNotional(), now, now.plusSeconds(5), fixture.ownerId(), now);
            transactions.executeWithoutResult(status -> sessionService.createOperatorPilotSession(
                    actor, firstSession, firstAuthority, createdEvent(firstSession, fixture.ownerId(), "first")));
            for (LiveSessionCommand command : List.of(
                    LiveSessionCommand.APPROVE, LiveSessionCommand.START, LiveSessionCommand.ACTIVATE)) {
                sessionService.transitionMinimalPilot(
                        actor, firstSession.id(), command, "request-first", "trace-first", "idem-first");
            }
            UUID predecessor = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO pilot_execution_leases(
                        lease_id,live_session_id,operator_pilot_authority_id,binding_id,binding_digest,
                        status,max_notional,valid_from,expires_at,created_by,version,created_at,updated_at)
                    VALUES (?,?,?,?,?,'CREATED',?,?,?,?,1,?,?)
                    """, predecessor, firstSession.id(), firstAuthority.id(), UUID.randomUUID(), "d".repeat(64),
                    new BigDecimal("10.00000000"), Timestamp.from(now), Timestamp.from(firstAuthority.expiresAt()),
                    fixture.ownerId(), Timestamp.from(now), Timestamp.from(now));
            Instant activatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
            jdbc.update("""
                    UPDATE pilot_execution_leases SET status='ACTIVE',version=2,updated_at=? WHERE lease_id=?
                    """, Timestamp.from(activatedAt), predecessor);
            LiveControlException activeRejected = assertThrows(LiveControlException.class, () -> recoveries.decide(
                    fixture.ownerId(), fixture.accountId(), fixture.credentialId(), "BTC-USDT",
                    new BigDecimal("10.00000000"), UUID.randomUUID(), "request-active", "trace-active",
                    Instant.now().truncatedTo(ChronoUnit.MICROS)));
            assertEquals("REPLACEMENT_FORBIDDEN_STATE_AMBIGUOUS", activeRejected.code());
            Thread.sleep(5_100L);
            Instant expiredAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
            jdbc.update("""
                    UPDATE pilot_execution_leases
                    SET status='EXPIRED',closed_at=?,version=3,updated_at=? WHERE lease_id=?
                    """, Timestamp.from(expiredAt), Timestamp.from(expiredAt), predecessor);

            transactions.executeWithoutResult(status -> {
                String orderId = "order-negative-created";
                jdbc.update("""
                        INSERT INTO orders(order_id,account_id,symbol,client_order_id,side,type,price,qty,
                                           status,trace_id,venue,trade_env)
                        VALUES (?,?,?,'client-negative-created','BUY','LIMIT',100,0.01,
                                'SENT','trace-negative','OKX','LIVE')
                        """, orderId, legacyId, "BTC-USDT");
                jdbc.update("""
                        INSERT INTO execution_intents(
                            intent_id,session_id,sequence,action,symbol,side,order_type,quantity,limit_price,
                            payload_hash_schema_version,payload_hash,client_order_id,local_order_id,state,version)
                        VALUES (?,?,1,'PLACE','BTC-USDT','BUY','LIMIT',0.01,100,
                                'execution-intent-payload.v1',?,'client-negative-created',?,'CREATED',1)
                        """, UUID.randomUUID(), firstSession.id(), "a".repeat(64), orderId);
                LiveControlException rejected = assertThrows(LiveControlException.class, () -> recoveries.decide(
                        fixture.ownerId(), fixture.accountId(), fixture.credentialId(), "BTC-USDT",
                        new BigDecimal("10.00000000"), UUID.randomUUID(), "request-intent", "trace-intent",
                        Instant.now().truncatedTo(ChronoUnit.MICROS)));
                assertEquals("REPLACEMENT_FORBIDDEN_SIDE_EFFECT_STARTED", rejected.code());
                status.setRollbackOnly();
            });

            transactions.executeWithoutResult(status -> {
                String orderId = "order-negative-send";
                UUID intentId = UUID.randomUUID();
                Instant factTime = Instant.now().truncatedTo(ChronoUnit.MICROS);
                jdbc.update("""
                        INSERT INTO orders(order_id,account_id,symbol,client_order_id,side,type,price,qty,
                                           status,trace_id,venue,trade_env)
                        VALUES (?,?,?,'client-negative-send','BUY','LIMIT',100,0.01,
                                'SENT','trace-negative','OKX','LIVE')
                        """, orderId, legacyId, "BTC-USDT");
                jdbc.update("""
                        INSERT INTO execution_intents(
                            intent_id,session_id,sequence,action,symbol,side,order_type,quantity,limit_price,
                            payload_hash_schema_version,payload_hash,client_order_id,local_order_id,state,version)
                        VALUES (?,?,1,'PLACE','BTC-USDT','BUY','LIMIT',0.01,100,
                                'execution-intent-payload.v1',?,'client-negative-send',?,'CREATED',1)
                        """, intentId, firstSession.id(), "b".repeat(64), orderId);
                UUID claimToken = UUID.randomUUID();
                jdbc.update("""
                        UPDATE execution_intents
                        SET state='CLAIMED',version=2,claimed_by='fixture',claim_token=?,
                            claimed_at=?,lease_expires_at=? WHERE intent_id=?
                        """, claimToken, Timestamp.from(factTime),
                        Timestamp.from(factTime.plusSeconds(30)), intentId);
                jdbc.update("""
                        UPDATE execution_intents SET state='SEND_STARTED',version=3,send_started_at=?
                        WHERE intent_id=?
                        """, Timestamp.from(factTime), intentId);
                jdbc.update("""
                        INSERT INTO execution_receipts(
                            receipt_id,intent_id,attempt_no,outcome,received_at,payload_digest,
                            payload_digest_schema_version)
                        VALUES (?,?,1,'UNKNOWN',?,?,'execution-receipt-envelope.v1')
                        """, UUID.randomUUID(), intentId, Timestamp.from(factTime), "c".repeat(64));
                LiveControlException rejected = assertThrows(LiveControlException.class, () -> recoveries.decide(
                        fixture.ownerId(), fixture.accountId(), fixture.credentialId(), "BTC-USDT",
                        new BigDecimal("10.00000000"), UUID.randomUUID(), "request-send", "trace-send",
                        factTime));
                assertEquals("REPLACEMENT_FORBIDDEN_SIDE_EFFECT_STARTED", rejected.code());
                status.setRollbackOnly();
            });

            var recovery = recoveries.decide(
                    fixture.ownerId(), fixture.accountId(), fixture.credentialId(), "BTC-USDT",
                    new BigDecimal("10.00000000"), UUID.randomUUID(), "request-v45", "trace-v45",
                    Instant.now().truncatedTo(ChronoUnit.MICROS)).orElseThrow();
            sessionService.terminalizeMinimalPilotPrePlaceRecovery(
                    actor, firstSession.id(), recovery.decisionId(),
                    "request-v45", "trace-v45", "idem-v45");
            assertEquals("LIVE_RECONCILED", jdbc.queryForObject(
                    "SELECT state FROM live_sessions WHERE session_id=?", String.class, firstSession.id()));

            Instant orphanNow = Instant.now().truncatedTo(ChronoUnit.MICROS);
            OperatorPilotAuthority orphanAuthority = OperatorPilotAuthority.active(
                    UUID.randomUUID(), fixture.ownerId(), fixture.accountId(), fixture.credentialId(),
                    "BTC-USDT", OperatorPilotAuthority.Side.BUY, OperatorPilotAuthority.OrderType.LIMIT,
                    new BigDecimal("10.00000000"), orphanNow, orphanNow.plusSeconds(1),
                    fixture.ownerId(), orphanNow);
            transactions.executeWithoutResult(status -> authorityService.materialize(actor, orphanAuthority));
            LiveSession orphanSession = LiveSession.createOperatorPilot(
                    UUID.randomUUID(), fixture.ownerId(), fixture.accountId(), orphanAuthority.id(),
                    orphanAuthority.canonicalDigest(), fixture.credentialId(), "BTC-USDT",
                    orphanAuthority.maxNotional(), orphanNow, orphanNow.plusSeconds(1),
                    fixture.ownerId(), orphanNow);
            transactions.executeWithoutResult(status -> sessionService.createOperatorPilotSession(
                    actor, orphanSession, orphanAuthority,
                    createdEvent(orphanSession, fixture.ownerId(), "orphan")));
            Thread.sleep(1_100L);
            assertTrue(sessionService.terminalizeExpiredMinimalPilotPreparation(
                    actor, fixture.accountId(), fixture.credentialId(), "BTC-USDT",
                    new BigDecimal("10.00000000"), recovery.decisionId(),
                    "request-orphan", "trace-orphan", "idem-orphan").isPresent());
            assertEquals("REJECTED", jdbc.queryForObject(
                    "SELECT state FROM live_sessions WHERE session_id=?", String.class, orphanSession.id()));
            assertEquals("EXPIRED", jdbc.queryForObject(
                    "SELECT status FROM operator_pilot_authorities WHERE authority_id=?",
                    String.class, orphanAuthority.id()));

            Instant secondNow = Instant.now().truncatedTo(ChronoUnit.MICROS);
            OperatorPilotAuthority secondAuthority = operatorAuthority(fixture, secondNow, "10.00000000");
            transactions.executeWithoutResult(status -> authorityService.materialize(actor, secondAuthority));
            LiveSession secondSession = operatorSession(fixture, secondAuthority, secondNow);
            transactions.executeWithoutResult(status -> sessionService.createOperatorPilotSession(
                    actor, secondSession, secondAuthority, createdEvent(secondSession, fixture.ownerId(), "second")));

            AtomicInteger succeeded = new AtomicInteger();
            try (var executor = Executors.newFixedThreadPool(2)) {
                java.util.concurrent.Callable<Void> insert = () -> {
                    try {
                        new JdbcTemplate(new DriverManagerDataSource(schemaUrl, user, password)).update("""
                                INSERT INTO pilot_execution_leases(
                                    lease_id,live_session_id,operator_pilot_authority_id,binding_id,binding_digest,
                                    status,max_notional,valid_from,expires_at,created_by,version,created_at,updated_at,
                                    predecessor_lease_id,recovery_decision_id,replacement_ordinal,replacement_reason)
                                VALUES (?,?,?,?,?,'CREATED',?,?,?,?,1,?,?,?,?,1,'PRE_PLACE_ZERO_INTENT_FAILURE')
                                """, UUID.randomUUID(), secondSession.id(), secondAuthority.id(), UUID.randomUUID(),
                                "e".repeat(64), new BigDecimal("10.00000000"), Timestamp.from(secondNow),
                                Timestamp.from(secondNow.plusSeconds(120)), fixture.ownerId(),
                                Timestamp.from(secondNow), Timestamp.from(secondNow), predecessor,
                                recovery.decisionId());
                        succeeded.incrementAndGet();
                    } catch (org.springframework.dao.DataIntegrityViolationException expected) {
                        // 数据库唯一约束/trigger必须让并发loser失败。
                    }
                    return null;
                };
                Future<Void> first = executor.submit(insert);
                Future<Void> second = executor.submit(insert);
                first.get();
                second.get();
            }
            assertEquals(1, succeeded.get());
            assertEquals(1, jdbc.queryForObject(
                    "SELECT count(*) FROM pilot_execution_leases WHERE predecessor_lease_id=?",
                    Integer.class, predecessor));
            assertEquals("EXPIRED", jdbc.queryForObject(
                    "SELECT status FROM pilot_execution_leases WHERE lease_id=?", String.class, predecessor));
            UUID successor = jdbc.queryForObject(
                    "SELECT lease_id FROM pilot_execution_leases WHERE predecessor_lease_id=?",
                    UUID.class, predecessor);
            for (LiveSessionCommand command : List.of(
                    LiveSessionCommand.APPROVE, LiveSessionCommand.START, LiveSessionCommand.ACTIVATE)) {
                sessionService.transitionMinimalPilot(
                        actor, secondSession.id(), command, "request-second", "trace-second", "idem-second");
            }
            Instant successorActiveAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
            jdbc.update("""
                    UPDATE pilot_execution_leases SET status='ACTIVE',version=2,updated_at=? WHERE lease_id=?
                    """, Timestamp.from(successorActiveAt), successor);
            UUID firstIntent = insertCreatedPlaceIntent(
                    jdbc, secondSession.id(), legacyId, 1, "first", "f".repeat(64));
            jdbc.update("""
                    INSERT INTO pilot_execution_lease_intents(lease_id,intent_id,action,created_at)
                    VALUES (?,?,'PLACE',?)
                    """, successor, firstIntent, Timestamp.from(successorActiveAt));
            UUID secondIntent = insertCreatedPlaceIntent(
                    jdbc, secondSession.id(), legacyId, 2, "second", "1".repeat(64));
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                    INSERT INTO pilot_execution_lease_intents(lease_id,intent_id,action,created_at)
                    VALUES (?,?,'PLACE',?)
                    """, successor, secondIntent, Timestamp.from(successorActiveAt)));
            assertEquals(1, jdbc.queryForObject(
                    "SELECT count(*) FROM pilot_execution_lease_intents WHERE action='PLACE'",
                    Integer.class));
        } finally {
            flyway.clean();
        }
    }

    @Test
    void enforcesOperatorAuthorityIsolationDigestLeaseAndCloseout() {
        String url = System.getProperty("nq.postgres.smoke.url", "").trim();
        String user = System.getProperty("nq.postgres.smoke.user", "").trim();
        String password = System.getProperty("nq.postgres.smoke.password", "").trim();
        boolean required = Boolean.parseBoolean(System.getProperty("nq.postgres.smoke.required", "false"));
        if (!required) {
            assumeTrue(!url.isBlank() && !user.isBlank() && !password.isBlank(),
                    "PostgreSQL V44 integration is disabled");
        }
        assertTrue(!url.isBlank() && !user.isBlank() && !password.isBlank());

        String schema = "gatey44_" + UUID.randomUUID().toString().replace("-", "");
        String schemaUrl = url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema + ",public";
        Flyway flyway = Flyway.configure().dataSource(schemaUrl, user, password).schemas(schema)
                .defaultSchema(schema).createSchemas(true).cleanDisabled(false)
                .locations("filesystem:../nq-infra/src/main/resources/db/migration").load();
        flyway.migrate();
        flyway.validate();
        try {
            assertEquals("45", flyway.info().current().getVersion().getVersion());
            DriverManagerDataSource dataSource = new DriverManagerDataSource(schemaUrl, user, password);
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Fixture fixture = seedOperator(jdbc);
            jdbc.update("""
                    UPDATE exchange_account_credentials
                    SET permission_probe_status='SUCCEEDED',permission_scope='TRADE',withdraw_enabled=FALSE,
                        ip_allowlist_probe_status='PASSED',last_permission_probe_at=?
                    WHERE credential_id=?
                    """, Timestamp.from(Instant.now()), fixture.credentialId());

            JdbcLiveControlRepository sessions = new JdbcLiveControlRepository(jdbc);
            JdbcOperatorPilotAuthorityRepository authorities = new JdbcOperatorPilotAuthorityRepository(jdbc);
            JdbcLiveControlAuthorization authorization = new JdbcLiveControlAuthorization(jdbc);
            OperatorPilotAuthorityService authorityService = new OperatorPilotAuthorityService(
                    authorities, authorization);
            LiveSessionControlService sessionService = new LiveSessionControlService(sessions, authorization);
            TransactionTemplate transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            OperatorPilotAuthority authority = OperatorPilotAuthority.active(
                    UUID.randomUUID(), fixture.ownerId(), fixture.accountId(), fixture.credentialId(),
                    "BTC-USDT", OperatorPilotAuthority.Side.BUY, OperatorPilotAuthority.OrderType.LIMIT,
                    new BigDecimal("10.00000000"), now, now.plusSeconds(120), fixture.ownerId(), now);
            OperatorPilotAuthority stored = transactions.execute(status -> authorityService.materialize(
                    new AuthenticatedLiveControlActor(fixture.ownerId()), authority));
            assertEquals(authority.canonicalDigest(), jdbc.queryForObject("""
                    SELECT gate_y44_operator_pilot_authority_digest(
                        authority_id,owner_user_id,exchange_account_id,credential_reference_id,
                        instrument,side,order_type,max_notional,max_place_count,max_cancel_count,
                        transfer_allowed,withdraw_allowed,valid_from,expires_at,created_by,created_at)
                    FROM operator_pilot_authorities WHERE authority_id=?
                    """, String.class, authority.id()));

            LiveSession session = LiveSession.createOperatorPilot(
                    UUID.randomUUID(), fixture.ownerId(), fixture.accountId(), authority.id(),
                    authority.canonicalDigest(), fixture.credentialId(), "BTC-USDT",
                    new BigDecimal("10.00000000"), now, now.plusSeconds(120), fixture.ownerId(), now);
            LiveSessionEvent event = new LiveSessionEvent(
                    UUID.randomUUID(), session.id(), 1, null, LiveSessionState.APPROVAL_PENDING,
                    "CREATE", fixture.ownerId(), "request-v44", "trace-v44", "SESSION_CREATED",
                    "idempotency-v44", session.approvalScopeHash(), "{}", now);
            transactions.executeWithoutResult(status -> sessionService.createOperatorPilotSession(
                    new AuthenticatedLiveControlActor(fixture.ownerId()), session, stored, event));
            LiveSession reloaded = sessions.findSession(session.id()).orElseThrow();
            assertEquals(LiveSessionAuthorityType.OPERATOR_PILOT, reloaded.authorityType());
            assertNull(reloaded.strategyReleaseId());
            assertNull(reloaded.riskLimitSetId());
            assertEquals(authority.id(), reloaded.operatorPilotAuthorityId());

            assertRejectedSessionVariant(jdbc, reloaded, "STRATEGY", null, null,
                    null, null, null, null, null, LiveSession.APPROVAL_SCOPE_SCHEMA);
            assertRejectedSessionVariant(jdbc, reloaded, "STRATEGY", authority.id(), authority.canonicalDigest(),
                    "synthetic-release", "b".repeat(64), 1L, UUID.randomUUID(), "c".repeat(64),
                    LiveSession.APPROVAL_SCOPE_SCHEMA);
            assertRejectedSessionVariant(jdbc, reloaded, "OPERATOR_PILOT", null, null,
                    null, null, null, null, null, LiveSession.OPERATOR_PILOT_APPROVAL_SCOPE_SCHEMA);
            assertRejectedSessionVariant(jdbc, reloaded, "OPERATOR_PILOT", authority.id(),
                    authority.canonicalDigest(), "synthetic-release", "b".repeat(64), 1L,
                    UUID.randomUUID(), "c".repeat(64), LiveSession.OPERATOR_PILOT_APPROVAL_SCOPE_SCHEMA);

            UUID leaseId = UUID.randomUUID();
            jdbc.update("""
                            INSERT INTO pilot_execution_leases(
                                lease_id,live_session_id,operator_pilot_authority_id,binding_id,binding_digest,
                                status,max_notional,valid_from,expires_at,created_by,version,created_at,updated_at)
                            VALUES (?,?,?,?,?,'CREATED',?,?,?,?,1,?,?)
                            """, leaseId, session.id(), authority.id(), UUID.randomUUID(), "d".repeat(64),
                    new BigDecimal("10.00000000"), Timestamp.from(now), Timestamp.from(now.plusSeconds(120)),
                    fixture.ownerId(), Timestamp.from(now), Timestamp.from(now));
            Instant closedAt = now.plusSeconds(1);
            jdbc.update("""
                    UPDATE pilot_execution_leases
                    SET status='FAILED',closed_at=?,version=2,updated_at=? WHERE lease_id=?
                    """, Timestamp.from(closedAt), Timestamp.from(closedAt), leaseId);
            assertEquals("CLOSED", jdbc.queryForObject(
                    "SELECT status FROM operator_pilot_authorities WHERE authority_id=?",
                    String.class, authority.id()));
        } finally {
            flyway.clean();
        }
    }

    private static void assertRejectedSessionVariant(
            JdbcTemplate jdbc,
            LiveSession base,
            String authorityType,
            UUID operatorAuthorityId,
            String operatorAuthorityDigest,
            String strategyReleaseId,
            String releaseDigest,
            Long releaseRevision,
            UUID riskId,
            String riskDigest,
            String scopeSchema
    ) {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                        INSERT INTO live_sessions(
                            session_id,owner_id,exchange_account_id,venue,authority_type,
                            operator_pilot_authority_id,operator_pilot_authority_digest,strategy_release_id,
                            release_digest,release_admission_revision,risk_limit_set_id,risk_limit_set_digest,
                            credential_reference,symbol_allowlist,capital_cap,execution_window_start,
                            execution_window_end,state,version,approval_scope_hash,approval_scope_schema_version,
                            next_event_sequence,created_by,created_at,updated_at)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'APPROVAL_PENDING',1,?,?,1,?,?,?)
                        """, UUID.randomUUID(), base.ownerId(), base.exchangeAccountId(), base.venue(), authorityType,
                operatorAuthorityId, operatorAuthorityDigest, strategyReleaseId, releaseDigest,
                releaseRevision, riskId, riskDigest, base.credentialReference(),
                base.symbolAllowlist().toArray(String[]::new), base.capitalCap(),
                Timestamp.from(base.executionWindowStart()), Timestamp.from(base.executionWindowEnd()),
                "a".repeat(64), scopeSchema, base.createdBy(),
                Timestamp.from(base.createdAt()), Timestamp.from(base.updatedAt())));
    }

    private static Fixture seedOperator(JdbcTemplate jdbc) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        long owner = jdbc.queryForObject(
                "INSERT INTO users(username,password_hash) VALUES (?, 'fixture') RETURNING id",
                Long.class, "gatey44_" + suffix);
        long role = jdbc.queryForObject("SELECT id FROM roles WHERE role_code='OPERATOR'", Long.class);
        jdbc.update("INSERT INTO user_roles(user_id,role_id) VALUES (?,?)", owner, role);
        long account = jdbc.queryForObject("""
                INSERT INTO exchange_accounts(owner_user_id,exchange_code,trade_env,account_alias,status)
                VALUES (?,'OKX','LIVE',?,'ACTIVE') RETURNING exchange_account_id
                """, Long.class, owner, "gatey44-" + suffix);
        long credential = jdbc.queryForObject("""
                INSERT INTO exchange_account_credentials(
                    exchange_account_id,credential_type,encrypted_payload,key_version,cipher_suite,
                    verification_status,is_active,credential_status)
                VALUES (?,'OKX_API_V5',?,1,'PGP_SYM_AES256','VERIFIED',TRUE,'ACTIVE')
                RETURNING credential_id
                """, Long.class, account, new byte[]{1, 2, 3});
        return new Fixture(owner, account, credential);
    }

    private static OperatorPilotAuthority operatorAuthority(
            Fixture fixture,
            Instant now,
            String maxNotional
    ) {
        return OperatorPilotAuthority.active(
                UUID.randomUUID(), fixture.ownerId(), fixture.accountId(), fixture.credentialId(),
                "BTC-USDT", OperatorPilotAuthority.Side.BUY, OperatorPilotAuthority.OrderType.LIMIT,
                new BigDecimal(maxNotional), now, now.plusSeconds(120), fixture.ownerId(), now);
    }

    private static LiveSession operatorSession(
            Fixture fixture,
            OperatorPilotAuthority authority,
            Instant now
    ) {
        return LiveSession.createOperatorPilot(
                UUID.randomUUID(), fixture.ownerId(), fixture.accountId(), authority.id(),
                authority.canonicalDigest(), fixture.credentialId(), "BTC-USDT",
                authority.maxNotional(), now, now.plusSeconds(120), fixture.ownerId(), now);
    }

    private static LiveSessionEvent createdEvent(LiveSession session, long ownerId, String suffix) {
        return new LiveSessionEvent(
                UUID.randomUUID(), session.id(), 1, null, LiveSessionState.APPROVAL_PENDING,
                "CREATE", ownerId, "request-" + suffix, "trace-" + suffix, "SESSION_CREATED",
                "idempotency-" + suffix, session.approvalScopeHash(), "{}", session.createdAt());
    }

    private static UUID insertCreatedPlaceIntent(
            JdbcTemplate jdbc,
            UUID sessionId,
            long legacyAccountId,
            int sequence,
            String suffix,
            String payloadHash
    ) {
        String orderId = "order-place-" + suffix;
        String clientOrderId = "client-place-" + suffix;
        jdbc.update("""
                INSERT INTO orders(order_id,account_id,symbol,client_order_id,side,type,price,qty,
                                   status,trace_id,venue,trade_env)
                VALUES (?,?,'BTC-USDT',?,'BUY','LIMIT',100,0.01,
                        'SENT','trace-place','OKX','LIVE')
                """, orderId, legacyAccountId, clientOrderId);
        UUID intentId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO execution_intents(
                    intent_id,session_id,sequence,action,symbol,side,order_type,quantity,limit_price,
                    payload_hash_schema_version,payload_hash,client_order_id,local_order_id,state,version)
                VALUES (?,?,?,'PLACE','BTC-USDT','BUY','LIMIT',0.01,100,
                        'execution-intent-payload.v1',?,?,?,'CREATED',1)
                """, intentId, sessionId, sequence, payloadHash, clientOrderId, orderId);
        return intentId;
    }

    private record Fixture(long ownerId, long accountId, long credentialId) {
    }
}
