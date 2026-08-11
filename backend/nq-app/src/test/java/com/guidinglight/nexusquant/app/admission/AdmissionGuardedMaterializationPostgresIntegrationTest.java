package com.guidinglight.nexusquant.app.admission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyValidationOverviewQueryService;
import com.guidinglight.nexusquant.strategy.domain.port.ShadowRunFactRepository;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyValidationOverviewFacts;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunAuthorizationBoundary;
import com.guidinglight.nexusquant.strategy.infra.jdbc.JdbcAdmissionMutationCoordinator;
import com.guidinglight.nexusquant.strategy.infra.jdbc.JdbcShadowRunFactRepository;
import com.guidinglight.nexusquant.strategy.infra.jdbc.JdbcShadowRunIllegalTransitionAuditWriter;
import com.guidinglight.nexusquant.strategy.infra.jdbc.JdbcStrategyReleaseAdmissionPreviewFactsRepository;
import com.guidinglight.nexusquant.strategy.infra.jdbc.JdbcStrategyReleaseAdmissionStateRepository;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.AdmissionGuard;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.AdmissionGuardDecisionService;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.AdmissionGuardFingerprinter;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.AdmissionStaleException;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.ReleaseToShadowAdmissionService;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.ShadowRunCreationPlan;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.ShadowRunMaterializationRejectedException;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.ShadowRunMaterializationResult;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.ShadowRunMaterializationWriter;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseAdmissionPreviewFacts;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseAdmissionPreviewFactsRepository;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseAdmissionPreviewService;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseAdmissionState;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseAdmissionStateRepository;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseArtifactBindingResolver;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseProductionService;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseProvenanceFacts;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.VerifiedStrategyReleaseIdentity;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactManifest;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactVerificationPolicy;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyArtifactVerificationResult;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.StrategyReleaseManifestFingerprinter;
import com.guidinglight.nexusquant.strategy.strategyrelease.artifact.TrustedRootStrategyArtifactVerifier;
import com.guidinglight.nexusquant.strategy.strategyrelease.domain.StrategyRelease;
import com.guidinglight.nexusquant.strategy.strategyrelease.domain.StrategyReleaseStatus;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** GateX-5B guarded materialization 的真实 localhost disposable PostgreSQL 17 回归。 */
class AdmissionGuardedMaterializationPostgresIntegrationTest {

    private static final String REQUIRED_PROPERTY = "nq.admission-guard.postgres.required";
    private static final String URL_PROPERTY = "nq.admission-guard.postgres.url";
    private static final String USER_PROPERTY = "nq.admission-guard.postgres.user";
    private static final String PASSWORD_PROPERTY = "nq.admission-guard.postgres.password";
    private static final String DIGEST = "a".repeat(64);
    private static final Instant START = Instant.parse("2026-08-11T00:00:00Z");

    @Test
    @Timeout(30)
    void issuanceShouldFirstBindRejectR0R1RaceIdentityMismatchAndUnknownSchema() throws Exception {
        PostgresConfig config = requireLocalDisposableConfig();
        String schema = randomSchema("issuance");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            migrate(config, schema);
            JdbcTemplate jdbc = jdbc(config, schema);
            Fixture fixture = seedEligibleFixture(jdbc, "issuance");
            Harness harness = harness(jdbc, fixture, false);
            StrategyReleaseAdmissionPreviewService service = harness.previewService(harness.factsRepository());

            var issued = service.evaluate(fixture.publishId(), "trace-first-bind").orElseThrow();
            assertNotNull(issued.guard());
            assertTrue(harness.stateRepository().loadByPublishRecordId(fixture.publishId()).identityBound());
            assertEquals(AdmissionGuard.SUPPORTED_GUARD_SCHEMA_VERSION, issued.guard().guardSchemaVersion());

            StrategyRelease mismatchedRelease = release(fixture, "d".repeat(64));
            StrategyReleaseAdmissionPreviewService mismatched = previewService(
                    harness,
                    harness.factsRepository(),
                    mismatchedRelease
            );
            assertThrows(AdmissionStaleException.class, () -> mismatched.evaluate(
                    fixture.publishId(),
                    "trace-mismatch"
            ));

            CountDownLatch factsLoaded = new CountDownLatch(1);
            CountDownLatch mutationCommitted = new CountDownLatch(1);
            StrategyReleaseAdmissionPreviewFactsRepository blockingFacts = publishId -> {
                StrategyReleaseAdmissionPreviewFacts facts = harness.factsRepository().loadByPublishRecordId(publishId);
                factsLoaded.countDown();
                await(mutationCommitted);
                return facts;
            };
            StrategyReleaseAdmissionPreviewService racing = harness.previewService(blockingFacts);
            Future<?> issuance = executor.submit(() -> racing.evaluate(fixture.publishId(), "trace-r0-r1"));
            await(factsLoaded);
            jdbc.update(
                    "UPDATE backtest_publish_records SET publish_name = ? WHERE publish_record_id = ?",
                    "r0-r1-mutated",
                    fixture.publishId()
            );
            mutationCommitted.countDown();
            ExecutionException raceFailure = assertThrows(ExecutionException.class, issuance::get);
            assertInstanceOf(AdmissionStaleException.class, raceFailure.getCause());

            jdbc.execute("ALTER TABLE strategy_release_admission_state DROP CONSTRAINT chk_strategy_release_guard_schema_version");
            jdbc.update(
                    "UPDATE strategy_release_admission_state SET guard_schema_version = 2 WHERE publish_record_id = ?",
                    fixture.publishId()
            );
            assertThrows(AdmissionStaleException.class, () -> service.evaluate(
                    fixture.publishId(),
                    "trace-unknown-schema"
            ));
        } finally {
            executor.shutdownNow();
            dropSchema(config, schema);
        }
    }

    @Test
    @Timeout(30)
    void everyAdmissionMutationShouldMakeOldGuardStaleWithoutWrites() {
        PostgresConfig config = requireLocalDisposableConfig();
        String schema = randomSchema("races");
        try {
            migrate(config, schema);
            JdbcTemplate jdbc = jdbc(config, schema);
            Fixture fixture = seedEligibleFixture(jdbc, "races");
            Harness harness = harness(jdbc, fixture, true);

            assertStaleAfter(harness, "validation", () -> jdbc.update(
                    "UPDATE backtest_eval_reports SET report_json = '{\"validationChanged\":true}'::jsonb "
                            + "WHERE eval_report_id = ?",
                    fixture.evaluationId()
            ));
            assertStaleAfter(harness, "paper-phantom", () -> insertPaper(
                    jdbc,
                    fixture,
                    "paper-phantom-" + shortId(),
                    START.plusSeconds(10)
            ));
            assertStaleAfter(harness, "publish", () -> jdbc.update(
                    "UPDATE backtest_publish_records SET publish_name = ? WHERE publish_record_id = ?",
                    "publish-mutated",
                    fixture.publishId()
            ));
            assertStaleAfter(harness, "evaluation", () -> jdbc.update(
                    "UPDATE backtest_eval_reports SET updated_at = ? WHERE eval_report_id = ?",
                    Timestamp.from(START.plusSeconds(20)),
                    fixture.evaluationId()
            ));

            UUID evidenceShadowId = UUID.randomUUID();
            assertStaleAfter(harness, "shadow-evidence", () -> insertShadowEvidence(
                    jdbc,
                    fixture,
                    evidenceShadowId,
                    "PRECHECKING",
                    START.plusSeconds(30)
            ));
            jdbc.update(
                    "INSERT INTO shadow_consistency_reports "
                            + "(id, shadow_run_id, comparison_status, generated_at, trace_id) "
                            + "VALUES (?, ?, 'CONSISTENT', ?, 'gatex5b-test')",
                    UUID.randomUUID(),
                    evidenceShadowId,
                    Timestamp.from(START.plusSeconds(31))
            );
            assertStaleAfter(harness, "consistency", () -> jdbc.update(
                    "UPDATE shadow_consistency_reports SET generated_at = ? WHERE shadow_run_id = ?",
                    Timestamp.from(START.plusSeconds(32)),
                    evidenceShadowId
            ));

            jdbc.update(
                    "UPDATE backtest_eval_reports SET evaluation_status = 'FAILED' WHERE eval_report_id = ?",
                    fixture.evaluationId()
            );
            StrategyReleaseAdmissionState blockedState = harness.stateRepository()
                    .loadByPublishRecordId(fixture.publishId());
            StrategyReleaseAdmissionPreviewFacts blockedFacts = harness.factsRepository()
                    .loadByPublishRecordId(fixture.publishId());
            AdmissionGuard blockedGuard = harness.fingerprinter().issue(blockedState, blockedFacts, START.plusSeconds(40));
            long revisionBefore = blockedState.admissionRevision();
            long runsBefore = count(jdbc, "shadow_runs");
            long eventsBefore = count(jdbc, "shadow_run_events");
            assertThrows(ShadowRunMaterializationRejectedException.class, () -> harness.writer().materialize(
                    harness.plan("blocked-command"),
                    blockedGuard,
                    41L
            ));
            assertEquals(runsBefore, count(jdbc, "shadow_runs"));
            assertEquals(eventsBefore, count(jdbc, "shadow_run_events"));
            assertEquals(revisionBefore, harness.stateRepository()
                    .loadByPublishRecordId(fixture.publishId()).admissionRevision());
        } finally {
            dropSchema(config, schema);
        }
    }

    @Test
    @Timeout(30)
    void concurrencyReplayRerunCreatedExclusionAndRollbackShouldBeAtomic() throws Exception {
        PostgresConfig config = requireLocalDisposableConfig();
        String schema = randomSchema("writer");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            migrate(config, schema);
            JdbcTemplate jdbc = jdbc(config, schema);
            Fixture fixture = seedEligibleFixture(jdbc, "writer");
            Harness harness = harness(jdbc, fixture, true);

            AdmissionGuard oldGuard = harness.guard();
            ShadowRunCreationPlan sameCommand = harness.plan("same-command");
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Future<Object> first = executor.submit(() -> concurrentWrite(harness, sameCommand, oldGuard, ready, start));
            Future<Object> second = executor.submit(() -> concurrentWrite(harness, sameCommand, oldGuard, ready, start));
            await(ready);
            start.countDown();
            List<Object> results = List.of(first.get(), second.get());
            assertEquals(1, results.stream().filter(ShadowRunMaterializationResult.class::isInstance).count());
            assertEquals(1, results.stream().filter(AdmissionStaleException.class::isInstance).count());

            ShadowRunMaterializationResult created = results.stream()
                    .filter(ShadowRunMaterializationResult.class::isInstance)
                    .map(ShadowRunMaterializationResult.class::cast)
                    .findFirst()
                    .orElseThrow();
            ShadowRunMaterializationResult replay = harness.writer().materialize(
                    sameCommand,
                    harness.guard(),
                    41L
            );
            assertEquals(created.shadowRunId(), replay.shadowRunId());
            assertTrue(replay.idempotentReplay());
            assertEquals(1L, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM shadow_run_events WHERE shadow_run_id = ? AND event_type = 'CREATED'",
                    Long.class,
                    created.shadowRunId()
            ));
            assertEquals(null, harness.factsRepository()
                    .loadByPublishRecordId(fixture.publishId()).latestShadowEvidenceIdentity());

            ShadowRunCreationPlan differentA = harness.plan("different-command-a");
            ShadowRunCreationPlan differentB = harness.plan("different-command-b");
            AdmissionGuard differentOldGuard = harness.guard();
            CountDownLatch differentReady = new CountDownLatch(2);
            CountDownLatch differentStart = new CountDownLatch(1);
            Future<Object> differentFirst = executor.submit(
                    () -> concurrentWrite(harness, differentA, differentOldGuard, differentReady, differentStart)
            );
            Future<Object> differentSecond = executor.submit(
                    () -> concurrentWrite(harness, differentB, differentOldGuard, differentReady, differentStart)
            );
            await(differentReady);
            differentStart.countDown();
            Object differentAResult = differentFirst.get();
            Object differentBResult = differentSecond.get();
            List<Object> differentResults = List.of(differentAResult, differentBResult);
            assertEquals(1, differentResults.stream().filter(ShadowRunMaterializationResult.class::isInstance).count());
            assertEquals(1, differentResults.stream().filter(AdmissionStaleException.class::isInstance).count());
            ShadowRunCreationPlan losingPlan = differentAResult instanceof AdmissionStaleException
                    ? differentA
                    : differentB;
            ShadowRunMaterializationResult differentWinner = differentResults.stream()
                    .filter(ShadowRunMaterializationResult.class::isInstance)
                    .map(ShadowRunMaterializationResult.class::cast)
                    .findFirst()
                    .orElseThrow();
            ShadowRunMaterializationResult legitimateRerun = harness.writer().materialize(
                    losingPlan,
                    harness.guard(),
                    41L
            );
            assertNotEquals(differentWinner.shadowRunId(), legitimateRerun.shadowRunId());

            ShadowRunCreationPlan rollbackPlan = harness.plan("rollback-command");
            long revisionBefore = harness.stateRepository().loadByPublishRecordId(fixture.publishId()).admissionRevision();
            long runsBefore = count(jdbc, "shadow_runs");
            long eventsBefore = count(jdbc, "shadow_run_events");
            ShadowRunFactRepository failingRepository = failingAuditRepository(harness.repository());
            assertThrows(IllegalStateException.class, () -> harness.writerFor(failingRepository).materialize(
                    rollbackPlan,
                    harness.guard(),
                    41L
            ));
            assertEquals(runsBefore, count(jdbc, "shadow_runs"));
            assertEquals(eventsBefore, count(jdbc, "shadow_run_events"));
            assertEquals(revisionBefore, harness.stateRepository()
                    .loadByPublishRecordId(fixture.publishId()).admissionRevision());
        } finally {
            executor.shutdownNow();
            dropSchema(config, schema);
        }
    }

    private static void assertStaleAfter(Harness harness, String command, Runnable mutation) {
        AdmissionGuard guard = harness.guard();
        ShadowRunCreationPlan plan = harness.plan(command);
        mutation.run();
        long runsAfterMutation = count(harness.jdbc(), "shadow_runs");
        long eventsAfterMutation = count(harness.jdbc(), "shadow_run_events");
        long revisionAfterMutation = harness.stateRepository()
                .loadByPublishRecordId(harness.fixture().publishId()).admissionRevision();

        assertThrows(AdmissionStaleException.class, () -> harness.writer().materialize(plan, guard, 41L));
        assertEquals(runsAfterMutation, count(harness.jdbc(), "shadow_runs"));
        assertEquals(eventsAfterMutation, count(harness.jdbc(), "shadow_run_events"));
        assertEquals(revisionAfterMutation, harness.stateRepository()
                .loadByPublishRecordId(harness.fixture().publishId()).admissionRevision());
    }

    private static Object concurrentWrite(
            Harness harness,
            ShadowRunCreationPlan plan,
            AdmissionGuard guard,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        ready.countDown();
        await(start);
        try {
            return harness.writer().materialize(plan, guard, 41L);
        } catch (AdmissionStaleException exception) {
            return exception;
        }
    }

    private static Harness harness(JdbcTemplate jdbc, Fixture fixture, boolean bindIdentity) {
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(jdbc.getDataSource());
        JdbcAdmissionMutationCoordinator coordinator = new JdbcAdmissionMutationCoordinator(jdbc, transactionManager, 256);
        JdbcStrategyReleaseAdmissionStateRepository stateRepository =
                new JdbcStrategyReleaseAdmissionStateRepository(jdbc, coordinator);
        JdbcStrategyReleaseAdmissionPreviewFactsRepository factsRepository =
                new JdbcStrategyReleaseAdmissionPreviewFactsRepository(jdbc);
        AdmissionGuardDecisionService decisionService = decisionService();
        AdmissionGuardFingerprinter fingerprinter = new AdmissionGuardFingerprinter();
        ObjectMapper objectMapper = new ObjectMapper();
        JdbcShadowRunFactRepository repository = new JdbcShadowRunFactRepository(
                jdbc,
                objectMapper,
                new JdbcShadowRunIllegalTransitionAuditWriter(jdbc, objectMapper, transactionManager),
                coordinator
        );
        Harness harness = new Harness(
                jdbc,
                fixture,
                coordinator,
                stateRepository,
                factsRepository,
                decisionService,
                fingerprinter,
                objectMapper,
                repository
        );
        if (bindIdentity) {
            stateRepository.bindVerifiedReleaseIdentity(VerifiedStrategyReleaseIdentity.fromVerifiedRelease(
                    release(fixture, DIGEST),
                    new StrategyReleaseManifestFingerprinter()
            ));
        }
        return harness;
    }

    private static StrategyReleaseAdmissionPreviewService previewService(
            Harness harness,
            StrategyReleaseAdmissionPreviewFactsRepository factsRepository,
            StrategyRelease release
    ) {
        return new StrategyReleaseAdmissionPreviewService(
                new StubReleaseProductionService(release),
                factsRepository,
                validationService(),
                new ReleaseToShadowAdmissionService(),
                harness.stateRepository(),
                new StrategyReleaseManifestFingerprinter(),
                harness.fingerprinter(),
                harness.decisionService()
        );
    }

    private static AdmissionGuardDecisionService decisionService() {
        return new AdmissionGuardDecisionService(validationService());
    }

    private static StrategyValidationOverviewQueryService validationService() {
        return new StrategyValidationOverviewQueryService(() -> new StrategyValidationOverviewFacts(
                0, 0, 0, 0, 0, 0, Optional.empty()
        ));
    }

    private static StrategyRelease release(Fixture fixture, String digest) {
        StrategyArtifactManifest manifest = new StrategyArtifactManifest(
                StrategyArtifactManifest.SUPPORTED_SCHEMA_VERSION,
                fixture.strategyVersionId(),
                fixture.datasetId(),
                fixture.evaluationId(),
                List.of(new StrategyArtifactManifest.ArtifactFile(
                        "model",
                        "artifacts/model.bin",
                        "b".repeat(64),
                        64,
                        "application/octet-stream"
                )),
                digest,
                START,
                "gatex5b-test"
        );
        return new StrategyRelease(
                fixture.publishId(),
                fixture.publishId(),
                fixture.strategyVersionId(),
                fixture.datasetId(),
                fixture.evaluationId(),
                manifest,
                digest,
                StrategyReleaseStatus.VERIFIED,
                StrategyArtifactVerificationResult.verified(digest, 1, 64),
                START,
                START
        );
    }

    private static Fixture seedEligibleFixture(JdbcTemplate jdbc, String suffix) {
        String unique = suffix + "-" + shortId();
        Long accountId = jdbc.queryForObject(
                "INSERT INTO accounts (account_code, venue, status) VALUES (?, 'PAPER', 'ACTIVE') RETURNING account_id",
                Long.class,
                "gatex5b-account-" + unique
        );
        assertNotNull(accountId);
        String strategyId = "gatex5b-strategy-" + unique;
        String strategyCode = "GATEX5B_" + unique.replace('-', '_').toUpperCase(java.util.Locale.ROOT);
        String strategyVersionId = "gatex5b-version-" + unique;
        String researchId = "gatex5b-research-" + unique;
        String configId = "gatex5b-config-" + unique;
        String runId = "gatex5b-run-" + unique;
        String evaluationId = "gatex5b-eval-" + unique;
        String publishId = "gatex5b-publish-" + unique;
        UUID datasetId = UUID.randomUUID();

        jdbc.update(
                "INSERT INTO strategy_definitions (strategy_id, strategy_code, strategy_name, strategy_type, "
                        + "exchange_code, account_id, trade_env, enabled, config_snapshot, version) "
                        + "VALUES (?, ?, 'GateX5B fixture', 'ADMISSION_GUARD', 'PAPER', ?, 'SIM', FALSE, '{}'::jsonb, 1)",
                strategyId, strategyCode, accountId
        );
        jdbc.update(
                "INSERT INTO research_configs (research_config_id, source_strategy_id, name, strategy_snapshot) "
                        + "VALUES (?, ?, 'GateX5B fixture', '{}'::jsonb)",
                researchId, strategyId
        );
        jdbc.update(
                "INSERT INTO backtest_configs (backtest_config_id, research_config_id, name) VALUES (?, ?, 'GateX5B fixture')",
                configId, researchId
        );
        jdbc.update(
                "INSERT INTO marketdata_datasets (dataset_id, dataset_name, exchange_code, market_type, symbol, "
                        + "\"interval\", start_time, end_time, status, quality_status, source, created_by) "
                        + "VALUES (?, ?, 'OKX', 'SPOT', 'BTC-USDT', '1m', ?, ?, 'READY', 'OK', 'GATEX5B_TEST', 'test')",
                datasetId, "gatex5b-dataset-" + unique, Timestamp.from(START), Timestamp.from(START.plusSeconds(60))
        );
        jdbc.update(
                "INSERT INTO strategy_versions (strategy_version_id, strategy_code, version, version_name, status, "
                        + "checksum, created_by) VALUES (?, ?, 1, 'GateX5B fixture', 'ACTIVE', ?, 'test')",
                strategyVersionId, strategyCode, DIGEST
        );
        jdbc.update(
                "INSERT INTO backtest_runs (backtest_run_id, backtest_config_id, research_config_id, source_strategy_id, "
                        + "status, strategy_snapshot, strategy_version_id, backtest_config_snapshot, "
                        + "config_snapshot_json, dataset_snapshot_json, requested_at) "
                        + "VALUES (?, ?, ?, ?, 'SUCCEEDED', '{}'::jsonb, ?, '{}'::jsonb, "
                        + "jsonb_build_object('startTime', ?, 'endTime', ?), "
                        + "jsonb_build_object('datasetId', ?), ?)",
                runId, configId, researchId, strategyId, strategyVersionId,
                START.toString(), START.plusSeconds(60).toString(), datasetId.toString(), Timestamp.from(START)
        );
        jdbc.update(
                "INSERT INTO backtest_eval_reports (eval_report_id, backtest_run_id, evaluation_status, evaluated_at) "
                        + "VALUES (?, ?, 'SUCCEEDED', ?)",
                evaluationId, runId, Timestamp.from(START)
        );
        jdbc.update(
                "INSERT INTO backtest_publish_records (publish_record_id, backtest_run_id, research_config_id, "
                        + "backtest_config_id, source_strategy_id, eval_report_id, strategy_version_id, "
                        + "publish_status, publish_name, artifact_storage_key, manifest_storage_key) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 'SUCCEEDED', 'GateX5B fixture', ?, ?)",
                publishId, runId, researchId, configId, strategyId, evaluationId, strategyVersionId,
                "artifact_" + unique, "manifest_" + unique
        );
        Fixture fixture = new Fixture(strategyVersionId, runId, evaluationId, publishId, datasetId);
        insertPaper(jdbc, fixture, "paper-" + unique, START);
        return fixture;
    }

    private static void insertPaper(JdbcTemplate jdbc, Fixture fixture, String paperRunId, Instant time) {
        jdbc.update(
                "INSERT INTO paper_trading_runs (paper_run_id, publish_id, strategy_version_id, status, trade_env, "
                        + "exchange_code, market_type, symbol, interval_code, created_by, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'STOPPED', 'SIM', 'OKX', 'SPOT', 'BTC-USDT', '1m', 'test', ?, ?)",
                paperRunId, fixture.publishId(), fixture.strategyVersionId(), Timestamp.from(time), Timestamp.from(time)
        );
    }

    private static void insertShadowEvidence(
            JdbcTemplate jdbc,
            Fixture fixture,
            UUID shadowRunId,
            String status,
            Instant time
    ) {
        jdbc.update(
                "INSERT INTO shadow_runs (id, strategy_version_id, dataset_id, evaluation_id, publish_id, "
                        + "artifact_digest, status, idempotency_key, trace_id, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'gatex5b-test', ?, ?)",
                shadowRunId, fixture.strategyVersionId(), fixture.datasetId(), fixture.evaluationId(),
                fixture.publishId(), DIGEST, status, "evidence-" + shortId(), Timestamp.from(time), Timestamp.from(time)
        );
    }

    private static ShadowRunFactRepository failingAuditRepository(ShadowRunFactRepository delegate) {
        return (ShadowRunFactRepository) Proxy.newProxyInstance(
                ShadowRunFactRepository.class.getClassLoader(),
                new Class<?>[]{ShadowRunFactRepository.class},
                (proxy, method, args) -> {
                    if ("appendEvent".equals(method.getName())) {
                        throw new IllegalStateException("forced audit failure");
                    }
                    try {
                        return method.invoke(delegate, args);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                }
        );
    }

    private static long count(JdbcTemplate jdbc, String table) {
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return value == null ? 0L : value;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting for test latch");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for test latch", exception);
        }
    }

    private static void migrate(PostgresConfig config, String schema) {
        Flyway flyway = Flyway.configure()
                .dataSource(withCurrentSchema(config.url(), schema), config.user(), config.password())
                .locations("classpath:db/migration")
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .load();
        flyway.migrate();
        flyway.validate();
    }

    private static JdbcTemplate jdbc(PostgresConfig config, String schema) {
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

    private static String randomSchema(String suffix) {
        return "gatex5b_" + suffix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static void dropSchema(PostgresConfig config, String schema) {
        if (schema == null || !schema.matches("gatex5b_(issuance|races|writer)_[0-9a-f]{32}")) {
            throw new IllegalArgumentException("refusing to drop non-GateX5B schema");
        }
        jdbc(config, "public").execute("DROP SCHEMA IF EXISTS \"" + schema + "\" CASCADE");
    }

    private static PostgresConfig requireLocalDisposableConfig() {
        PostgresConfig config = PostgresConfig.fromSystemProperties();
        if (!config.required()) {
            assumeTrue(config.configured(), "local disposable PostgreSQL properties are not configured");
        }
        assertTrue(config.configured(), "missing required local disposable PostgreSQL properties");
        assertTrue(config.localhost(), "GateX-5B PostgreSQL test refuses non-local database URLs");
        return config;
    }

    private record Fixture(
            String strategyVersionId,
            String backtestRunId,
            String evaluationId,
            String publishId,
            UUID datasetId
    ) {
    }

    private record Harness(
            JdbcTemplate jdbc,
            Fixture fixture,
            JdbcAdmissionMutationCoordinator coordinator,
            StrategyReleaseAdmissionStateRepository stateRepository,
            StrategyReleaseAdmissionPreviewFactsRepository factsRepository,
            AdmissionGuardDecisionService decisionService,
            AdmissionGuardFingerprinter fingerprinter,
            ObjectMapper objectMapper,
            ShadowRunFactRepository repository
    ) {
        ShadowRunMaterializationWriter writer() {
            return writerFor(repository);
        }

        ShadowRunMaterializationWriter writerFor(ShadowRunFactRepository target) {
            return new ShadowRunMaterializationWriter(
                    target,
                    objectMapper,
                    coordinator,
                    stateRepository,
                    factsRepository,
                    decisionService,
                    fingerprinter
            );
        }

        AdmissionGuard guard() {
            return fingerprinter.issue(
                    stateRepository.loadByPublishRecordId(fixture.publishId()),
                    factsRepository.loadByPublishRecordId(fixture.publishId()),
                    START.plusSeconds(100)
            );
        }

        ShadowRunCreationPlan plan(String commandIdentity) {
            return new ShadowRunCreationPlan(
                    fixture.publishId(),
                    fixture.publishId(),
                    DIGEST,
                    fixture.strategyVersionId(),
                    fixture.datasetId(),
                    fixture.evaluationId(),
                    START,
                    START.plusSeconds(60),
                    "dataset:" + fixture.datasetId(),
                    ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY,
                    new ShadowRunCreationPlan.SideEffectPolicy(true, true, true, true, true, true),
                    "strategy-release-manifest.v1",
                    "publish:" + fixture.publishId(),
                    "trace-gatex5b",
                    "f".repeat(64)
            ).bindMaterializationCommand(commandIdentity);
        }

        StrategyReleaseAdmissionPreviewService previewService(
                StrategyReleaseAdmissionPreviewFactsRepository targetFactsRepository
        ) {
            return AdmissionGuardedMaterializationPostgresIntegrationTest.previewService(
                    this,
                    targetFactsRepository,
                    release(fixture, DIGEST)
            );
        }
    }

    private static final class StubReleaseProductionService extends StrategyReleaseProductionService {
        private final StrategyRelease release;

        private StubReleaseProductionService(StrategyRelease release) {
            super(
                    ignored -> StrategyReleaseProvenanceFacts.missing(release.publishRecordId()),
                    (artifactStorageKey, manifestStorageKey) ->
                            StrategyReleaseArtifactBindingResolver.ArtifactBindingResolution.rejected(
                                    StrategyArtifactVerificationResult.FindingCode.ARTIFACT_LOCATION_UNBOUND,
                                    "<artifact-binding>"
                            ),
                    new TrustedRootStrategyArtifactVerifier(new StrategyArtifactVerificationPolicy(1, 64, 64))
            );
            this.release = release;
        }

        @Override
        public StrategyRelease verify(String publishRecordId) {
            return release;
        }
    }

    private record PostgresConfig(String url, String user, String password, boolean required) {
        private static PostgresConfig fromSystemProperties() {
            return new PostgresConfig(
                    property(URL_PROPERTY),
                    property(USER_PROPERTY),
                    property(PASSWORD_PROPERTY),
                    Boolean.parseBoolean(property(REQUIRED_PROPERTY))
            );
        }

        private boolean configured() {
            return !url.isBlank() && !user.isBlank() && !password.isBlank();
        }

        private boolean localhost() {
            try {
                String host = URI.create(url.replaceFirst("^jdbc:", "")).getHost();
                return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }

        private static String property(String name) {
            return System.getProperty(name, "").trim();
        }
    }
}
