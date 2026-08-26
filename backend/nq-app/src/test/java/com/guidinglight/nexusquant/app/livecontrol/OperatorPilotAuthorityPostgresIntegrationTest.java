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
 * V44-V46 disposable PostgreSQL：operator authority、conditional session 与 lease lifecycle。
 */
class OperatorPilotAuthorityPostgresIntegrationTest {

    @Test
    void migratesExactV45ToV46AndValidates() {
        String url = System.getProperty("nq.postgres.smoke.url", "").trim();
        String user = System.getProperty("nq.postgres.smoke.user", "").trim();
        String password = System.getProperty("nq.postgres.smoke.password", "").trim();
        boolean required = Boolean.parseBoolean(System.getProperty("nq.postgres.smoke.required", "false"));
        if (!required) {
            assumeTrue(!url.isBlank() && !user.isBlank() && !password.isBlank(),
                    "PostgreSQL V45 to V46 integration is disabled");
        }
        String schema = "gatey46upgrade_" + UUID.randomUUID().toString().replace("-", "");
        String schemaUrl = url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema + ",public";
        Flyway throughV45 = Flyway.configure().dataSource(schemaUrl, user, password).schemas(schema)
                .defaultSchema(schema).createSchemas(true).cleanDisabled(false).target("45")
                .locations("filesystem:../nq-infra/src/main/resources/db/migration").load();
        throughV45.migrate();
        assertEquals("45", throughV45.info().current().getVersion().getVersion());
        Flyway latest = Flyway.configure().dataSource(schemaUrl, user, password).schemas(schema)
                .defaultSchema(schema).createSchemas(true).cleanDisabled(false)
                .locations("filesystem:../nq-infra/src/main/resources/db/migration").load();
        try {
            latest.migrate();
            latest.validate();
            assertEquals("46", latest.info().current().getVersion().getVersion());
        } finally {
            latest.clean();
        }
    }

    @Test
    void regeneratesTerminalZeroExecutionLineageAndKeepsAttemptPlaceExactlyOnce() throws Exception {
        String url = System.getProperty("nq.postgres.smoke.url", "").trim();
        String user = System.getProperty("nq.postgres.smoke.user", "").trim();
        String password = System.getProperty("nq.postgres.smoke.password", "").trim();
        boolean required = Boolean.parseBoolean(System.getProperty("nq.postgres.smoke.required", "false"));
        if (!required) {
            assumeTrue(!url.isBlank() && !user.isBlank() && !password.isBlank(),
                    "PostgreSQL V46 regeneration integration is disabled");
        }
        String schema = "gatey46lineage_" + UUID.randomUUID().toString().replace("-", "");
        String schemaUrl = url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema + ",public";
        Flyway throughV45 = Flyway.configure().dataSource(schemaUrl, user, password).schemas(schema)
                .defaultSchema(schema).createSchemas(true).cleanDisabled(false).target("45")
                .locations("filesystem:../nq-infra/src/main/resources/db/migration").load();
        throughV45.migrate();
        Flyway latest = Flyway.configure().dataSource(schemaUrl, user, password).schemas(schema)
                .defaultSchema(schema).createSchemas(true).cleanDisabled(false)
                .locations("filesystem:../nq-infra/src/main/resources/db/migration").load();
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
            JdbcLiveControlRepository sessionRepository = new JdbcLiveControlRepository(jdbc);
            JdbcLiveControlAuthorization authorization = new JdbcLiveControlAuthorization(jdbc);
            JdbcOperatorPilotAuthorityRepository authorityRepository =
                    new JdbcOperatorPilotAuthorityRepository(jdbc);
            JdbcPilotPrePlaceRecoveryRepository recoveries = new JdbcPilotPrePlaceRecoveryRepository(jdbc);
            OperatorPilotAuthorityService authorityService = new OperatorPilotAuthorityService(
                    authorityRepository, authorization);
            LiveSessionControlService sessionService = new LiveSessionControlService(
                    sessionRepository, authorization, recoveries, authorityRepository);
            TransactionTemplate transactions = new TransactionTemplate(
                    new DataSourceTransactionManager(dataSource));
            var actor = new AuthenticatedLiveControlActor(fixture.ownerId());

            OperatorPilotAuthority authority0 = OperatorPilotAuthority.active(
                    UUID.randomUUID(), fixture.ownerId(), fixture.accountId(), fixture.credentialId(),
                    "BTC-USDT", OperatorPilotAuthority.Side.BUY, OperatorPilotAuthority.OrderType.LIMIT,
                    new BigDecimal("10.00000000"), now, now.plusSeconds(3), fixture.ownerId(), now);
            transactions.executeWithoutResult(status -> authorityService.materialize(actor, authority0));
            LiveSession session0 = LiveSession.createOperatorPilot(
                    UUID.randomUUID(), fixture.ownerId(), fixture.accountId(), authority0.id(),
                    authority0.canonicalDigest(), fixture.credentialId(), "BTC-USDT",
                    authority0.maxNotional(), now, now.plusSeconds(3), fixture.ownerId(), now);
            transactions.executeWithoutResult(status -> sessionService.createOperatorPilotSession(
                    actor, session0, authority0, createdEvent(session0, fixture.ownerId(), "origin-v46")));
            for (LiveSessionCommand command : List.of(
                    LiveSessionCommand.APPROVE, LiveSessionCommand.START, LiveSessionCommand.ACTIVATE)) {
                sessionService.transitionMinimalPilot(
                        actor, session0.id(), command, "request-origin", "trace-origin", "idem-origin");
            }
            UUID lease0 = UUID.randomUUID();
            insertLease(jdbc, lease0, session0.id(), authority0.id(), fixture.ownerId(),
                    now, now.plusSeconds(1), null, null, 0, null);
            jdbc.update("UPDATE pilot_execution_leases SET status='ACTIVE',version=2,updated_at=? WHERE lease_id=?",
                    Timestamp.from(now), lease0);
            Thread.sleep(3_100L);
            Instant terminal0 = Instant.now().truncatedTo(ChronoUnit.MICROS);
            jdbc.update("""
                    UPDATE pilot_execution_leases
                    SET status='EXPIRED',closed_at=?,version=3,updated_at=? WHERE lease_id=?
                    """, Timestamp.from(terminal0), Timestamp.from(terminal0), lease0);
            UUID decision0 = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO pilot_pre_place_recovery_decisions(
                        decision_id,predecessor_lease_id,predecessor_session_id,decision,
                        place_intent_count,send_started_count,execution_intent_count,
                        execution_receipt_count,order_count,trade_count,ledger_count,
                        decided_by,request_id,trace_id,decided_at)
                    VALUES (?,?,?,'REPLACEMENT_ALLOWED_ZERO_INTENT',0,0,0,0,0,0,0,?,?,?,?)
                    """, decision0, lease0, session0.id(), fixture.ownerId(),
                    "request-decision0", "trace-decision0", Timestamp.from(terminal0));
            sessionService.terminalizeMinimalPilotPrePlaceRecovery(
                    actor, session0.id(), decision0,
                    "request-terminal0", "trace-terminal0", "idem-terminal0");

            Instant now1 = Instant.now().truncatedTo(ChronoUnit.MICROS);
            OperatorPilotAuthority authority1 = operatorAuthority(fixture, now1, "10.00000000");
            transactions.executeWithoutResult(status -> authorityService.materialize(actor, authority1));
            LiveSession session1 = operatorSession(fixture, authority1, now1);
            transactions.executeWithoutResult(status -> sessionService.createOperatorPilotSession(
                    actor, session1, authority1, createdEvent(session1, fixture.ownerId(), "ordinal1")));
            UUID lease1 = UUID.randomUUID();
            insertLease(jdbc, lease1, session1.id(), authority1.id(), fixture.ownerId(),
                    now1, now1.plusSeconds(120), lease0, decision0, 1,
                    "PRE_PLACE_ZERO_INTENT_FAILURE");
            for (LiveSessionCommand command : List.of(
                    LiveSessionCommand.APPROVE, LiveSessionCommand.START, LiveSessionCommand.ACTIVATE,
                    LiveSessionCommand.STOP, LiveSessionCommand.BEGIN_RECONCILE,
                    LiveSessionCommand.RECONCILE_BLOCK)) {
                sessionService.transitionMinimalPilot(
                        actor, session1.id(), command, "request-ordinal1", "trace-ordinal1", "idem-ordinal1");
            }
            jdbc.update("UPDATE pilot_execution_leases SET status='ACTIVE',version=2,updated_at=? WHERE lease_id=?",
                    Timestamp.from(now1), lease1);
            Instant terminal1 = Instant.now().truncatedTo(ChronoUnit.MICROS);
            jdbc.update("""
                    UPDATE pilot_execution_leases
                    SET status='FAILED',closed_at=?,version=3,updated_at=? WHERE lease_id=?
                    """, Timestamp.from(terminal1), Timestamp.from(terminal1), lease1);

            latest.migrate();
            latest.validate();
            assertEquals("46", latest.info().current().getVersion().getVersion());
            var decision1 = recoveries.decide(
                    fixture.ownerId(), fixture.accountId(), fixture.credentialId(), "BTC-USDT",
                    new BigDecimal("10.00000000"), UUID.randomUUID(),
                    "request-decision1", "trace-decision1", terminal1).orElseThrow();
            assertEquals(2, decision1.replacementOrdinal());
            assertEquals(LiveSessionState.LIVE_RECONCILED,
                    sessionService.terminalizeMinimalPilotPrePlaceRecovery(
                            actor, session1.id(), decision1.decisionId(),
                            "request-terminal1", "trace-terminal1", "idem-terminal1").state());

            Instant now2 = Instant.now().truncatedTo(ChronoUnit.MICROS);
            OperatorPilotAuthority authority2 = operatorAuthority(fixture, now2, "10.00000000");
            transactions.executeWithoutResult(status -> authorityService.materialize(actor, authority2));
            LiveSession session2 = operatorSession(fixture, authority2, now2);
            transactions.executeWithoutResult(status -> sessionService.createOperatorPilotSession(
                    actor, session2, authority2, createdEvent(session2, fixture.ownerId(), "ordinal2")));
            AtomicInteger regenerationWinners = new AtomicInteger();
            try (var executor = Executors.newFixedThreadPool(2)) {
                java.util.concurrent.Callable<Void> insert = () -> {
                    try {
                        insertLease(new JdbcTemplate(new DriverManagerDataSource(schemaUrl, user, password)),
                                UUID.randomUUID(), session2.id(), authority2.id(), fixture.ownerId(),
                                now2, now2.plusSeconds(120), lease1, decision1.decisionId(),
                                decision1.replacementOrdinal(), "PRE_PLACE_TERMINAL_REGENERATION");
                        regenerationWinners.incrementAndGet();
                    } catch (DataIntegrityViolationException expected) {
                        // per-predecessor与decision unique只允许一个winner。
                    }
                    return null;
                };
                Future<Void> first = executor.submit(insert);
                Future<Void> second = executor.submit(insert);
                first.get();
                second.get();
            }
            assertEquals(1, regenerationWinners.get());
            UUID lease2 = jdbc.queryForObject(
                    "SELECT lease_id FROM pilot_execution_leases WHERE predecessor_lease_id=?",
                    UUID.class, lease1);
            assertEquals(2, jdbc.queryForObject(
                    "SELECT replacement_ordinal FROM pilot_execution_leases WHERE lease_id=?",
                    Integer.class, lease2));

            for (LiveSessionCommand command : List.of(
                    LiveSessionCommand.APPROVE, LiveSessionCommand.START)) {
                sessionService.transitionMinimalPilot(
                        actor, session2.id(), command, "request-ordinal2", "trace-ordinal2", "idem-ordinal2");
            }
            jdbc.update("UPDATE pilot_execution_leases SET status='ACTIVE',version=2,updated_at=? WHERE lease_id=?",
                    Timestamp.from(now2), lease2);
            for (LiveSessionCommand command : List.of(
                    LiveSessionCommand.ACTIVATE, LiveSessionCommand.STOP,
                    LiveSessionCommand.BEGIN_RECONCILE, LiveSessionCommand.RECONCILE_BLOCK)) {
                sessionService.transitionMinimalPilot(
                        actor, session2.id(), command, "request-ordinal2", "trace-ordinal2", "idem-ordinal2");
            }
            LiveControlException activeRejected = assertThrows(LiveControlException.class, () -> recoveries.decide(
                    fixture.ownerId(), fixture.accountId(), fixture.credentialId(), "BTC-USDT",
                    new BigDecimal("10.00000000"), UUID.randomUUID(),
                    "request-active-v46", "trace-active-v46", Instant.now()));
            assertEquals("REPLACEMENT_FORBIDDEN_STATE_AMBIGUOUS", activeRejected.code());
            transactions.executeWithoutResult(status -> {
                Instant consumedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
                jdbc.update("""
                        UPDATE pilot_execution_leases
                        SET status='CONSUMED',consumed_at=?,version=3,updated_at=? WHERE lease_id=?
                        """, Timestamp.from(consumedAt), Timestamp.from(consumedAt), lease2);
                LiveControlException consumedRejected = assertThrows(LiveControlException.class,
                        () -> recoveries.decide(
                                fixture.ownerId(), fixture.accountId(), fixture.credentialId(), "BTC-USDT",
                                new BigDecimal("10.00000000"), UUID.randomUUID(),
                                "request-consumed-v46", "trace-consumed-v46", consumedAt));
                assertEquals("REPLACEMENT_FORBIDDEN_STATE_AMBIGUOUS", consumedRejected.code());
                status.setRollbackOnly();
            });
            Instant terminal2 = Instant.now().truncatedTo(ChronoUnit.MICROS);
            jdbc.update("""
                    UPDATE pilot_execution_leases
                    SET status='FAILED',closed_at=?,version=3,updated_at=? WHERE lease_id=?
                    """, Timestamp.from(terminal2), Timestamp.from(terminal2), lease2);
            transactions.executeWithoutResult(status -> {
                UUID intent = insertCreatedPlaceIntent(
                        jdbc, session2.id(), ensureLegacyAccount(jdbc, fixture.accountId()),
                        1, "v46-negative", "9".repeat(64));
                LiveControlException rejected = assertThrows(LiveControlException.class, () -> recoveries.decide(
                        fixture.ownerId(), fixture.accountId(), fixture.credentialId(), "BTC-USDT",
                        new BigDecimal("10.00000000"), UUID.randomUUID(),
                        "request-intent-v46", "trace-intent-v46", Instant.now()));
                assertEquals("REPLACEMENT_FORBIDDEN_SIDE_EFFECT_STARTED", rejected.code());
                assertTrue(intent != null);
                status.setRollbackOnly();
            });
            var decision2 = recoveries.decide(
                    fixture.ownerId(), fixture.accountId(), fixture.credentialId(), "BTC-USDT",
                    new BigDecimal("10.00000000"), UUID.randomUUID(),
                    "request-decision2", "trace-decision2", terminal2).orElseThrow();
            assertEquals(3, decision2.replacementOrdinal());
            sessionService.terminalizeMinimalPilotPrePlaceRecovery(
                    actor, session2.id(), decision2.decisionId(),
                    "request-terminal2", "trace-terminal2", "idem-terminal2");

            Instant now3 = Instant.now().truncatedTo(ChronoUnit.MICROS);
            OperatorPilotAuthority authority3 = operatorAuthority(fixture, now3, "10.00000000");
            transactions.executeWithoutResult(status -> authorityService.materialize(actor, authority3));
            LiveSession session3 = operatorSession(fixture, authority3, now3);
            transactions.executeWithoutResult(status -> sessionService.createOperatorPilotSession(
                    actor, session3, authority3, createdEvent(session3, fixture.ownerId(), "ordinal3")));
            UUID lease3 = UUID.randomUUID();
            insertLease(jdbc, lease3, session3.id(), authority3.id(), fixture.ownerId(),
                    now3, now3.plusSeconds(120), lease2, decision2.decisionId(),
                    decision2.replacementOrdinal(), "PRE_PLACE_TERMINAL_REGENERATION");
            assertEquals(List.of(0, 1, 2, 3), jdbc.queryForList(
                    "SELECT replacement_ordinal FROM pilot_execution_leases ORDER BY replacement_ordinal",
                    Integer.class));
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                    "UPDATE pilot_execution_leases SET replacement_ordinal=99 WHERE lease_id=?", lease0));
            assertThrows(DataIntegrityViolationException.class, () -> insertLease(
                    jdbc, UUID.randomUUID(), session3.id(), authority3.id(), fixture.ownerId(),
                    now3, now3.plusSeconds(120), lease2, decision2.decisionId(), 3,
                    "PRE_PLACE_TERMINAL_REGENERATION"));

            for (LiveSessionCommand command : List.of(
                    LiveSessionCommand.APPROVE, LiveSessionCommand.START)) {
                sessionService.transitionMinimalPilot(
                        actor, session3.id(), command, "request-ordinal3", "trace-ordinal3", "idem-ordinal3");
            }
            jdbc.update("UPDATE pilot_execution_leases SET status='ACTIVE',version=2,updated_at=? WHERE lease_id=?",
                    Timestamp.from(now3), lease3);
            sessionService.transitionMinimalPilot(
                    actor, session3.id(), LiveSessionCommand.ACTIVATE,
                    "request-ordinal3", "trace-ordinal3", "idem-ordinal3");
            long legacyId = ensureLegacyAccount(jdbc, fixture.accountId());
            UUID intentA = insertCreatedPlaceIntent(jdbc, session3.id(), legacyId, 1, "v46-a", "a".repeat(64));
            UUID intentB = insertCreatedPlaceIntent(jdbc, session3.id(), legacyId, 2, "v46-b", "b".repeat(64));
            AtomicInteger placeWinners = new AtomicInteger();
            try (var executor = Executors.newFixedThreadPool(2)) {
                java.util.ArrayList<Future<?>> futures = new java.util.ArrayList<>();
                for (UUID intent : List.of(intentA, intentB)) {
                    futures.add(executor.submit(() -> {
                        try {
                            new JdbcTemplate(new DriverManagerDataSource(schemaUrl, user, password)).update("""
                                    INSERT INTO pilot_execution_lease_intents(lease_id,intent_id,action,created_at)
                                    VALUES (?,?,'PLACE',?)
                                    """, lease3, intent, Timestamp.from(now3));
                            placeWinners.incrementAndGet();
                        } catch (DataIntegrityViolationException expected) {
                            // global PLACE unique只允许一个winner。
                        }
                    }));
                }
                for (Future<?> future : futures) {
                    future.get();
                }
            }
            assertEquals(1, placeWinners.get());
            assertEquals(1, jdbc.queryForObject(
                    "SELECT count(*) FROM pilot_execution_lease_intents WHERE action='PLACE'",
                    Integer.class));
            Instant postPlaceFailureAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
            jdbc.update("""
                    UPDATE pilot_execution_leases
                    SET status='FAILED',closed_at=?,version=3,updated_at=? WHERE lease_id=?
                    """, Timestamp.from(postPlaceFailureAt), Timestamp.from(postPlaceFailureAt), lease3);
            LiveControlException postPlaceRejected = assertThrows(LiveControlException.class,
                    () -> recoveries.decide(
                            fixture.ownerId(), fixture.accountId(), fixture.credentialId(), "BTC-USDT",
                            new BigDecimal("10.00000000"), UUID.randomUUID(),
                            "request-post-place-v46", "trace-post-place-v46", postPlaceFailureAt));
            assertEquals("REPLACEMENT_FORBIDDEN_SIDE_EFFECT_STARTED", postPlaceRejected.code());
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
            sessionRepository.appendSessionEvent(new LiveSessionEvent(
                    UUID.randomUUID(), orphanSession.id(), 1,
                    LiveSessionState.APPROVAL_PENDING, LiveSessionState.APPROVAL_PENDING,
                    "CREATE_EXACT_PILOT_BINDING", fixture.ownerId(),
                    "request-orphan-binding", "trace-orphan-binding", "EXACT_PILOT_BINDING_VERIFIED",
                    "idem-orphan-binding", "a".repeat(64),
                    "{\"bindingId\":\"fixture-unconsumed\"}", orphanNow));
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
                                VALUES (?,?,?,?,?,'CREATED',?,?,?,?,1,?,?,?,?,1,'PRE_PLACE_TERMINAL_REGENERATION')
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
            assertEquals("46", flyway.info().current().getVersion().getVersion());
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

    private static void insertLease(
            JdbcTemplate jdbc,
            UUID leaseId,
            UUID sessionId,
            UUID authorityId,
            long createdBy,
            Instant validFrom,
            Instant expiresAt,
            UUID predecessorLeaseId,
            UUID recoveryDecisionId,
            int replacementOrdinal,
            String replacementReason
    ) {
        jdbc.update("""
                INSERT INTO pilot_execution_leases(
                    lease_id,live_session_id,operator_pilot_authority_id,binding_id,binding_digest,
                    status,max_notional,valid_from,expires_at,created_by,version,created_at,updated_at,
                    predecessor_lease_id,recovery_decision_id,replacement_ordinal,replacement_reason)
                VALUES (?,?,?,?,?,'CREATED',10.00000000,?,?,?,1,?,?,?,?,?,?)
                """, leaseId, sessionId, authorityId, UUID.randomUUID(), "d".repeat(64),
                Timestamp.from(validFrom), Timestamp.from(expiresAt), createdBy,
                Timestamp.from(validFrom), Timestamp.from(validFrom), predecessorLeaseId,
                recoveryDecisionId, replacementOrdinal, replacementReason);
    }

    private static long ensureLegacyAccount(JdbcTemplate jdbc, long exchangeAccountId) {
        var accounts = new JdbcExchangeAccountRepository(jdbc);
        return new CanonicalLegacyAccountBridgeService(jdbc).resolveOrCreate(
                accounts.findById(exchangeAccountId).orElseThrow(),
                "trace-v46-legacy-bridge", Instant.now().truncatedTo(ChronoUnit.MICROS));
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
