package com.guidinglight.nexusquant.app.livecontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.guidinglight.nexusquant.livecontrol.application.AuthenticatedLiveControlActor;
import com.guidinglight.nexusquant.livecontrol.application.LiveSessionControlService;
import com.guidinglight.nexusquant.livecontrol.application.OperatorApprovalCommand;
import com.guidinglight.nexusquant.livecontrol.application.PilotScopeAuthorityResolver;
import com.guidinglight.nexusquant.livecontrol.application.PilotScopeMaterializationCommand;
import com.guidinglight.nexusquant.livecontrol.domain.LiveControlException;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSession;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSessionEvent;
import com.guidinglight.nexusquant.livecontrol.domain.LiveSessionState;
import com.guidinglight.nexusquant.livecontrol.domain.OperatorApproval;
import com.guidinglight.nexusquant.livecontrol.domain.PilotObservationCanonicalEncoder;
import com.guidinglight.nexusquant.livecontrol.domain.PilotObservationSet;
import com.guidinglight.nexusquant.livecontrol.domain.PilotPrerequisiteObservation;
import com.guidinglight.nexusquant.livecontrol.domain.PilotScopeBinding;
import com.guidinglight.nexusquant.livecontrol.domain.RiskLimitSet;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntent;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntentCanonicalEncoder;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntentDraft;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionIntentState;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionReceiptCanonicalEncoder;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionReceiptDraft;
import com.guidinglight.nexusquant.livecontrol.execution.domain.ExecutionReceiptOutcome;
import com.guidinglight.nexusquant.livecontrol.execution.infra.jdbc.JdbcExecutionIntentRepository;
import com.guidinglight.nexusquant.livecontrol.infra.jdbc.JdbcLiveControlAuthorization;
import com.guidinglight.nexusquant.livecontrol.infra.jdbc.JdbcLiveControlRepository;
import com.guidinglight.nexusquant.livecontrol.infra.jdbc.JdbcPilotScopeRepository;
import com.guidinglight.nexusquant.livecontrol.infra.PilotScopeControlPlaneService;
import com.guidinglight.nexusquant.livecontrol.infra.PilotScopeFactTransactionService;
import com.guidinglight.nexusquant.livecontrol.infra.UnavailablePilotPrerequisiteObservationAuthority;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * GateY disposable PostgreSQL integration：回放 V1→latest，验证真实 FK/trigger/JDBC/并发。
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
            assertEquals("42", latest.info().current().getVersion().getVersion());
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
            assertExecutionIntentRuntime(jdbc, transactions, service, existing, risk);
        } finally {
            latest.clean();
        }
    }

    @Test
    void shouldUpgradeV39WithoutFakeBackfillAndEnforcePilotFacts() throws Exception {
        SmokeConfig config = SmokeConfig.fromSystemProperties();
        if (!config.required()) {
            assumeTrue(config.configured(), "PostgreSQL GateY-6D integration is disabled");
        }
        assertTrue(config.configured(), "Missing required nq.postgres.smoke.* properties");

        String schema = "gatey6d_" + UUID.randomUUID().toString().replace("-", "");
        Flyway throughV38 = flyway(config, schema, "38");
        throughV38.migrate();
        JdbcTemplate jdbc = jdbc(config, schema);
        ExistingFixture historicalFixture = seedExistingFacts(jdbc);
        Flyway throughV39 = flyway(config, schema, "39");
        throughV39.migrate();
        HistoricalV39 historical = seedHistoricalV39Facts(jdbc, historicalFixture);

        Flyway latest = flyway(config, schema, null);
        long startedAt = System.nanoTime();
        latest.migrate();
        long migrationElapsedMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        latest.validate();
        System.out.println("gatey6e_v39_to_v41_elapsed_ms=" + migrationElapsedMs);
        try {
            assertEquals("42", latest.info().current().getVersion().getVersion());
            assertTrue(migrationElapsedMs < 60_000);
            assertEquals(historical.fingerprint(), historicalApprovalFingerprint(jdbc, historical.approvalId()));
            assertEquals("approval-scope.v1", jdbc.queryForObject(
                    "SELECT scope_schema_version FROM operator_approvals WHERE approval_id=?",
                    String.class, historical.approvalId()));
            assertEquals(0, jdbc.queryForObject(
                    "SELECT count(*) FROM operator_approvals WHERE approval_id=? AND pilot_scope_id IS NOT NULL",
                    Integer.class, historical.approvalId()));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM pilot_scope_bindings", Integer.class));
            assertEquals(0, jdbc.queryForObject(
                    "SELECT count(*) FROM pilot_prerequisite_observations", Integer.class));
            assertEquals(0, jdbc.queryForObject(
                    "SELECT count(*) FROM pilot_instrument_observation_items", Integer.class));

            ExistingFixture pilotFixture = seedExistingFacts(jdbc);
            JdbcLiveControlRepository liveRepository = new JdbcLiveControlRepository(jdbc);
            JdbcPilotScopeRepository pilotRepository = new JdbcPilotScopeRepository(jdbc);
            JdbcLiveControlAuthorization authorization = new JdbcLiveControlAuthorization(jdbc);
            var transactionManager = new DataSourceTransactionManager(jdbc.getDataSource());
            TransactionTemplate transactions = new TransactionTemplate(transactionManager);
            LiveSessionControlService liveService = new LiveSessionControlService(liveRepository, authorization);
            PilotScopeFactTransactionService pilotTransactions = new PilotScopeFactTransactionService(
                    liveService, liveRepository, pilotRepository, authorization, transactionManager);
            RiskLimitSet pilotRisk = risk(pilotFixture.creatorId(), 100);
            transactions.executeWithoutResult(status -> liveService.createRiskLimitSet(
                    new AuthenticatedLiveControlActor(pilotFixture.creatorId()), pilotRisk));

            Instant factNow = Instant.now().truncatedTo(ChronoUnit.MICROS);
            assertUnavailableTrustedObservationLeavesNoPartialFacts(
                    jdbc, liveRepository, pilotRepository, authorization, pilotTransactions,
                    pilotFixture, pilotRisk, factNow);
            LiveSession session = LiveSession.create(
                    UUID.randomUUID(), pilotFixture.creatorId(), pilotFixture.exchangeAccountId(),
                    pilotFixture.releaseId(), DIGEST_A, 1, pilotRisk.id(), pilotRisk.canonicalDigest(),
                    pilotFixture.credentialId(), List.of("BTC-USDT"), decimal("25"),
                    factNow.minusSeconds(5), factNow.plusSeconds(300), pilotFixture.creatorId(), factNow);
            PilotScopeBinding scope = pilotScope(session, pilotFixture.creatorId(), factNow);
            PilotObservationSet observations = pilotObservations(scope, UUID.randomUUID(), factNow, decimal("25"));
            PilotScopeBinding stored = pilotTransactions.materialize(
                    new AuthenticatedLiveControlActor(pilotFixture.creatorId()), session, pilotRisk,
                    createdEventAt(session, pilotFixture.creatorId(), factNow), scope, observations);
            assertEquals(scope.id(), stored.id());
            long operatorRoleId = jdbc.queryForObject(
                    "SELECT id FROM roles WHERE role_code = 'OPERATOR'", Long.class);
            jdbc.update("DELETE FROM user_roles WHERE user_id=? AND role_id=?",
                    pilotFixture.creatorId(), operatorRoleId);
            LiveControlException revokedReplay = assertThrows(LiveControlException.class,
                    () -> pilotTransactions.materialize(
                            new AuthenticatedLiveControlActor(pilotFixture.creatorId()), session, pilotRisk,
                            createdEventAt(session, pilotFixture.creatorId(), factNow), scope, observations));
            assertEquals("LIVE_SESSION_OPERATOR_ROLE_REQUIRED", revokedReplay.code());
            jdbc.update("INSERT INTO user_roles(user_id,role_id) VALUES (?,?)",
                    pilotFixture.creatorId(), operatorRoleId);
            assertEquals(4, jdbc.queryForObject(
                    "SELECT count(*) FROM pilot_prerequisite_observations WHERE pilot_scope_id=?",
                    Integer.class, scope.id()));
            assertEquals(1, jdbc.queryForObject(
                    "SELECT count(*) FROM pilot_instrument_observation_items WHERE observation_id=?",
                    Integer.class, observations.instrumentMetadata().id()));

            String postgresCanonical = jdbc.queryForObject("""
                    SELECT gate_y6d_pilot_scope_canonical_payload(
                        scope.session_id, scope.instrument_metadata_digest, scope.instrument_source_identity,
                        scope.instrument_source_schema_version, scope.instrument_maximum_age_ms,
                        scope.fee_schedule_digest, scope.fee_tier, scope.fee_evidence_class,
                        scope.fee_source_identity, scope.fee_source_schema_version, scope.fee_maximum_age_ms,
                        scope.balance_source_identity, scope.balance_source_schema_version, scope.balance_maximum_age_ms,
                        scope.clock_source_identity, scope.clock_source_schema_version, scope.clock_maximum_age_ms,
                        scope.signed_timestamp_source, scope.maximum_tolerated_skew_ms,
                        scope.endpoint_policy_version, scope.endpoint_policy_digest,
                        scope.provider_contract_identity, scope.provider_artifact_digest,
                        scope.worker_identity, scope.worker_release_digest)
                    FROM pilot_scope_bindings scope WHERE scope.pilot_scope_id=?
                    """, String.class, scope.id());
            assertEquals(com.guidinglight.nexusquant.livecontrol.domain.PilotScopeCanonicalEncoder.encode(session, scope),
                    postgresCanonical);
            assertEquals(scope.pilotScopeHash(), jdbc.queryForObject(
                    "SELECT gate_y6d_reconstruct_pilot_scope_hash(?)", String.class, scope.id()));

            PilotScopeBinding replay = transactions.execute(
                    status -> pilotRepository.materialize(session, scope));
            assertEquals(scope.id(), replay.id());
            assertEquals(observations.id(), transactions.execute(
                    status -> pilotRepository.appendObservationSet(scope, observations)).id());
            assertConcurrentPilotRetries(jdbc, liveService, pilotRepository, transactions, factNow);

            PilotScopeBinding conflict = pilotScopeWithProvider(scope, session, "provider-contract-2");
            LiveControlException scopeConflict = assertThrows(LiveControlException.class,
                    () -> transactions.execute(status -> pilotRepository.materialize(session, conflict)));
            assertEquals("PILOT_SCOPE_MATERIALIZATION_CONFLICT", scopeConflict.code());

            assertSqlState23514(() -> jdbc.update(
                    "UPDATE pilot_scope_bindings SET worker_identity='mutated' WHERE pilot_scope_id=?", scope.id()));
            assertSqlState23514(() -> jdbc.update(
                    "DELETE FROM pilot_scope_bindings WHERE pilot_scope_id=?", scope.id()));
            assertSqlState23514(() -> jdbc.update(
                    "UPDATE pilot_prerequisite_observations SET recorder_identity='mutated' WHERE observation_id=?",
                    observations.balanceSnapshot().id()));
            assertSqlState23514(() -> jdbc.update(
                    "DELETE FROM pilot_instrument_observation_items WHERE observation_id=?",
                    observations.instrumentMetadata().id()));

            PilotObservationSet identityConflict = pilotObservations(
                    scope, UUID.randomUUID(), factNow.plusMillis(1), decimal("26"));
            LiveControlException observationConflict = assertThrows(LiveControlException.class,
                    () -> transactions.execute(status -> pilotRepository.appendObservationSet(scope, identityConflict)));
            assertEquals("PREREQUISITE_OBSERVATION_IDENTITY_CONFLICT", observationConflict.code());

            assertIncompleteObservationSetRollback(jdbc, transactions, observations);
            assertFutureAndSkewObservationsRejected(jdbc, observations);
            assertTrue(pilotTransactions.preflight(session.id(), decimal("20")).eligible());
            assertPilotApprovalCompatibility(
                    jdbc, liveRepository, pilotRepository, transactions, session, scope,
                    pilotFixture.creatorId(), pilotFixture.approverId(), factNow);

            PilotScopeBinding lateScope = pilotScope(historical.session(), historicalFixture.creatorId(), factNow);
            assertSqlState23514(() -> transactions.execute(
                    status -> pilotRepository.materialize(historical.session(), lateScope)));

            assertTrue(pilotTransactions.preflight(session.id(), decimal("20")).eligible());
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM execution_intents", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM execution_receipts", Integer.class));
        } finally {
            latest.clean();
        }
    }

    @Test
    void shouldUpgradePopulatedV40WithoutFabricatingVenueEvidenceAndCoexistWithV2() {
        SmokeConfig config = SmokeConfig.fromSystemProperties();
        if (!config.required()) {
            assumeTrue(config.configured(), "PostgreSQL GateY-6E integration is disabled");
        }
        assertTrue(config.configured(), "Missing required nq.postgres.smoke.* properties");

        String schema = "gatey6e_v40_" + UUID.randomUUID().toString().replace("-", "");
        Flyway throughV40 = flyway(config, schema, "40");
        throughV40.migrate();
        JdbcTemplate jdbc = jdbc(config, schema);
        ExistingFixture legacyFixture = seedExistingFacts(jdbc);
        JdbcLiveControlRepository liveRepository = new JdbcLiveControlRepository(jdbc);
        JdbcPilotScopeRepository pilotRepository = new JdbcPilotScopeRepository(jdbc);
        JdbcLiveControlAuthorization authorization = new JdbcLiveControlAuthorization(jdbc);
        var transactionManager = new DataSourceTransactionManager(jdbc.getDataSource());
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        LiveSessionControlService liveService = new LiveSessionControlService(liveRepository, authorization);
        RiskLimitSet legacyRisk = risk(legacyFixture.creatorId(), 100);
        Instant factNow = Instant.now().truncatedTo(ChronoUnit.MICROS);
        LiveSession legacySession = LiveSession.create(
                UUID.randomUUID(), legacyFixture.creatorId(), legacyFixture.exchangeAccountId(),
                legacyFixture.releaseId(), DIGEST_A, 1, legacyRisk.id(), legacyRisk.canonicalDigest(),
                legacyFixture.credentialId(), List.of("BTC-USDT"), decimal("25"),
                factNow.minusSeconds(5), factNow.plusSeconds(300), legacyFixture.creatorId(), factNow);
        var legacyItem = legacyInstrumentItem();
        PilotScopeBinding legacyScope = pilotScope(
                legacySession, legacyFixture.creatorId(), factNow,
                PilotPrerequisiteObservation.InstrumentMetadata.LEGACY_SCHEMA_VERSION, legacyItem);
        PilotObservationSet legacyObservations = pilotObservations(
                legacyScope, UUID.randomUUID(), factNow, decimal("25"),
                PilotPrerequisiteObservation.InstrumentMetadata.LEGACY_SCHEMA_VERSION,
                legacyItem, "-legacy");
        transactions.executeWithoutResult(status -> {
            liveService.createRiskLimitSet(
                    new AuthenticatedLiveControlActor(legacyFixture.creatorId()), legacyRisk);
            liveService.createSession(
                    new AuthenticatedLiveControlActor(legacyFixture.creatorId()), legacySession, legacyRisk,
                    createdEventAt(legacySession, legacyFixture.creatorId(), factNow));
            pilotRepository.materialize(legacySession, legacyScope);
            appendLegacyV40ObservationSet(jdbc, legacyObservations);
        });
        String legacyFingerprint = legacyInstrumentFingerprint(jdbc, legacyObservations.instrumentMetadata().id());

        Flyway latest = flyway(config, schema, null);
        try {
            latest.migrate();
            latest.validate();
            assertEquals("42", latest.info().current().getVersion().getVersion());
            assertEquals(legacyFingerprint,
                    legacyInstrumentFingerprint(jdbc, legacyObservations.instrumentMetadata().id()));
            assertEquals("LEGACY_V40_REQUIRED", jdbc.queryForObject("""
                    SELECT minimum_order_value_evidence_class
                    FROM pilot_instrument_observation_items WHERE observation_id=?
                    """, String.class, legacyObservations.instrumentMetadata().id()));

            PilotObservationSet reloaded = pilotRepository.findObservationSet(
                    legacyScope.id(), legacyObservations.id()).orElseThrow();
            assertEquals(PilotPrerequisiteObservation.InstrumentMetadata.LEGACY_SCHEMA_VERSION,
                    reloaded.instrumentMetadata().envelope().observationSchemaVersion());
            assertEquals(PilotPrerequisiteObservation.MinimumOrderValueEvidenceClass.LEGACY_V40_REQUIRED,
                    reloaded.instrumentMetadata().items().getFirst().minimumOrderValueEvidenceClass());
            assertEquals(legacyObservations.instrumentMetadata().instrumentMetadataDigest(), jdbc.queryForObject(
                    "SELECT gate_y6d_instrument_metadata_digest(?)", String.class,
                    legacyObservations.instrumentMetadata().id()));
            assertEquals(legacyObservations.instrumentMetadata().observationPayloadHash(), jdbc.queryForObject(
                    "SELECT gate_y6d_observation_payload_hash(?)", String.class,
                    legacyObservations.instrumentMetadata().id()));
            assertInstrumentCanonicalBytesParity(jdbc, reloaded.instrumentMetadata());

            assertSqlState23514(() -> jdbc.update("""
                    INSERT INTO pilot_prerequisite_observations(
                        observation_id,pilot_scope_id,observation_set_id,observation_type,
                        observation_schema_version,observation_identity,source_identity,source_schema_version,
                        observed_at,recorded_at,recorder_identity,observation_payload_hash,
                        instrument_metadata_digest)
                    SELECT ?,pilot_scope_id,?,'INSTRUMENT_METADATA',
                           'instrument-metadata-observation.v1',?,source_identity,source_schema_version,
                           observed_at,recorded_at,recorder_identity,observation_payload_hash,
                           instrument_metadata_digest
                    FROM pilot_prerequisite_observations WHERE observation_id=?
                    """, UUID.randomUUID(), UUID.randomUUID(), "new-v1-" + UUID.randomUUID(),
                    legacyObservations.instrumentMetadata().id()));

            ExistingFixture v2Fixture = seedExistingFacts(jdbc);
            RiskLimitSet v2Risk = risk(v2Fixture.creatorId(), 101);
            LiveSession v2Session = LiveSession.create(
                    UUID.randomUUID(), v2Fixture.creatorId(), v2Fixture.exchangeAccountId(),
                    v2Fixture.releaseId(), DIGEST_A, 1, v2Risk.id(), v2Risk.canonicalDigest(),
                    v2Fixture.credentialId(), List.of("BTC-USDT"), decimal("25"),
                    factNow.minusSeconds(5), factNow.plusSeconds(300), v2Fixture.creatorId(), factNow);
            PilotScopeBinding v2Scope = pilotScope(v2Session, v2Fixture.creatorId(), factNow);
            PilotObservationSet v2Observations = pilotObservations(
                    v2Scope, UUID.randomUUID(), factNow.plusMillis(1), decimal("25"));
            transactions.executeWithoutResult(status -> {
                liveService.createRiskLimitSet(
                        new AuthenticatedLiveControlActor(v2Fixture.creatorId()), v2Risk);
                liveService.createSession(
                        new AuthenticatedLiveControlActor(v2Fixture.creatorId()), v2Session, v2Risk,
                        createdEventAt(v2Session, v2Fixture.creatorId(), factNow));
                pilotRepository.materialize(v2Session, v2Scope);
                pilotRepository.appendObservationSet(v2Scope, v2Observations);
            });
            assertEquals(1, jdbc.queryForObject("""
                    SELECT count(*) FROM pilot_prerequisite_observations
                    WHERE observation_type='INSTRUMENT_METADATA'
                      AND observation_schema_version='instrument-metadata-observation.v1'
                    """, Integer.class));
            assertEquals(1, jdbc.queryForObject("""
                    SELECT count(*) FROM pilot_prerequisite_observations
                    WHERE observation_type='INSTRUMENT_METADATA'
                      AND observation_schema_version='instrument-metadata-observation.v2'
                    """, Integer.class));
            var v2Item = v2Observations.instrumentMetadata().items().getFirst();
            assertEquals(PilotPrerequisiteObservation.MinimumOrderValueEvidenceClass.VENUE_NOT_PUBLISHED,
                    v2Item.minimumOrderValueEvidenceClass());
            assertNull(v2Item.minimumOrderValue());
            assertNull(v2Item.minimumOrderValueCurrency());
            assertInstrumentCanonicalBytesParity(jdbc, v2Observations.instrumentMetadata());

            ExistingFixture publishedFixture = seedExistingFacts(jdbc);
            RiskLimitSet publishedRisk = risk(publishedFixture.creatorId(), 102);
            List<PilotPrerequisiteObservation.InstrumentItem> publishedItems = publishedInstrumentItems();
            LiveSession publishedSession = LiveSession.create(
                    UUID.randomUUID(), publishedFixture.creatorId(), publishedFixture.exchangeAccountId(),
                    publishedFixture.releaseId(), DIGEST_A, 1, publishedRisk.id(), publishedRisk.canonicalDigest(),
                    publishedFixture.credentialId(), List.of("BTC-USDT", "ETH-USDT"), decimal("25"),
                    factNow.minusSeconds(5), factNow.plusSeconds(300), publishedFixture.creatorId(), factNow);
            PilotScopeBinding publishedScope = pilotScope(
                    publishedSession, publishedFixture.creatorId(), factNow,
                    PilotPrerequisiteObservation.InstrumentMetadata.SCHEMA_VERSION, publishedItems);
            PilotObservationSet publishedObservations = pilotObservations(
                    publishedScope, UUID.randomUUID(), factNow.plusMillis(2), decimal("25"),
                    PilotPrerequisiteObservation.InstrumentMetadata.SCHEMA_VERSION,
                    publishedItems, "-published");
            transactions.executeWithoutResult(status -> {
                liveService.createRiskLimitSet(
                        new AuthenticatedLiveControlActor(publishedFixture.creatorId()), publishedRisk);
                liveService.createSession(
                        new AuthenticatedLiveControlActor(publishedFixture.creatorId()),
                        publishedSession, publishedRisk,
                        createdEventAt(publishedSession, publishedFixture.creatorId(), factNow));
                pilotRepository.materialize(publishedSession, publishedScope);
                pilotRepository.appendObservationSet(publishedScope, publishedObservations);
            });
            assertInstrumentCanonicalBytesParity(jdbc, publishedObservations.instrumentMetadata());
            assertEquals(publishedObservations.instrumentMetadata().instrumentMetadataDigest(), jdbc.queryForObject(
                    "SELECT gate_y6d_instrument_metadata_digest(?)", String.class,
                    publishedObservations.instrumentMetadata().id()));
            assertSqlState23514(() -> jdbc.update("""
                    INSERT INTO pilot_instrument_observation_items(
                        observation_id,observation_type,symbol,trading_status,tick_size,lot_size,
                        minimum_order_size,minimum_order_value_evidence_class,
                        minimum_order_value,minimum_order_value_currency)
                    VALUES (?,'INSTRUMENT_METADATA','ETH-USDT','LIVE',0.1,0.001,0.001,
                            'VENUE_NOT_PUBLISHED',1,'USDT')
                    """, v2Observations.instrumentMetadata().id()));
            assertSqlState23514(() -> jdbc.update("""
                    INSERT INTO pilot_instrument_observation_items(
                        observation_id,observation_type,symbol,trading_status,tick_size,lot_size,
                        minimum_order_size,minimum_order_value_evidence_class,
                        minimum_order_value,minimum_order_value_currency)
                     VALUES (?,'INSTRUMENT_METADATA','ETH-USDT','LIVE',0.1,0.001,0.001,
                             'VENUE_PUBLISHED',NULL,NULL)
                     """, v2Observations.instrumentMetadata().id()));
            assertSqlState23514(() -> jdbc.update("""
                    INSERT INTO pilot_instrument_observation_items(
                        observation_id,observation_type,symbol,trading_status,tick_size,lot_size,
                        minimum_order_size,minimum_order_value_evidence_class,
                        minimum_order_value,minimum_order_value_currency)
                    VALUES (?,'INSTRUMENT_METADATA','ETH-USDT','LIVE',0.1,0.001,0.001,
                            'LEGACY_V40_REQUIRED',5,'USDT')
                    """, v2Observations.instrumentMetadata().id()));
            assertSqlState23514(() -> jdbc.update("""
                    UPDATE pilot_instrument_observation_items SET minimum_order_value=6
                    WHERE observation_id=?
                    """, legacyObservations.instrumentMetadata().id()));
        } finally {
            latest.clean();
        }
    }

    @Test
    void shouldReplayV1ToV41AndRollbackOnMigrationLockTimeout() throws Exception {
        SmokeConfig config = SmokeConfig.fromSystemProperties();
        if (!config.required()) {
            assumeTrue(config.configured(), "PostgreSQL GateY-6D integration is disabled");
        }
        assertTrue(config.configured(), "Missing required nq.postgres.smoke.* properties");

        String replaySchema = "gatey6d_replay_" + UUID.randomUUID().toString().replace("-", "");
        Flyway replay = flyway(config, replaySchema, null);
        replay.migrate();
        try {
            assertEquals("42", replay.info().current().getVersion().getVersion());
            replay.validate();
        } finally {
            replay.clean();
        }

        String timeoutSchema = "gatey6e_timeout_" + UUID.randomUUID().toString().replace("-", "");
        Flyway throughV40 = flyway(config, timeoutSchema, "40");
        throughV40.migrate();
        JdbcTemplate jdbc = jdbc(config, timeoutSchema);
        TransactionTemplate locker = new TransactionTemplate(
                new DataSourceTransactionManager(jdbc.getDataSource()));
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?> lock = executor.submit(() -> locker.executeWithoutResult(status -> {
                jdbc.execute("LOCK TABLE pilot_instrument_observation_items IN ACCESS SHARE MODE");
                locked.countDown();
                try {
                    assertTrue(release.await(15, TimeUnit.SECONDS));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
            }));
            assertTrue(locked.await(10, TimeUnit.SECONDS));
            long startedAt = System.nanoTime();
            assertThrows(FlywayException.class, () -> flyway(config, timeoutSchema, null).migrate());
            long elapsedMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            System.out.println("gatey6e_v41_lock_timeout_elapsed_ms=" + elapsedMs);
            assertTrue(elapsedMs >= 4_000 && elapsedMs < 15_000);
            release.countDown();
            lock.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
        }
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema=current_schema() AND table_name='pilot_instrument_observation_items'
                  AND column_name='minimum_order_value_evidence_class'
                """, Integer.class));
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*) FROM pg_constraint constraint_row
                JOIN pg_class table_row ON table_row.oid=constraint_row.conrelid
                JOIN pg_namespace namespace_row ON namespace_row.oid=table_row.relnamespace
                WHERE namespace_row.nspname=current_schema()
                  AND table_row.relname='pilot_instrument_observation_items'
                  AND constraint_row.conname='chk_pilot_instrument_observation_item_value_evidence'
                """, Integer.class));
        assertEquals("40", throughV40.info().current().getVersion().getVersion());
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version='41'", Integer.class));
        throughV40.clean();

        String failureSchema = "gatey6e_failure_" + UUID.randomUUID().toString().replace("-", "");
        Flyway failureThroughV40 = flyway(config, failureSchema, "40");
        failureThroughV40.migrate();
        JdbcTemplate failureJdbc = jdbc(config, failureSchema);
        failureJdbc.execute("""
                CREATE FUNCTION gate_y6e_guard_instrument_observation_schema_insert()
                RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN RETURN NEW; END $$
                """);
        assertThrows(FlywayException.class, () -> flyway(config, failureSchema, null).migrate());
        assertEquals(0, failureJdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema=current_schema() AND table_name='pilot_instrument_observation_items'
                  AND column_name='minimum_order_value_evidence_class'
                """, Integer.class));
        assertEquals(0, failureJdbc.queryForObject("""
                SELECT count(*) FROM pg_constraint constraint_row
                JOIN pg_class table_row ON table_row.oid=constraint_row.conrelid
                JOIN pg_namespace namespace_row ON namespace_row.oid=table_row.relnamespace
                WHERE namespace_row.nspname=current_schema()
                  AND table_row.relname='pilot_instrument_observation_items'
                  AND constraint_row.conname='chk_pilot_instrument_observation_item_value_evidence'
                """, Integer.class));
        assertEquals("40", failureThroughV40.info().current().getVersion().getVersion());
        assertEquals(0, failureJdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version='41'", Integer.class));
        failureThroughV40.clean();
    }

    private static HistoricalV39 seedHistoricalV39Facts(JdbcTemplate jdbc, ExistingFixture fixture) {
        JdbcLiveControlRepository repository = new JdbcLiveControlRepository(jdbc);
        RiskLimitSet risk = risk(fixture.creatorId(), 99);
        repository.createRiskLimitSet(risk);
        LiveSession session = session(fixture, risk, UUID.randomUUID(), NOW);
        repository.createSession(session);
        UUID approvalId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO operator_approvals(
                    approval_id,session_id,scope_hash,release_digest,risk_limit_set_digest,
                    approver_id,approver_role,decision,reason,approved_at,expires_at
                ) VALUES (?,?,?,?,?,?,'LIVE_APPROVER','REJECTED','historical-v39',?,?)
                """, approvalId, session.id(), session.approvalScopeHash(), session.releaseDigest(),
                session.riskLimitSetDigest(), fixture.approverId(), Timestamp.from(NOW),
                Timestamp.from(NOW.plusSeconds(120)));
        return new HistoricalV39(session, approvalId, historicalApprovalFingerprint(jdbc, approvalId));
    }

    private static String historicalApprovalFingerprint(JdbcTemplate jdbc, UUID approvalId) {
        return jdbc.queryForObject("""
                SELECT concat_ws('|', approval_id::text, session_id::text, scope_hash, release_digest,
                    risk_limit_set_digest, approver_id::text, approver_role, decision, reason,
                    approved_at::text, expires_at::text)
                FROM operator_approvals WHERE approval_id=?
                """, String.class, approvalId);
    }

    private static void assertUnavailableTrustedObservationLeavesNoPartialFacts(
            JdbcTemplate jdbc,
            JdbcLiveControlRepository liveRepository,
            JdbcPilotScopeRepository pilotRepository,
            JdbcLiveControlAuthorization authorization,
            PilotScopeFactTransactionService pilotTransactions,
            ExistingFixture fixture,
            RiskLimitSet risk,
            Instant factNow
    ) {
        UUID sessionId = UUID.randomUUID();
        LiveSession expectedSession = LiveSession.create(
                sessionId, fixture.creatorId(), fixture.exchangeAccountId(), fixture.releaseId(), DIGEST_A, 1,
                risk.id(), risk.canonicalDigest(), fixture.credentialId(), List.of("BTC-USDT"), decimal("25"),
                factNow.minusSeconds(5), factNow.plusSeconds(300), fixture.creatorId(), factNow);
        PilotScopeBinding expectedScope = pilotScope(expectedSession, fixture.creatorId(), factNow);
        PilotScopeAuthorityResolver.ResolvedScopeBindings bindings = resolvedBindings(expectedScope);
        var riskSelection = new PilotScopeMaterializationCommand.RiskSelection(
                risk.id(), risk.canonicalDigest(), risk.version(), risk.capitalCap(), risk.maxOrderNotional(),
                risk.maxSymbolPositionNotional(), risk.maxDailyRealizedLoss(), risk.maxDailyTotalLoss(),
                risk.maxOpenOrders(), risk.maxIntradayOrders(), risk.symbolAllowlist(),
                risk.maxSessionDurationSeconds(), risk.spreadLimitBps(), risk.slippageLimitBps(),
                risk.maxMarketDataAgeMs(), risk.minDataCoverageBps());
        var command = new PilotScopeMaterializationCommand(
                sessionId, expectedScope.id(), fixture.exchangeAccountId(), fixture.credentialId(),
                fixture.releaseId(), DIGEST_A, 1, riskSelection, List.of("BTC-USDT"), decimal("25"),
                factNow.minusSeconds(5), factNow.plusSeconds(300), expectedScope.pilotScopeHash(),
                "unavailable-" + sessionId, "unavailable-request", "unavailable-trace");
        PilotScopeAuthorityResolver resolver = (actor, ignored) ->
                new PilotScopeAuthorityResolver.ResolvedAuthority(risk, bindings);
        var controlPlane = new PilotScopeControlPlaneService(
                resolver, new UnavailablePilotPrerequisiteObservationAuthority(), pilotTransactions,
                liveRepository, pilotRepository, authorization);

        LiveControlException failure = assertThrows(
                LiveControlException.class,
                () -> controlPlane.materialize(
                        new AuthenticatedLiveControlActor(fixture.creatorId()), command));

        assertEquals("TRUSTED_PREREQUISITE_OBSERVATION_UNAVAILABLE", failure.code());
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM live_sessions WHERE session_id=?", Integer.class, sessionId));
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM pilot_scope_bindings WHERE pilot_scope_id=?",
                Integer.class, expectedScope.id()));
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM pilot_prerequisite_observations WHERE pilot_scope_id=?",
                Integer.class, expectedScope.id()));
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM operator_approvals WHERE session_id=?", Integer.class, sessionId));
    }

    private static PilotScopeAuthorityResolver.ResolvedScopeBindings resolvedBindings(PilotScopeBinding scope) {
        return new PilotScopeAuthorityResolver.ResolvedScopeBindings(
                scope.instrumentMetadataDigest(), scope.instrumentSourceIdentity(),
                scope.instrumentSourceSchemaVersion(), scope.instrumentMaximumAgeMs(), scope.feeScheduleDigest(),
                scope.feeTier(), scope.feeEvidenceClass(), scope.feeSourceIdentity(),
                scope.feeSourceSchemaVersion(), scope.feeMaximumAgeMs(), scope.balanceSourceIdentity(),
                scope.balanceSourceSchemaVersion(), scope.balanceMaximumAgeMs(), scope.clockSourceIdentity(),
                scope.clockSourceSchemaVersion(), scope.clockMaximumAgeMs(), scope.signedTimestampSource(),
                scope.maximumToleratedSkewMs(), scope.endpointPolicyVersion(), scope.endpointPolicyDigest(),
                scope.providerContractIdentity(), scope.providerArtifactDigest(), scope.workerIdentity(),
                scope.workerReleaseDigest());
    }

    private static PilotScopeBinding pilotScope(LiveSession session, long createdBy, Instant createdAt) {
        var item = instrumentItem(PilotPrerequisiteObservation.TradingStatus.LIVE);
        return pilotScope(
                session, createdBy, createdAt,
                PilotPrerequisiteObservation.InstrumentMetadata.SCHEMA_VERSION, item);
    }

    private static PilotScopeBinding pilotScope(
            LiveSession session,
            long createdBy,
            Instant createdAt,
            String instrumentSchemaVersion,
            PilotPrerequisiteObservation.InstrumentItem item
    ) {
        return pilotScope(session, createdBy, createdAt, instrumentSchemaVersion, List.of(item));
    }

    private static PilotScopeBinding pilotScope(
            LiveSession session,
            long createdBy,
            Instant createdAt,
            String instrumentSchemaVersion,
            List<PilotPrerequisiteObservation.InstrumentItem> items
    ) {
        PilotScopeBinding draft = new PilotScopeBinding(
                UUID.randomUUID(), session.id(),
                PilotObservationCanonicalEncoder.instrumentMetadataDigest(instrumentSchemaVersion, items),
                "instrument-source", "instrument-source.v1", 300_000,
                DIGEST_B, "tier-1", PilotScopeBinding.FeeEvidenceClass.OBSERVED_PRIVATE,
                "fee-source", "fee-source.v1", 3_600_000,
                "balance-source", "balance-source.v1", 10_000,
                "clock-source", "clock-source.v1", 60_000,
                PilotScopeBinding.SIGNED_TIMESTAMP_SOURCE, 500,
                "endpoint-policy.v1", DIGEST_A, "provider-contract", DIGEST_A,
                "pilot-worker", DIGEST_B, "0".repeat(64), createdBy, createdAt
        );
        return draft.withCanonicalHash(session);
    }

    private static PilotScopeBinding pilotScopeWithProvider(
            PilotScopeBinding source,
            LiveSession session,
            String providerIdentity
    ) {
        return new PilotScopeBinding(
                UUID.randomUUID(), source.sessionId(), source.instrumentMetadataDigest(),
                source.instrumentSourceIdentity(), source.instrumentSourceSchemaVersion(),
                source.instrumentMaximumAgeMs(), source.feeScheduleDigest(), source.feeTier(),
                source.feeEvidenceClass(), source.feeSourceIdentity(), source.feeSourceSchemaVersion(),
                source.feeMaximumAgeMs(), source.balanceSourceIdentity(), source.balanceSourceSchemaVersion(),
                source.balanceMaximumAgeMs(), source.clockSourceIdentity(), source.clockSourceSchemaVersion(),
                source.clockMaximumAgeMs(), source.signedTimestampSource(), source.maximumToleratedSkewMs(),
                source.endpointPolicyVersion(), source.endpointPolicyDigest(), providerIdentity,
                source.providerArtifactDigest(), source.workerIdentity(), source.workerReleaseDigest(),
                "0".repeat(64), source.createdBy(), source.createdAt()
        ).withCanonicalHash(session);
    }

    private static PilotScopeBinding pilotScopeWithId(PilotScopeBinding source, UUID pilotScopeId) {
        return new PilotScopeBinding(
                pilotScopeId, source.sessionId(), source.instrumentMetadataDigest(),
                source.instrumentSourceIdentity(), source.instrumentSourceSchemaVersion(),
                source.instrumentMaximumAgeMs(), source.feeScheduleDigest(), source.feeTier(),
                source.feeEvidenceClass(), source.feeSourceIdentity(), source.feeSourceSchemaVersion(),
                source.feeMaximumAgeMs(), source.balanceSourceIdentity(), source.balanceSourceSchemaVersion(),
                source.balanceMaximumAgeMs(), source.clockSourceIdentity(), source.clockSourceSchemaVersion(),
                source.clockMaximumAgeMs(), source.signedTimestampSource(), source.maximumToleratedSkewMs(),
                source.endpointPolicyVersion(), source.endpointPolicyDigest(), source.providerContractIdentity(),
                source.providerArtifactDigest(), source.workerIdentity(), source.workerReleaseDigest(),
                source.pilotScopeHash(), source.createdBy(), source.createdAt()
        );
    }

    private static void assertConcurrentPilotRetries(
            JdbcTemplate jdbc,
            LiveSessionControlService liveService,
            JdbcPilotScopeRepository pilotRepository,
            TransactionTemplate transactions,
            Instant factNow
    ) throws Exception {
        ExistingFixture fixture = seedExistingFacts(jdbc);
        RiskLimitSet riskLimitSet = risk(fixture.creatorId(), 101);
        transactions.executeWithoutResult(status -> liveService.createRiskLimitSet(
                new AuthenticatedLiveControlActor(fixture.creatorId()), riskLimitSet));
        LiveSession concurrentSession = LiveSession.create(
                UUID.randomUUID(), fixture.creatorId(), fixture.exchangeAccountId(), fixture.releaseId(),
                DIGEST_A, 1, riskLimitSet.id(), riskLimitSet.canonicalDigest(), fixture.credentialId(),
                List.of("BTC-USDT"), decimal("25"), factNow.minusSeconds(5), factNow.plusSeconds(300),
                fixture.creatorId(), factNow);
        transactions.executeWithoutResult(status -> liveService.createSession(
                new AuthenticatedLiveControlActor(fixture.creatorId()), concurrentSession, riskLimitSet,
                createdEventAt(concurrentSession, fixture.creatorId(), factNow)));

        PilotScopeBinding firstScope = pilotScope(concurrentSession, fixture.creatorId(), factNow);
        PilotScopeBinding secondScope = pilotScopeWithId(firstScope, UUID.randomUUID());
        CountDownLatch scopeStart = new CountDownLatch(1);
        List<PilotScopeBinding> scopeResults;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<PilotScopeBinding>> futures = List.of(firstScope, secondScope).stream()
                    .map(candidate -> executor.submit(() -> {
                        assertTrue(scopeStart.await(10, TimeUnit.SECONDS));
                        return transactions.execute(status -> pilotRepository.materialize(concurrentSession, candidate));
                    }))
                    .toList();
            scopeStart.countDown();
            scopeResults = List.of(
                    futures.get(0).get(10, TimeUnit.SECONDS),
                    futures.get(1).get(10, TimeUnit.SECONDS));
        }
        assertEquals(scopeResults.get(0).id(), scopeResults.get(1).id());
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM pilot_scope_bindings WHERE session_id=?",
                Integer.class, concurrentSession.id()));

        PilotScopeBinding storedScope = scopeResults.getFirst();
        Instant observationTime = factNow.plusMillis(5);
        PilotObservationSet firstSet = pilotObservations(
                storedScope, UUID.randomUUID(), observationTime, decimal("25"));
        PilotObservationSet secondSet = pilotObservations(
                storedScope, UUID.randomUUID(), observationTime, decimal("25"));
        CountDownLatch observationStart = new CountDownLatch(1);
        List<PilotObservationSet> observationResults;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<PilotObservationSet>> futures = List.of(firstSet, secondSet).stream()
                    .map(candidate -> executor.submit(() -> {
                        assertTrue(observationStart.await(10, TimeUnit.SECONDS));
                        return transactions.execute(status -> pilotRepository.appendObservationSet(storedScope, candidate));
                    }))
                    .toList();
            observationStart.countDown();
            observationResults = List.of(
                    futures.get(0).get(10, TimeUnit.SECONDS),
                    futures.get(1).get(10, TimeUnit.SECONDS));
        }
        assertEquals(observationResults.get(0).id(), observationResults.get(1).id());
        assertEquals(4, jdbc.queryForObject(
                "SELECT count(*) FROM pilot_prerequisite_observations WHERE pilot_scope_id=?",
                Integer.class, storedScope.id()));
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(DISTINCT observation_set_id) FROM pilot_prerequisite_observations WHERE pilot_scope_id=?",
                Integer.class, storedScope.id()));
    }

    private static PilotObservationSet pilotObservations(
            PilotScopeBinding scope,
            UUID setId,
            Instant recordedAt,
            BigDecimal availableBalance
    ) {
        return pilotObservations(
                scope, setId, recordedAt, availableBalance,
                PilotPrerequisiteObservation.InstrumentMetadata.SCHEMA_VERSION,
                instrumentItem(PilotPrerequisiteObservation.TradingStatus.LIVE), "");
    }

    private static PilotObservationSet pilotObservations(
            PilotScopeBinding scope,
            UUID setId,
            Instant recordedAt,
            BigDecimal availableBalance,
            String instrumentSchemaVersion,
            PilotPrerequisiteObservation.InstrumentItem item,
            String identitySuffix
    ) {
        return pilotObservations(
                scope, setId, recordedAt, availableBalance, instrumentSchemaVersion, List.of(item), identitySuffix);
    }

    private static PilotObservationSet pilotObservations(
            PilotScopeBinding scope,
            UUID setId,
            Instant recordedAt,
            BigDecimal availableBalance,
            String instrumentSchemaVersion,
            List<PilotPrerequisiteObservation.InstrumentItem> items,
            String identitySuffix
    ) {
        Instant observedAt = recordedAt.minusMillis(100);
        var instrument = canonical(new PilotPrerequisiteObservation.InstrumentMetadata(
                observationEnvelope(scope, setId, "instrument-identity" + identitySuffix, scope.instrumentSourceIdentity(),
                        scope.instrumentSourceSchemaVersion(),
                        instrumentSchemaVersion, observedAt, recordedAt),
                scope.instrumentMetadataDigest(), items));
        var fee = canonical(new PilotPrerequisiteObservation.FeeSchedule(
                observationEnvelope(scope, setId, "fee-identity" + identitySuffix, scope.feeSourceIdentity(),
                        scope.feeSourceSchemaVersion(),
                        PilotPrerequisiteObservation.FeeSchedule.SCHEMA_VERSION, observedAt, recordedAt),
                scope.feeScheduleDigest(), scope.feeTier(), scope.feeEvidenceClass(),
                decimal("0.001"), decimal("0.0015"),
                PilotPrerequisiteObservation.FeeSchedule.LOSS_TREATMENT));
        var balance = canonical(new PilotPrerequisiteObservation.BalanceSnapshot(
                observationEnvelope(scope, setId, "balance-identity" + identitySuffix, scope.balanceSourceIdentity(),
                        scope.balanceSourceSchemaVersion(),
                        PilotPrerequisiteObservation.BalanceSnapshot.SCHEMA_VERSION, observedAt, recordedAt),
                DIGEST_A, "USDT", availableBalance));
        var clock = canonical(new PilotPrerequisiteObservation.ClockSync(
                observationEnvelope(scope, setId, "clock-identity" + identitySuffix, scope.clockSourceIdentity(),
                        scope.clockSourceSchemaVersion(),
                        PilotPrerequisiteObservation.ClockSync.SCHEMA_VERSION, observedAt, recordedAt),
                DIGEST_B, scope.signedTimestampSource(), 25));
        return new PilotObservationSet(setId, scope.id(), instrument, fee, balance, clock);
    }

    private static PilotPrerequisiteObservation.InstrumentItem instrumentItem(
            PilotPrerequisiteObservation.TradingStatus status
    ) {
        return new PilotPrerequisiteObservation.InstrumentItem(
                "BTC-USDT", status, new BigDecimal("0.1"), new BigDecimal("0.001"),
                new BigDecimal("0.001"),
                PilotPrerequisiteObservation.MinimumOrderValueEvidenceClass.VENUE_NOT_PUBLISHED,
                null, null);
    }

    private static PilotPrerequisiteObservation.InstrumentItem legacyInstrumentItem() {
        return new PilotPrerequisiteObservation.InstrumentItem(
                "BTC-USDT", PilotPrerequisiteObservation.TradingStatus.LIVE,
                new BigDecimal("0.1"), new BigDecimal("0.001"), new BigDecimal("0.001"),
                PilotPrerequisiteObservation.MinimumOrderValueEvidenceClass.LEGACY_V40_REQUIRED,
                new BigDecimal("5"), "USDT");
    }

    private static List<PilotPrerequisiteObservation.InstrumentItem> publishedInstrumentItems() {
        return List.of(
                new PilotPrerequisiteObservation.InstrumentItem(
                        "BTC-USDT", PilotPrerequisiteObservation.TradingStatus.LIVE,
                        new BigDecimal("0.1000"), new BigDecimal("0.001000"), new BigDecimal("0.001000"),
                        PilotPrerequisiteObservation.MinimumOrderValueEvidenceClass.VENUE_PUBLISHED,
                        new BigDecimal("5.0000"), "USDT"),
                new PilotPrerequisiteObservation.InstrumentItem(
                        "ETH-USDT", PilotPrerequisiteObservation.TradingStatus.LIVE,
                        new BigDecimal("0.0100"), new BigDecimal("0.000100"), new BigDecimal("0.000100"),
                        PilotPrerequisiteObservation.MinimumOrderValueEvidenceClass.VENUE_PUBLISHED,
                        new BigDecimal("3.5000"), "USDT")
        );
    }

    private static void assertInstrumentCanonicalBytesParity(
            JdbcTemplate jdbc,
            PilotPrerequisiteObservation.InstrumentMetadata observation
    ) {
        String encoded = PilotObservationCanonicalEncoder.encode(observation);
        String marker = ",\"items\":";
        int itemsStart = encoded.indexOf(marker);
        assertTrue(itemsStart >= 0);
        String javaItems = encoded.substring(itemsStart + marker.length(), encoded.length() - 2);
        String postgresItems = jdbc.queryForObject(
                "SELECT '[' || gate_y6e_instrument_items_canonical(?) || ']'",
                String.class, observation.id());
        assertEquals(javaItems, postgresItems);
    }

    private static PilotPrerequisiteObservation.Envelope observationEnvelope(
            PilotScopeBinding scope,
            UUID setId,
            String identity,
            String source,
            String sourceSchema,
            String observationSchema,
            Instant observedAt,
            Instant recordedAt
    ) {
        return new PilotPrerequisiteObservation.Envelope(
                UUID.randomUUID(), scope.id(), setId, observationSchema, identity, source, sourceSchema,
                observedAt, recordedAt, scope.workerIdentity(), "0".repeat(64));
    }

    private static PilotPrerequisiteObservation.InstrumentMetadata canonical(
            PilotPrerequisiteObservation.InstrumentMetadata value
    ) {
        return new PilotPrerequisiteObservation.InstrumentMetadata(
                value.envelope().withPayloadHash(PilotObservationCanonicalEncoder.digest(value)),
                value.instrumentMetadataDigest(), value.items());
    }

    private static PilotPrerequisiteObservation.FeeSchedule canonical(
            PilotPrerequisiteObservation.FeeSchedule value
    ) {
        return new PilotPrerequisiteObservation.FeeSchedule(
                value.envelope().withPayloadHash(PilotObservationCanonicalEncoder.digest(value)),
                value.feeScheduleDigest(), value.feeTier(), value.feeEvidenceClass(),
                value.makerFeeRate(), value.takerFeeRate(), value.feeLossTreatment());
    }

    private static PilotPrerequisiteObservation.BalanceSnapshot canonical(
            PilotPrerequisiteObservation.BalanceSnapshot value
    ) {
        return new PilotPrerequisiteObservation.BalanceSnapshot(
                value.envelope().withPayloadHash(PilotObservationCanonicalEncoder.digest(value)),
                value.balanceSnapshotDigest(), value.balanceCurrency(), value.availableBalance());
    }

    private static PilotPrerequisiteObservation.ClockSync canonical(
            PilotPrerequisiteObservation.ClockSync value
    ) {
        return new PilotPrerequisiteObservation.ClockSync(
                value.envelope().withPayloadHash(PilotObservationCanonicalEncoder.digest(value)),
                value.clockSyncObservationDigest(), value.signedTimestampSource(), value.observedSkewMs());
    }

    private static void appendLegacyV40ObservationSet(
            JdbcTemplate jdbc,
            PilotObservationSet observations
    ) {
        var instrument = observations.instrumentMetadata();
        jdbc.update("""
                INSERT INTO pilot_prerequisite_observations(
                    observation_id,pilot_scope_id,observation_set_id,observation_type,
                    observation_schema_version,observation_identity,source_identity,source_schema_version,
                    observed_at,recorded_at,recorder_identity,observation_payload_hash,
                    instrument_metadata_digest)
                VALUES (?,?,?,'INSTRUMENT_METADATA',?,?,?,?,?,?,?,?,?)
                """, instrument.id(), instrument.pilotScopeId(), instrument.observationSetId(),
                instrument.envelope().observationSchemaVersion(), instrument.envelope().observationIdentity(),
                instrument.envelope().sourceIdentity(), instrument.envelope().sourceSchemaVersion(),
                Timestamp.from(instrument.envelope().observedAt()), Timestamp.from(instrument.envelope().recordedAt()),
                instrument.envelope().recorderIdentity(), instrument.observationPayloadHash(),
                instrument.instrumentMetadataDigest());

        var fee = observations.feeSchedule();
        jdbc.update("""
                INSERT INTO pilot_prerequisite_observations(
                    observation_id,pilot_scope_id,observation_set_id,observation_type,
                    observation_schema_version,observation_identity,source_identity,source_schema_version,
                    observed_at,recorded_at,recorder_identity,observation_payload_hash,
                    fee_schedule_digest,fee_tier,fee_evidence_class,maker_fee_rate,taker_fee_rate,
                    fee_loss_treatment)
                VALUES (?,?,?,'FEE_SCHEDULE',?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, fee.id(), fee.pilotScopeId(), fee.observationSetId(),
                fee.envelope().observationSchemaVersion(), fee.envelope().observationIdentity(),
                fee.envelope().sourceIdentity(), fee.envelope().sourceSchemaVersion(),
                Timestamp.from(fee.envelope().observedAt()), Timestamp.from(fee.envelope().recordedAt()),
                fee.envelope().recorderIdentity(), fee.observationPayloadHash(), fee.feeScheduleDigest(),
                fee.feeTier(), fee.feeEvidenceClass().name(), fee.makerFeeRate(), fee.takerFeeRate(),
                fee.feeLossTreatment());

        var balance = observations.balanceSnapshot();
        jdbc.update("""
                INSERT INTO pilot_prerequisite_observations(
                    observation_id,pilot_scope_id,observation_set_id,observation_type,
                    observation_schema_version,observation_identity,source_identity,source_schema_version,
                    observed_at,recorded_at,recorder_identity,observation_payload_hash,
                    balance_snapshot_digest,balance_currency,available_balance)
                VALUES (?,?,?,'BALANCE_SNAPSHOT',?,?,?,?,?,?,?,?,?,?,?)
                """, balance.id(), balance.pilotScopeId(), balance.observationSetId(),
                balance.envelope().observationSchemaVersion(), balance.envelope().observationIdentity(),
                balance.envelope().sourceIdentity(), balance.envelope().sourceSchemaVersion(),
                Timestamp.from(balance.envelope().observedAt()), Timestamp.from(balance.envelope().recordedAt()),
                balance.envelope().recorderIdentity(), balance.observationPayloadHash(),
                balance.balanceSnapshotDigest(), balance.balanceCurrency(), balance.availableBalance());

        var clock = observations.clockSync();
        jdbc.update("""
                INSERT INTO pilot_prerequisite_observations(
                    observation_id,pilot_scope_id,observation_set_id,observation_type,
                    observation_schema_version,observation_identity,source_identity,source_schema_version,
                    observed_at,recorded_at,recorder_identity,observation_payload_hash,
                    clock_sync_observation_digest,signed_timestamp_source,observed_skew_ms)
                VALUES (?,?,?,'CLOCK_SYNC',?,?,?,?,?,?,?,?,?,?,?)
                """, clock.id(), clock.pilotScopeId(), clock.observationSetId(),
                clock.envelope().observationSchemaVersion(), clock.envelope().observationIdentity(),
                clock.envelope().sourceIdentity(), clock.envelope().sourceSchemaVersion(),
                Timestamp.from(clock.envelope().observedAt()), Timestamp.from(clock.envelope().recordedAt()),
                clock.envelope().recorderIdentity(), clock.observationPayloadHash(),
                clock.clockSyncObservationDigest(), clock.signedTimestampSource(), clock.observedSkewMs());

        var item = instrument.items().getFirst();
        jdbc.update("""
                INSERT INTO pilot_instrument_observation_items(
                    observation_id,observation_type,symbol,trading_status,tick_size,lot_size,
                    minimum_order_size,minimum_order_value,minimum_order_value_currency)
                VALUES (?,'INSTRUMENT_METADATA',?,?,?,?,?,?,?)
                """, instrument.id(), item.symbol(), item.tradingStatus().name(), item.tickSize(), item.lotSize(),
                item.minimumOrderSize(), item.minimumOrderValue(), item.minimumOrderValueCurrency());
    }

    private static String legacyInstrumentFingerprint(JdbcTemplate jdbc, UUID observationId) {
        return jdbc.queryForObject("""
                SELECT concat_ws('|', observation.observation_schema_version,
                    observation.instrument_metadata_digest, observation.observation_payload_hash,
                    item.symbol, item.trading_status, item.tick_size::TEXT, item.lot_size::TEXT,
                    item.minimum_order_size::TEXT, item.minimum_order_value::TEXT,
                    item.minimum_order_value_currency)
                FROM pilot_prerequisite_observations observation
                JOIN pilot_instrument_observation_items item
                  ON item.observation_id=observation.observation_id
                WHERE observation.observation_id=?
                """, String.class, observationId);
    }

    private static void assertIncompleteObservationSetRollback(
            JdbcTemplate jdbc,
            TransactionTemplate transactions,
            PilotObservationSet source
    ) {
        assertSqlState23514(() -> transactions.executeWithoutResult(status -> jdbc.update("""
                INSERT INTO pilot_prerequisite_observations(
                    observation_id,pilot_scope_id,observation_set_id,observation_type,
                    observation_schema_version,observation_identity,source_identity,source_schema_version,
                    observed_at,recorded_at,recorder_identity,observation_payload_hash,
                    instrument_metadata_digest,fee_schedule_digest,balance_snapshot_digest,
                    clock_sync_observation_digest,fee_tier,fee_evidence_class,maker_fee_rate,taker_fee_rate,
                    fee_loss_treatment,balance_currency,available_balance,signed_timestamp_source,observed_skew_ms)
                SELECT ?,pilot_scope_id,?,'BALANCE_SNAPSHOT',observation_schema_version,?,
                       source_identity,source_schema_version,observed_at,recorded_at,recorder_identity,
                       observation_payload_hash,instrument_metadata_digest,fee_schedule_digest,
                       balance_snapshot_digest,clock_sync_observation_digest,fee_tier,fee_evidence_class,
                       maker_fee_rate,taker_fee_rate,fee_loss_treatment,balance_currency,available_balance,
                       signed_timestamp_source,observed_skew_ms
                FROM pilot_prerequisite_observations WHERE observation_id=?
                """, UUID.randomUUID(), UUID.randomUUID(), "incomplete-" + UUID.randomUUID(),
                source.balanceSnapshot().id())));
        assertEquals(4, jdbc.queryForObject(
                "SELECT count(*) FROM pilot_prerequisite_observations WHERE pilot_scope_id=?",
                Integer.class, source.pilotScopeId()));
    }

    private static void assertFutureAndSkewObservationsRejected(
            JdbcTemplate jdbc,
            PilotObservationSet source
    ) {
        assertSqlState23514(() -> jdbc.update("""
                INSERT INTO pilot_prerequisite_observations(
                    observation_id,pilot_scope_id,observation_set_id,observation_type,
                    observation_schema_version,observation_identity,source_identity,source_schema_version,
                    observed_at,recorded_at,recorder_identity,observation_payload_hash,
                    instrument_metadata_digest,fee_schedule_digest,balance_snapshot_digest,
                    clock_sync_observation_digest,fee_tier,fee_evidence_class,maker_fee_rate,taker_fee_rate,
                    fee_loss_treatment,balance_currency,available_balance,signed_timestamp_source,observed_skew_ms)
                SELECT ?,pilot_scope_id,?,'BALANCE_SNAPSHOT',observation_schema_version,?,
                       source_identity,source_schema_version,transaction_timestamp()+INTERVAL '2 seconds',
                       transaction_timestamp(),recorder_identity,observation_payload_hash,
                       instrument_metadata_digest,fee_schedule_digest,balance_snapshot_digest,
                       clock_sync_observation_digest,fee_tier,fee_evidence_class,maker_fee_rate,taker_fee_rate,
                       fee_loss_treatment,balance_currency,available_balance,signed_timestamp_source,observed_skew_ms
                FROM pilot_prerequisite_observations WHERE observation_id=?
                """, UUID.randomUUID(), UUID.randomUUID(), "future-" + UUID.randomUUID(),
                source.balanceSnapshot().id()));
        assertSqlState23514(() -> jdbc.update("""
                INSERT INTO pilot_prerequisite_observations(
                    observation_id,pilot_scope_id,observation_set_id,observation_type,
                    observation_schema_version,observation_identity,source_identity,source_schema_version,
                    observed_at,recorded_at,recorder_identity,observation_payload_hash,
                    instrument_metadata_digest,fee_schedule_digest,balance_snapshot_digest,
                    clock_sync_observation_digest,fee_tier,fee_evidence_class,maker_fee_rate,taker_fee_rate,
                    fee_loss_treatment,balance_currency,available_balance,signed_timestamp_source,observed_skew_ms)
                SELECT ?,pilot_scope_id,?,'CLOCK_SYNC',observation_schema_version,?,
                       source_identity,source_schema_version,observed_at,recorded_at,recorder_identity,
                       observation_payload_hash,instrument_metadata_digest,fee_schedule_digest,
                       balance_snapshot_digest,clock_sync_observation_digest,fee_tier,fee_evidence_class,
                       maker_fee_rate,taker_fee_rate,fee_loss_treatment,balance_currency,available_balance,
                       signed_timestamp_source,501
                FROM pilot_prerequisite_observations WHERE observation_id=?
                """, UUID.randomUUID(), UUID.randomUUID(), "skew-" + UUID.randomUUID(),
                source.clockSync().id()));
    }

    private static void assertPilotApprovalCompatibility(
            JdbcTemplate jdbc,
            JdbcLiveControlRepository liveRepository,
            JdbcPilotScopeRepository pilotRepository,
            TransactionTemplate transactions,
            LiveSession session,
            PilotScopeBinding scope,
            long creatorId,
            long approverId,
            Instant approvedAt
    ) {
        OperatorApproval legacy = new OperatorApproval(
                UUID.randomUUID(), session.id(), session.approvalScopeHash(), session.releaseDigest(),
                session.riskLimitSetDigest(), approverId, OperatorApproval.REQUIRED_ROLE,
                OperatorApproval.Decision.APPROVED, "legacy-cannot-authorize-pilot",
                approvedAt, approvedAt.plusSeconds(120));
        assertSqlState23514(() -> transactions.executeWithoutResult(
                status -> liveRepository.appendApproval(legacy)));

        OperatorApproval selfApproval = new OperatorApproval(
                UUID.randomUUID(), session.id(), OperatorApproval.PILOT_SCOPE_SCHEMA, scope.id(),
                scope.pilotScopeHash(), session.releaseDigest(), session.riskLimitSetDigest(), creatorId,
                OperatorApproval.REQUIRED_ROLE, OperatorApproval.Decision.APPROVED, "self-approval",
                approvedAt, approvedAt.plusSeconds(120));
        assertSqlState23514(() -> transactions.executeWithoutResult(
                status -> liveRepository.appendApproval(selfApproval)));

        OperatorApproval lateExpiry = new OperatorApproval(
                UUID.randomUUID(), session.id(), OperatorApproval.PILOT_SCOPE_SCHEMA, scope.id(),
                scope.pilotScopeHash(), session.releaseDigest(), session.riskLimitSetDigest(), approverId,
                OperatorApproval.REQUIRED_ROLE, OperatorApproval.Decision.APPROVED, "late-expiry",
                approvedAt, session.executionWindowEnd().plusSeconds(1));
        assertSqlState23514(() -> transactions.executeWithoutResult(
                status -> liveRepository.appendApproval(lateExpiry)));

        OperatorApproval futureDated = new OperatorApproval(
                UUID.randomUUID(), session.id(), OperatorApproval.PILOT_SCOPE_SCHEMA, scope.id(),
                scope.pilotScopeHash(), session.releaseDigest(), session.riskLimitSetDigest(), approverId,
                OperatorApproval.REQUIRED_ROLE, OperatorApproval.Decision.APPROVED, "future-dated",
                approvedAt.plusSeconds(60), approvedAt.plusSeconds(120));
        transactions.executeWithoutResult(status -> liveRepository.appendApproval(futureDated));
        assertTrue(pilotRepository.findValidPilotApproval(scope, approvedAt.plusSeconds(1)).isEmpty());

        OperatorApproval valid = new OperatorApproval(
                UUID.randomUUID(), session.id(), OperatorApproval.PILOT_SCOPE_SCHEMA, scope.id(),
                scope.pilotScopeHash(), session.releaseDigest(), session.riskLimitSetDigest(), approverId,
                OperatorApproval.REQUIRED_ROLE, OperatorApproval.Decision.APPROVED, "pilot-approved",
                approvedAt, approvedAt.plusSeconds(120));
        transactions.executeWithoutResult(status -> liveRepository.appendApproval(valid));
        assertEquals(valid.id(), pilotRepository.findValidPilotApproval(scope, approvedAt.plusSeconds(1))
                .orElseThrow().id());
        assertTrue(liveRepository.findValidApproval(session, approvedAt.plusSeconds(1)).isEmpty());
        assertEquals(2, jdbc.queryForObject("""
                SELECT count(*) FROM operator_approvals
                WHERE scope_schema_version='pilot-scope.v1' AND pilot_scope_id=?
                """, Integer.class, scope.id()));
    }

    private static LiveSessionEvent createdEventAt(LiveSession session, long actorId, Instant occurredAt) {
        return new LiveSessionEvent(
                UUID.randomUUID(), session.id(), 1, null, LiveSessionState.APPROVAL_PENDING,
                "CREATE", actorId, "pilot-create-request", "pilot-create-trace", "SESSION_CREATED",
                "pilot-create-session", "2".repeat(64), "{}", occurredAt);
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
        return url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema + ",public";
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
                SELECT count(*) FROM pg_trigger t
                JOIN pg_class c ON c.oid = t.tgrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = current_schema() AND NOT t.tgisinternal AND t.tgname IN (
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
                    approval_id,session_id,scope_schema_version,scope_hash,release_digest,risk_limit_set_digest,
                    approver_id,approver_role,decision,reason,approved_at,expires_at
                ) VALUES (?,?,'approval-scope.v1',?,?,?,?, 'LIVE_APPROVER','REJECTED','fixture',?,?)
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

    private static void assertExecutionIntentRuntime(
            JdbcTemplate jdbc,
            TransactionTemplate transactions,
            LiveSessionControlService sessionService,
            ExistingFixture existing,
            RiskLimitSet risk
    ) throws Exception {
        jdbc.update("""
                UPDATE live_sessions
                SET state='KILLED',version=version+1,updated_at=CURRENT_TIMESTAMP
                WHERE exchange_account_id=?
                  AND state NOT IN ('REJECTED','KILLED','FAILED','LIVE_RECONCILED')
                """, existing.exchangeAccountId());
        LiveSession runtimeSession = session(existing, risk, UUID.randomUUID(), NOW.plusSeconds(20));
        transactions.executeWithoutResult(status -> sessionService.createSession(
                new AuthenticatedLiveControlActor(existing.creatorId()), runtimeSession, risk,
                createdEvent(runtimeSession, existing.creatorId())));
        for (String state : List.of("APPROVED", "LIVE_WARMUP", "LIVE_ACTIVE")) {
            jdbc.update("UPDATE live_sessions SET state=?,version=version+1,updated_at=CURRENT_TIMESTAMP "
                    + "WHERE session_id=?", state, runtimeSession.id());
        }

        UUID intentId = UUID.randomUUID();
        ExecutionIntentDraft draft = ExecutionIntentCanonicalEncoder.place(
                intentId, runtimeSession.id(), "BTC-USDT", "BUY",
                decimal("1"), decimal("10"), "gatey3-order-" + intentId.toString().substring(0, 8));
        jdbc.update("""
                INSERT INTO orders(
                    order_id,account_id,venue,exchange_code,trade_env,symbol,client_order_id,
                    side,type,price,qty,status,trace_id
                ) VALUES (?,?,'OKX','OKX','LIVE','BTC-USDT',?,'BUY','LIMIT',10,1,'CREATED','gatey3-test')
                """, draft.localOrderId(), existing.legacyAccountId(), draft.clientOrderId());

        JdbcExecutionIntentRepository repository = new JdbcExecutionIntentRepository(
                jdbc, new DataSourceTransactionManager(jdbc.getDataSource()));
        LiveControlException engaged = assertThrows(
                LiveControlException.class, () -> repository.createOrGet(draft));
        assertEquals("GLOBAL_KILL_SWITCH_NOT_DISENGAGED", engaged.code());
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM execution_intents WHERE intent_id=?", Integer.class, intentId));
        jdbc.update("UPDATE kill_switch_states SET status='DISENGAGED',version=version+1," +
                "reason_code='GATEY3_TEST_ONLY',source='POSTGRES_TEST_FIXTURE',updated_at=CURRENT_TIMESTAMP," +
                "updated_by='gatey3-test',trace_id='gatey3-test' WHERE scope='GLOBAL_TRADING'");
        ExecutionIntent created = repository.createOrGet(draft);
        assertEquals(created, repository.createOrGet(draft));
        assertConcurrentPilotLeasePlaceBinding(
                jdbc, repository, runtimeSession, existing, intentId);
        ExecutionIntentDraft conflict = ExecutionIntentCanonicalEncoder.place(
                intentId, runtimeSession.id(), "BTC-USDT", "BUY", decimal("2"), decimal("10"),
                draft.localOrderId());
        LiveControlException conflictFailure = assertThrows(
                LiveControlException.class, () -> repository.createOrGet(conflict));
        assertEquals("IDEMPOTENCY_CONFLICT", conflictFailure.code());

        CountDownLatch createStart = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            List<Future<ExecutionIntent>> futures = java.util.stream.IntStream.range(0, 4)
                    .mapToObj(index -> executor.submit(() -> {
                        assertTrue(createStart.await(10, TimeUnit.SECONDS));
                        return repository.createOrGet(draft);
                    })).toList();
            createStart.countDown();
            for (Future<ExecutionIntent> future : futures) {
                assertEquals(intentId, future.get(10, TimeUnit.SECONDS).intentId());
            }
        }
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM execution_intents WHERE intent_id=?", Integer.class, intentId));

        CountDownLatch claimStart = new CountDownLatch(1);
        List<UUID> tokens = java.util.stream.IntStream.range(0, 4).mapToObj(ignored -> UUID.randomUUID()).toList();
        List<Optional<ExecutionIntent>> claims = new java.util.ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            List<Future<Optional<ExecutionIntent>>> futures = java.util.stream.IntStream.range(0, 4)
                    .mapToObj(index -> executor.submit(() -> {
                        assertTrue(claimStart.await(10, TimeUnit.SECONDS));
                        return repository.claim(intentId, "worker-" + index, tokens.get(index), Duration.ofMillis(500));
                    })).toList();
            claimStart.countDown();
            for (Future<Optional<ExecutionIntent>> future : futures) {
                claims.add(future.get(10, TimeUnit.SECONDS));
            }
        }
        assertEquals(1, claims.stream().filter(Optional::isPresent).count());
        ExecutionIntent claimed = claims.stream().flatMap(Optional::stream).findFirst().orElseThrow();
        jdbc.queryForObject("SELECT pg_sleep(0.55)", Object.class);
        UUID reclaimedToken = UUID.randomUUID();
        ExecutionIntent reclaimed = repository.claim(
                intentId, "worker-reclaimed", reclaimedToken, Duration.ofMinutes(1)).orElseThrow();
        assertEquals(ExecutionIntentState.CLAIMED, reclaimed.state());
        assertNotEquals(claimed.claimToken(), reclaimed.claimToken());

        jdbc.update("UPDATE kill_switch_states SET status='ENGAGED',version=version+1," +
                "reason_code='GATEY3_TEST_ENGAGE',source='POSTGRES_TEST_FIXTURE',updated_at=CURRENT_TIMESTAMP," +
                "updated_by='gatey3-test',trace_id='gatey3-test' WHERE scope='GLOBAL_TRADING'");
        LiveControlException sendBlocked = assertThrows(LiveControlException.class,
                () -> repository.markSendStarted(intentId, reclaimed.version(), reclaimedToken));
        assertEquals("GLOBAL_KILL_SWITCH_NOT_DISENGAGED", sendBlocked.code());
        assertEquals(ExecutionIntentState.CLAIMED, repository.find(intentId).orElseThrow().state());
        jdbc.update("UPDATE kill_switch_states SET status='DISENGAGED',version=version+1," +
                "reason_code='GATEY3_TEST_ONLY',source='POSTGRES_TEST_FIXTURE',updated_at=CURRENT_TIMESTAMP," +
                "updated_by='gatey3-test',trace_id='gatey3-test' WHERE scope='GLOBAL_TRADING'");
        ExecutionIntent sendStarted = repository.markSendStarted(
                intentId, reclaimed.version(), reclaimedToken).orElseThrow();
        assertEquals(ExecutionIntentState.SEND_STARTED, sendStarted.state());
        assertTrue(repository.markSendStarted(intentId, reclaimed.version(), reclaimedToken).isEmpty());
        assertTrue(repository.claim(
                intentId, "worker-forbidden", UUID.randomUUID(), Duration.ofMinutes(1)).isEmpty());
        assertEquals(sendStarted.sendStartedAt(), repository.find(intentId).orElseThrow().sendStartedAt());

        jdbc.update("UPDATE exchange_accounts SET legacy_account_id=NULL WHERE exchange_account_id=?",
                existing.exchangeAccountId());
        UUID bridgeIntentId = UUID.randomUUID();
        ExecutionIntentDraft bridgeDraft = ExecutionIntentCanonicalEncoder.place(
                bridgeIntentId, runtimeSession.id(), "BTC-USDT", "BUY", decimal("1"), decimal("10"),
                draft.localOrderId());
        LiveControlException bridgeFailure = assertThrows(
                LiveControlException.class, () -> repository.createOrGet(bridgeDraft));
        assertEquals("ACCOUNT_IDENTITY_BRIDGE_UNVERIFIED", bridgeFailure.code());
        jdbc.update("UPDATE exchange_accounts SET legacy_account_id=? WHERE exchange_account_id=?",
                existing.legacyAccountId(), existing.exchangeAccountId());

        long mismatchedAccountId = jdbc.queryForObject(
                "INSERT INTO accounts(account_code,venue,status) VALUES (?, 'OKX', 'ACTIVE') RETURNING account_id",
                Long.class, "gatey3-mismatch-" + intentId);
        ExecutionIntentDraft mismatchDraft = insertPlaceOrder(
                jdbc, runtimeSession.id(), mismatchedAccountId, "gatey3-mismatch-");
        assertBridgeRejected(jdbc, repository, mismatchDraft);

        ExecutionIntentDraft ownerDraft = insertPlaceOrder(
                jdbc, runtimeSession.id(), existing.legacyAccountId(), "gatey3-owner-");
        jdbc.update("UPDATE exchange_accounts SET owner_user_id=? WHERE exchange_account_id=?",
                existing.approverId(), existing.exchangeAccountId());
        assertBridgeRejected(jdbc, repository, ownerDraft);
        jdbc.update("UPDATE exchange_accounts SET owner_user_id=? WHERE exchange_account_id=?",
                existing.creatorId(), existing.exchangeAccountId());

        ExecutionIntentDraft missingOrderDraft = ExecutionIntentCanonicalEncoder.place(
                UUID.randomUUID(), runtimeSession.id(), "BTC-USDT", "BUY", decimal("1"), decimal("10"),
                "gatey3-missing-" + UUID.randomUUID().toString().substring(0, 8));
        assertBridgeRejected(jdbc, repository, missingOrderDraft);

        ExecutionIntentDraft forged = new ExecutionIntentDraft(
                draft.intentId(), draft.sessionId(), draft.action(), draft.symbol(), draft.side(), draft.orderType(),
                draft.quantity(), draft.limitPrice(), draft.localOrderId(), draft.clientOrderId(), "f".repeat(64));
        LiveControlException forgedFailure = assertThrows(
                LiveControlException.class, () -> repository.createOrGet(forged));
        assertEquals("INTENT_CANONICAL_PAYLOAD_INVALID", forgedFailure.code());

        ExecutionIntentDraft fieldMismatch = insertPlaceOrder(
                jdbc, runtimeSession.id(), existing.legacyAccountId(), "gatey3-field-mismatch-");
        jdbc.update("""
                INSERT INTO execution_intents(
                    intent_id,session_id,sequence,action,symbol,side,order_type,quantity,limit_price,
                    payload_hash_schema_version,payload_hash,client_order_id,local_order_id,state
                ) VALUES (?, ?, 900001, 'PLACE', 'BTC-USDT', 'SELL', 'LIMIT', 1, 10,
                          'execution-intent-payload.v1', ?, ?, ?, 'CREATED')
                """, fieldMismatch.intentId(), fieldMismatch.sessionId(), fieldMismatch.payloadHash(),
                fieldMismatch.clientOrderId(), fieldMismatch.localOrderId());
        LiveControlException fieldMismatchFailure = assertThrows(
                LiveControlException.class, () -> repository.createOrGet(fieldMismatch));
        assertEquals("IDEMPOTENCY_CONFLICT", fieldMismatchFailure.code());

        ExecutionIntent unknown = repository.markAmbiguousForRecovery(
                intentId, sendStarted.version(), reclaimedToken).orElseThrow();
        assertEquals(ExecutionIntentState.UNKNOWN, unknown.state());

        ExecutionIntentDraft cancelDraft = ExecutionIntentCanonicalEncoder.cancel(
                UUID.randomUUID(), runtimeSession.id(), "BTC-USDT", draft.localOrderId(), draft.clientOrderId());
        LiveControlException unresolvedCancel = assertThrows(
                LiveControlException.class, () -> repository.createOrGet(cancelDraft));
        assertEquals("CANCEL_PLACE_RECONCILIATION_REQUIRED", unresolvedCancel.code());
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM execution_intents WHERE local_order_id=?", Integer.class, draft.localOrderId()));

        UUID receiptId = UUID.randomUUID();
        CountDownLatch receiptStart = new CountDownLatch(1);
        List<Object> receiptResults = new java.util.ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Object>> futures = java.util.stream.IntStream.range(0, 2)
                    .mapToObj(index -> executor.submit(() -> {
                        assertTrue(receiptStart.await(10, TimeUnit.SECONDS));
                        ExecutionReceiptDraft receipt = ExecutionReceiptCanonicalEncoder.draft(
                                index == 0 ? receiptId : UUID.randomUUID(), intentId,
                                ExecutionReceiptOutcome.QUERY_CONFIRMED, "query-" + index,
                                "exchange-order", "FAKE_RECONCILIATION", "CONFIRMED", NOW.plusSeconds(index));
                        try {
                            return repository.appendReceiptAndTransition(
                                    intentId, unknown.version(), reclaimedToken, receipt,
                                    ExecutionIntentState.RECONCILED);
                        } catch (RuntimeException ex) {
                            return ex;
                        }
                    })).toList();
            receiptStart.countDown();
            for (Future<Object> future : futures) {
                receiptResults.add(future.get(10, TimeUnit.SECONDS));
            }
        }
        assertEquals(1, receiptResults.stream().filter(ExecutionIntent.class::isInstance).count());
        assertEquals(1, receiptResults.stream().filter(RuntimeException.class::isInstance).count());
        assertEquals(List.of(1), jdbc.queryForList(
                "SELECT attempt_no FROM execution_receipts WHERE intent_id=? ORDER BY attempt_no",
                Integer.class, intentId));
        ExecutionIntent cancel = repository.createOrGet(cancelDraft);
        assertEquals("CANCEL", cancel.action().name());
        assertEquals(draft.clientOrderId(), cancel.clientOrderId());
        assertEquals(null, cancel.side());
        assertEquals(2, jdbc.queryForObject(
                "SELECT count(*) FROM execution_intents WHERE local_order_id=?", Integer.class, draft.localOrderId()));
        UUID existingReceiptId = jdbc.queryForObject(
                "SELECT receipt_id FROM execution_receipts WHERE intent_id=?", UUID.class, intentId);

        UUID rollbackIntentId = UUID.randomUUID();
        String rollbackOrderId = "gatey3-rollback-" + rollbackIntentId.toString().substring(0, 8);
        ExecutionIntentDraft rollbackDraft = ExecutionIntentCanonicalEncoder.place(
                rollbackIntentId, runtimeSession.id(), "BTC-USDT", "BUY",
                decimal("1"), decimal("10"), rollbackOrderId);
        jdbc.update("""
                INSERT INTO orders(
                    order_id,account_id,venue,exchange_code,trade_env,symbol,client_order_id,
                    side,type,price,qty,status,trace_id
                ) VALUES (?,?,'OKX','OKX','LIVE','BTC-USDT',?,'BUY','LIMIT',10,1,'CREATED','gatey3-test')
                """, rollbackOrderId, existing.legacyAccountId(), rollbackDraft.clientOrderId());
        repository.createOrGet(rollbackDraft);
        UUID rollbackToken = UUID.randomUUID();
        ExecutionIntent rollbackClaimed = repository.claim(
                rollbackIntentId, "rollback-worker", rollbackToken, Duration.ofMinutes(1)).orElseThrow();
        ExecutionIntent rollbackSendStarted = repository.markSendStarted(
                rollbackIntentId, rollbackClaimed.version(), rollbackToken).orElseThrow();
        ExecutionIntent rollbackUnknown = repository.markAmbiguousForRecovery(
                rollbackIntentId, rollbackSendStarted.version(), rollbackToken).orElseThrow();
        long rollbackVersion = rollbackUnknown.version();
        ExecutionReceiptDraft duplicateReceipt = ExecutionReceiptCanonicalEncoder.draft(
                existingReceiptId, rollbackIntentId, ExecutionReceiptOutcome.QUERY_CONFIRMED,
                "rollback-query", "rollback-exchange", "FAKE_RECONCILIATION", "CONFIRMED", NOW);
        assertThrows(DataIntegrityViolationException.class, () -> repository.appendReceiptAndTransition(
                rollbackIntentId, rollbackVersion, rollbackToken, duplicateReceipt,
                ExecutionIntentState.RECONCILED));
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM execution_receipts WHERE intent_id=?", Integer.class, rollbackIntentId));
        ExecutionIntent afterRollback = repository.find(rollbackIntentId).orElseThrow();
        assertEquals(ExecutionIntentState.UNKNOWN, afterRollback.state());
        assertEquals(rollbackVersion, afterRollback.version());
    }

    private static void assertConcurrentPilotLeasePlaceBinding(
            JdbcTemplate jdbc,
            JdbcExecutionIntentRepository repository,
            LiveSession session,
            ExistingFixture existing,
            UUID firstIntentId
    ) throws Exception {
        ExecutionIntentDraft second = insertPlaceOrder(
                jdbc, session.id(), existing.legacyAccountId(), "gatey-pilot-double-place-");
        repository.createOrGet(second);
        UUID leaseId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO pilot_execution_leases(
                    lease_id,live_session_id,binding_id,binding_digest,status,max_notional,
                    valid_from,expires_at,created_by,version,created_at,updated_at
                ) VALUES (?,?,?,?,'ACTIVE',100,CURRENT_TIMESTAMP,
                          CURRENT_TIMESTAMP+INTERVAL '2 minutes',?,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, leaseId, session.id(), UUID.randomUUID(), DIGEST_A, existing.creatorId());
        CountDownLatch start = new CountDownLatch(1);
        List<Object> results = new java.util.ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Object>> futures = List.of(firstIntentId, second.intentId()).stream()
                    .map(intentId -> executor.submit(() -> {
                        assertTrue(start.await(10, TimeUnit.SECONDS));
                        try {
                            return (Object) jdbc.update("""
                                    INSERT INTO pilot_execution_lease_intents(lease_id,intent_id,action)
                                    VALUES (?,?,'PLACE')
                                    """, leaseId, intentId);
                        } catch (RuntimeException failure) {
                            return (Object) failure;
                        }
                    })).toList();
            start.countDown();
            for (Future<Object> future : futures) results.add(future.get(10, TimeUnit.SECONDS));
        }
        assertEquals(1, results.stream().filter(Integer.class::isInstance).count());
        assertEquals(1, results.stream().filter(RuntimeException.class::isInstance).count());
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM pilot_execution_lease_intents WHERE lease_id=? AND action='PLACE'",
                Integer.class, leaseId));
        jdbc.update("""
                UPDATE pilot_execution_leases
                SET status='FAILED',closed_at=CURRENT_TIMESTAMP,version=version+1,updated_at=CURRENT_TIMESTAMP
                WHERE lease_id=?
                """, leaseId);
    }

    private static ExecutionIntentDraft insertPlaceOrder(
            JdbcTemplate jdbc,
            UUID sessionId,
            long accountId,
            String prefix
    ) {
        UUID intentId = UUID.randomUUID();
        String orderId = prefix + intentId.toString().substring(0, 8);
        ExecutionIntentDraft draft = ExecutionIntentCanonicalEncoder.place(
                intentId, sessionId, "BTC-USDT", "BUY", decimal("1"), decimal("10"), orderId);
        jdbc.update("""
                INSERT INTO orders(
                    order_id,account_id,venue,exchange_code,trade_env,symbol,client_order_id,
                    side,type,price,qty,status,trace_id
                ) VALUES (?,?,'OKX','OKX','LIVE','BTC-USDT',?,'BUY','LIMIT',10,1,'CREATED','gatey3-test')
                """, orderId, accountId, draft.clientOrderId());
        return draft;
    }

    private static void assertBridgeRejected(
            JdbcTemplate jdbc,
            JdbcExecutionIntentRepository repository,
            ExecutionIntentDraft draft
    ) {
        LiveControlException failure = assertThrows(
                LiveControlException.class, () -> repository.createOrGet(draft));
        assertEquals("ACCOUNT_IDENTITY_BRIDGE_UNVERIFIED", failure.code());
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM execution_intents WHERE intent_id=?", Integer.class, draft.intentId()));
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
        RuntimeException failure = assertThrows(RuntimeException.class, action::run);
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

    private record HistoricalV39(LiveSession session, UUID approvalId, String fingerprint) {
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
