package com.guidinglight.nexusquant.app.shadowrun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.service.StrategyValidationOverviewQueryService;
import com.guidinglight.nexusquant.strategy.domain.port.ShadowRunFactRepository;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyValidationOverviewFacts;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRun;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunAuthorizationBoundary;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunIdempotencyConflictException;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunReleaseBindingMode;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStatus;
import com.guidinglight.nexusquant.strategy.infra.jdbc.JdbcAdmissionMutationCoordinator;
import com.guidinglight.nexusquant.strategy.infra.jdbc.JdbcPaperShadowConsistencyDrilldownQueryRepository;
import com.guidinglight.nexusquant.strategy.infra.jdbc.JdbcShadowRunFactRepository;
import com.guidinglight.nexusquant.strategy.infra.jdbc.JdbcShadowRunIllegalTransitionAuditWriter;
import com.guidinglight.nexusquant.strategy.infra.jdbc.JdbcShadowRunOverviewQueryRepository;
import com.guidinglight.nexusquant.strategy.infra.jdbc.JdbcStrategyReleaseAdmissionPreviewFactsRepository;
import com.guidinglight.nexusquant.strategy.infra.jdbc.JdbcStrategyReleaseAdmissionStateRepository;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.exception.AdmissionStaleException;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.model.AdmissionGuard;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.model.ShadowRunCreationPlan;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.model.ShadowRunMaterializationResult;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.model.VerifiedStrategyReleaseIdentity;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.port.StrategyReleaseAdmissionPreviewFactsRepository;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.port.StrategyReleaseAdmissionStateRepository;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.service.AdmissionGuardDecisionService;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.service.AdmissionGuardFingerprinter;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.service.ShadowRunMaterializationWriter;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 影子运行来源绑定在隔离 PostgreSQL 上的正式基线行为回归。
 *
 * <p>测试只接受固定本机端口的新随机控制库，并只创建、删除自身登记的随机数据库；未提供
 * properties 时普通 Maven 回归跳过。显式 focused run 必须将 required 设为 true，防止把未执行误报为通过。
 */
class ShadowRunProvenancePostgresIntegrationTest {

    private static final String REQUIRED_PROPERTY = "nq.shadow-provenance.postgres.required";
    private static final String URL_PROPERTY = "nq.shadow-provenance.postgres.url";
    private static final String USER_PROPERTY = "nq.shadow-provenance.postgres.user";
    private static final String PASSWORD_PROPERTY = "nq.shadow-provenance.postgres.password";
    private static final String DIGEST = "a".repeat(64);
    private static final Instant START = Instant.parse("2026-08-10T00:00:00Z");
    private static final Set<String> OWNED_DATABASES = ConcurrentHashMap.newKeySet();

    @Test
    void freshBaselineShouldPreserveProvenanceAcrossLifecycleUpdates() {
        PostgresConfig config = requireLocalDisposableConfig();
        String schema = randomSchema("fresh");
        try {
            migrate(config, schema, null);
            JdbcTemplate jdbc = jdbc(config, schema);
            assertEquals("20", currentFlywayVersion(jdbc));
            assertSchemaContract(jdbc);

            Fixture fixture = seedFixture(jdbc, "fresh");
            JdbcShadowRunFactRepository repository = repository(jdbc);
            ShadowRun stopped = repository.create(releaseBoundRun(fixture, "stopped"));
            assertBinding(
                    stopped,
                    fixture.publishId(),
                    DIGEST,
                    ShadowRunReleaseBindingMode.RELEASE_BOUND);
            ShadowRun conflicting =
                    copyWithProvenance(stopped, fixture.publishId(), "b".repeat(64));
            assertThrows(
                    ShadowRunIdempotencyConflictException.class,
                    () -> repository.create(conflicting));
            assertBinding(
                    repository.findById(stopped.id()).orElseThrow(),
                    fixture.publishId(),
                    DIGEST,
                    ShadowRunReleaseBindingMode.RELEASE_BOUND);
            transitionAndAssert(
                    repository,
                    stopped,
                    List.of(
                            ShadowRunStatus.PRECHECKING,
                            ShadowRunStatus.READY,
                            ShadowRunStatus.RUNNING,
                            ShadowRunStatus.STOP_REQUESTED,
                            ShadowRunStatus.STOPPED));

            ShadowRun completed = repository.create(releaseBoundRun(fixture, "completed"));
            transitionAndAssert(
                    repository,
                    completed,
                    List.of(
                            ShadowRunStatus.PRECHECKING,
                            ShadowRunStatus.READY,
                            ShadowRunStatus.RUNNING,
                            ShadowRunStatus.COMPLETED));
            assertConstraintRejections(jdbc, fixture);
        } finally {
            dropSchema(config, schema);
        }
    }

    @Test
    void baselineRerunShouldPreserveUnknownLegacyBindingModes() {
        PostgresConfig config = requireLocalDisposableConfig();
        String schema = randomSchema("upgrade");
        try {
            migrate(config, schema, MigrationVersion.fromVersion("1"));
            JdbcTemplate before = jdbc(config, schema);
            Fixture fixture = seedFixture(before, "upgrade");
            UUID unboundId = insertLegacyRun(before, fixture, null, "legacy-unbound");
            UUID publishOnlyId =
                    insertLegacyRun(before, fixture, fixture.publishId(), "legacy-publish");

            migrate(config, schema, null);
            JdbcTemplate upgraded = jdbc(config, schema);
            assertEquals("20", currentFlywayVersion(upgraded));
            assertSchemaContract(upgraded);
            assertEquals(
                    2,
                    upgraded.queryForObject(
                            "SELECT COUNT(*) FROM shadow_runs WHERE artifact_digest IS NULL",
                            Integer.class));

            JdbcShadowRunFactRepository repository = repository(upgraded);
            ShadowRun unbound = repository.findById(unboundId).orElseThrow();
            ShadowRun publishOnly = repository.findById(publishOnlyId).orElseThrow();
            assertBinding(unbound, null, null, ShadowRunReleaseBindingMode.LEGACY_UNBOUND);
            assertBinding(
                    publishOnly,
                    fixture.publishId(),
                    null,
                    ShadowRunReleaseBindingMode.LEGACY_PUBLISH_ONLY);
        } finally {
            dropSchema(config, schema);
        }
    }

    @Test
    void materializationShouldBeAtomicIdempotentConcurrentAndProvenanceBound() throws Exception {
        PostgresConfig config = requireLocalDisposableConfig();
        String schema = randomSchema("fresh");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            migrate(config, schema, null);
            JdbcTemplate jdbc = jdbc(config, schema);
            Fixture fixture = seedFixture(jdbc, "materialization");
            JdbcShadowRunFactRepository repository = repository(jdbc);
            prepareEligibleAdmissionFacts(jdbc, fixture);
            WriterHarness harness = writerHarness(jdbc, repository, fixture);
            ShadowRunMaterializationWriter writer = harness.writer();

            ShadowRunCreationPlan singlePlan = materializationPlan(fixture, "single-command");
            ShadowRunMaterializationResult created =
                    writer.materialize(singlePlan, harness.guard(), 41L);
            ShadowRunMaterializationResult replay =
                    writer.materialize(singlePlan, harness.guard(), 41L);
            assertTrue(created != null && replay != null);
            assertEquals(created.shadowRunId(), replay.shadowRunId());
            assertEquals(false, created.idempotentReplay());
            assertEquals(true, replay.idempotentReplay());
            assertMaterializedFacts(jdbc, singlePlan, created.shadowRunId());

            ShadowRunCreationPlan concurrentPlan =
                    materializationPlan(fixture, "concurrent-command");
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            AdmissionGuard concurrentGuard = harness.guard();
            Future<Object> first =
                    executor.submit(
                            () ->
                                    concurrentMaterialize(
                                            writer, concurrentPlan, concurrentGuard, ready, start));
            Future<Object> second =
                    executor.submit(
                            () ->
                                    concurrentMaterialize(
                                            writer, concurrentPlan, concurrentGuard, ready, start));
            ready.await();
            start.countDown();
            List<Object> concurrentResults = List.of(first.get(), second.get());
            assertEquals(
                    1,
                    concurrentResults.stream()
                            .filter(ShadowRunMaterializationResult.class::isInstance)
                            .count());
            assertEquals(
                    1,
                    concurrentResults.stream()
                            .filter(AdmissionStaleException.class::isInstance)
                            .count());
            ShadowRunMaterializationResult firstResult =
                    concurrentResults.stream()
                            .filter(ShadowRunMaterializationResult.class::isInstance)
                            .map(ShadowRunMaterializationResult.class::cast)
                            .findFirst()
                            .orElseThrow();
            ShadowRunMaterializationResult concurrentReplay =
                    writer.materialize(concurrentPlan, harness.guard(), 41L);
            assertEquals(firstResult.shadowRunId(), concurrentReplay.shadowRunId());
            assertTrue(concurrentReplay.idempotentReplay());
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM shadow_runs WHERE idempotency_key = ?",
                            Integer.class,
                            concurrentPlan.shadowRunIdempotencyKey()));
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM shadow_run_events WHERE shadow_run_id = ? AND event_type = 'CREATED'",
                            Integer.class,
                            firstResult.shadowRunId()));

            ShadowRunCreationPlan conflicting = copyWithWindowConflict(concurrentPlan);
            assertThrows(
                    AdmissionStaleException.class,
                    () -> writer.materialize(conflicting, harness.guard(), 41L));

            ShadowRunCreationPlan rollbackPlan = materializationPlan(fixture, "rollback-command");
            ShadowRunFactRepository failingAuditRepository = failingAuditRepository(repository);
            ShadowRunMaterializationWriter failingWriter =
                    harness.writerFor(failingAuditRepository);
            long revisionBeforeRollback =
                    harness.stateRepository()
                            .loadByPublishRecordId(fixture.publishId())
                            .admissionRevision();
            assertThrows(
                    IllegalStateException.class,
                    () -> failingWriter.materialize(rollbackPlan, harness.guard(), 41L));
            assertEquals(
                    0,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM shadow_runs WHERE idempotency_key = ?",
                            Integer.class,
                            rollbackPlan.shadowRunIdempotencyKey()));
            assertEquals(
                    0,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM shadow_run_events e JOIN shadow_runs s ON s.id = e.shadow_run_id "
                                    + "WHERE s.idempotency_key = ?",
                            Integer.class,
                            rollbackPlan.shadowRunIdempotencyKey()));
            assertEquals(
                    revisionBeforeRollback,
                    harness.stateRepository()
                            .loadByPublishRecordId(fixture.publishId())
                            .admissionRevision());
        } finally {
            executor.shutdownNow();
            dropSchema(config, schema);
        }
    }

    private static Object concurrentMaterialize(
            ShadowRunMaterializationWriter writer,
            ShadowRunCreationPlan plan,
            AdmissionGuard guard,
            CountDownLatch ready,
            CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            return writer.materialize(plan, guard, 41L);
        } catch (AdmissionStaleException exception) {
            return exception;
        }
    }

    @Test
    void idempotencyConflictMatrixShouldPreserveExistingPostgresFacts() {
        PostgresConfig config = requireLocalDisposableConfig();
        String schema = randomSchema("fresh");
        try {
            migrate(config, schema, null);
            JdbcTemplate jdbc = jdbc(config, schema);
            Fixture fixture = seedFixture(jdbc, "conflict-base");
            Fixture other = seedFixture(jdbc, "conflict-other");
            JdbcShadowRunFactRepository repository = repository(jdbc);
            ShadowRun existing = repository.create(provenanceMatrixRun(fixture, "shared-command"));
            jdbc.update(
                    "INSERT INTO shadow_run_events (id, shadow_run_id, event_type, to_status, reason_code, message, "
                            + "metadata, request_id, trace_id, created_at) "
                            + "VALUES (?, ?, 'CREATED', 'CREATED', 'TEST_CREATED', 'fixture', '{}'::jsonb, ?, ?, ?)",
                    UUID.randomUUID(),
                    existing.id(),
                    existing.requestId(),
                    existing.traceId(),
                    Timestamp.from(existing.createdAt()));

            ObjectNode policyConflict = (ObjectNode) existing.sideEffectPolicy().deepCopy();
            policyConflict.put("policyVersion", "gate-x5-release-materialization.v2");
            ObjectNode provenanceConflict = (ObjectNode) existing.sideEffectPolicy().deepCopy();
            provenanceConflict.put("provenanceReference", "publish:other");
            ObjectNode inputConflict = (ObjectNode) existing.sideEffectPolicy().deepCopy();
            inputConflict.put("inputReference", "dataset:other");
            List<ShadowRun> conflicts =
                    List.of(
                            copyMatrixRun(
                                    existing,
                                    other.publishId(),
                                    existing.artifactDigest(),
                                    existing.strategyVersionId(),
                                    existing.datasetId(),
                                    existing.evaluationId(),
                                    existing.windowStart(),
                                    existing.windowEnd(),
                                    existing.sideEffectPolicy(),
                                    existing.authorizationBoundary()),
                            copyMatrixRun(
                                    existing,
                                    existing.publishId(),
                                    "b".repeat(64),
                                    existing.strategyVersionId(),
                                    existing.datasetId(),
                                    existing.evaluationId(),
                                    existing.windowStart(),
                                    existing.windowEnd(),
                                    existing.sideEffectPolicy(),
                                    existing.authorizationBoundary()),
                            copyMatrixRun(
                                    existing,
                                    existing.publishId(),
                                    existing.artifactDigest(),
                                    other.strategyVersionId(),
                                    existing.datasetId(),
                                    existing.evaluationId(),
                                    existing.windowStart(),
                                    existing.windowEnd(),
                                    existing.sideEffectPolicy(),
                                    existing.authorizationBoundary()),
                            copyMatrixRun(
                                    existing,
                                    existing.publishId(),
                                    existing.artifactDigest(),
                                    existing.strategyVersionId(),
                                    other.datasetId(),
                                    existing.evaluationId(),
                                    existing.windowStart(),
                                    existing.windowEnd(),
                                    existing.sideEffectPolicy(),
                                    existing.authorizationBoundary()),
                            copyMatrixRun(
                                    existing,
                                    existing.publishId(),
                                    existing.artifactDigest(),
                                    existing.strategyVersionId(),
                                    existing.datasetId(),
                                    other.evaluationId(),
                                    existing.windowStart(),
                                    existing.windowEnd(),
                                    existing.sideEffectPolicy(),
                                    existing.authorizationBoundary()),
                            copyMatrixRun(
                                    existing,
                                    existing.publishId(),
                                    existing.artifactDigest(),
                                    existing.strategyVersionId(),
                                    existing.datasetId(),
                                    existing.evaluationId(),
                                    existing.windowStart().minusSeconds(1),
                                    existing.windowEnd(),
                                    existing.sideEffectPolicy(),
                                    existing.authorizationBoundary()),
                            copyMatrixRun(
                                    existing,
                                    existing.publishId(),
                                    existing.artifactDigest(),
                                    existing.strategyVersionId(),
                                    existing.datasetId(),
                                    existing.evaluationId(),
                                    existing.windowStart(),
                                    existing.windowEnd().plusSeconds(1),
                                    existing.sideEffectPolicy(),
                                    existing.authorizationBoundary()),
                            copyMatrixRun(
                                    existing,
                                    existing.publishId(),
                                    existing.artifactDigest(),
                                    existing.strategyVersionId(),
                                    existing.datasetId(),
                                    existing.evaluationId(),
                                    existing.windowStart(),
                                    existing.windowEnd(),
                                    policyConflict,
                                    existing.authorizationBoundary()),
                            copyMatrixRun(
                                    existing,
                                    existing.publishId(),
                                    existing.artifactDigest(),
                                    existing.strategyVersionId(),
                                    existing.datasetId(),
                                    existing.evaluationId(),
                                    existing.windowStart(),
                                    existing.windowEnd(),
                                    provenanceConflict,
                                    existing.authorizationBoundary()),
                            copyMatrixRun(
                                    existing,
                                    existing.publishId(),
                                    existing.artifactDigest(),
                                    existing.strategyVersionId(),
                                    existing.datasetId(),
                                    existing.evaluationId(),
                                    existing.windowStart(),
                                    existing.windowEnd(),
                                    inputConflict,
                                    existing.authorizationBoundary()),
                            copyMatrixRun(
                                    existing,
                                    existing.publishId(),
                                    existing.artifactDigest(),
                                    existing.strategyVersionId(),
                                    existing.datasetId(),
                                    existing.evaluationId(),
                                    existing.windowStart(),
                                    existing.windowEnd(),
                                    existing.sideEffectPolicy(),
                                    ShadowRunAuthorizationBoundary.REVIEW_ONLY));

            for (ShadowRun conflict : conflicts) {
                ShadowRunIdempotencyConflictException exception =
                        assertThrows(
                                ShadowRunIdempotencyConflictException.class,
                                () -> repository.create(conflict));
                assertEquals("IDEMPOTENCY_CONFLICT", exception.reasonCode());
                assertEquals(
                        existing,
                        repository.findByIdempotencyKey(existing.idempotencyKey()).orElseThrow());
                assertEquals(
                        1,
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM shadow_runs WHERE idempotency_key = ?",
                                Integer.class,
                                existing.idempotencyKey()));
                assertEquals(
                        1,
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM shadow_run_events WHERE shadow_run_id = ? AND event_type = 'CREATED'",
                                Integer.class,
                                existing.id()));
            }
        } finally {
            dropSchema(config, schema);
        }
    }

    @Test
    void preciseWindowsRoundTripAcrossEveryFormalReaderAndRejectOneNanosecondConflicts() {
        PostgresConfig config = requireLocalDisposableConfig();
        String schema = randomSchema("fresh");
        try {
            migrate(config, schema, null);
            JdbcTemplate jdbc = jdbc(config, schema);
            Fixture fixture = seedFixture(jdbc, "window-precision");
            JdbcShadowRunFactRepository repository = repository(jdbc);
            ObjectMapper mapper = new ObjectMapper();
            List<Instant[]> windows =
                    List.of(
                            new Instant[] {
                                Instant.parse("2026-10-14T23:59:33.567325500Z"),
                                Instant.parse("2026-10-15T00:59:33.567325999Z")
                            },
                            new Instant[] {
                                Instant.parse("2026-10-14T23:59:33.999999999Z"),
                                Instant.parse("2026-10-14T23:59:34Z")
                            },
                            new Instant[] {
                                Instant.EPOCH.minusNanos(501), Instant.EPOCH.minusNanos(500)
                            },
                            new Instant[] {START.plusNanos(1), START.plusNanos(2)},
                            new Instant[] {START.plusNanos(499), START.plusNanos(500)},
                            new Instant[] {START.plusNanos(999), START.plusNanos(1_000)},
                            new Instant[] {START, START.plusNanos(1_000)},
                            new Instant[] {null, null},
                            new Instant[] {START, START});
            int index = 0;
            for (Instant[] window : windows) {
                ShadowRun source = provenanceMatrixRun(fixture, "precise-window-" + index);
                ShadowRun requested = withWindow(source, window[0], window[1]);
                ShadowRun created = repository.create(requested);
                assertEquals(requested.id(), created.id());
                assertEquals(window[0], created.windowStart());
                assertEquals(window[1], created.windowEnd());
                assertEquals(
                        created.id(),
                        repository.create(withWindow(requested, window[0], window[1])).id());
                assertEquals(
                        window[0], repository.findById(created.id()).orElseThrow().windowStart());
                assertEquals(
                        window[1],
                        repository
                                .findByIdempotencyKey(created.idempotencyKey())
                                .orElseThrow()
                                .windowEnd());
                // 仅改变测试行的排序时间，确保 overview 真正映射本轮窗口，而非碰巧读到另一条记录。
                jdbc.update(
                        "UPDATE shadow_runs SET updated_at=? WHERE id=?",
                        Timestamp.from(START.plusSeconds(++index)),
                        created.id());
                ShadowRun overview =
                        new JdbcShadowRunOverviewQueryRepository(jdbc, mapper)
                                .loadOverviewFacts()
                                .latestRun()
                                .orElseThrow();
                ShadowRun drilldown =
                        new JdbcPaperShadowConsistencyDrilldownQueryRepository(jdbc, mapper)
                                .loadDrilldownFacts(created.id())
                                .shadowRun()
                                .orElseThrow();
                assertEquals(created.id(), overview.id());
                assertEquals(window[0], overview.windowStart());
                assertEquals(window[1], overview.windowEnd());
                assertEquals(window[0], drilldown.windowStart());
                assertEquals(window[1], drilldown.windowEnd());
                if (window[0] != null && window[1] != null && window[1].isAfter(window[0])) {
                    assertThrows(
                            ShadowRunIdempotencyConflictException.class,
                            () ->
                                    repository.create(
                                            withWindow(
                                                    requested, window[0].plusNanos(1), window[1])));
                    assertThrows(
                            ShadowRunIdempotencyConflictException.class,
                            () ->
                                    repository.create(
                                            withWindow(
                                                    requested, window[0], window[1].plusNanos(1))));
                    assertEquals(
                            window[0],
                            repository.findById(created.id()).orElseThrow().windowStart());
                }
            }
            assertEquals(
                    windows.size(),
                    jdbc.queryForObject("SELECT count(*) FROM shadow_runs", Integer.class));
            assertThrows(
                    DataAccessException.class,
                    () ->
                            jdbc.update(
                                    "UPDATE shadow_runs SET window_start_nanosecond_remainder=-1"));
            assertThrows(
                    DataAccessException.class,
                    () ->
                            jdbc.update(
                                    "UPDATE shadow_runs SET window_end_nanosecond_remainder=1000"));
            assertThrows(
                    DataAccessException.class,
                    () ->
                            jdbc.update(
                                    "UPDATE shadow_runs SET window_start=NULL, window_start_nanosecond_remainder=1"));
            assertThrows(
                    DataAccessException.class,
                    () ->
                            jdbc.update(
                                    "UPDATE shadow_runs SET window_end=window_start, window_start_nanosecond_remainder=2, window_end_nanosecond_remainder=1 WHERE window_start IS NOT NULL"));
        } finally {
            dropSchema(config, schema);
        }
    }

    @Test
    void windowPrecisionUpgradePreservesEveryLegacyColumnAndDefaultWriteCompatibility() {
        PostgresConfig config = requireLocalDisposableConfig();
        String schema = randomSchema("upgrade");
        try {
            migrate(config, schema, MigrationVersion.fromVersion("19"));
            JdbcTemplate jdbc = jdbc(config, schema);
            Fixture fixture = seedFixture(jdbc, "precision-upgrade");
            UUID unbound = insertLegacyRun(jdbc, fixture, null, "precision-legacy-null");
            UUID bound =
                    insertLegacyRun(jdbc, fixture, fixture.publishId(), "precision-legacy-equal");
            jdbc.update(
                    "UPDATE shadow_runs SET window_start=NULL, window_end=NULL WHERE id=?",
                    unbound);
            jdbc.update(
                    "UPDATE shadow_runs SET window_start=?, window_end=? WHERE id=?",
                    Timestamp.from(START),
                    Timestamp.from(START),
                    bound);
            String before =
                    jdbc.queryForObject(
                            "SELECT jsonb_agg(to_jsonb(s) ORDER BY id)::text FROM shadow_runs s",
                            String.class);
            migrate(config, schema, null);
            String after =
                    jdbc.queryForObject(
                            "SELECT jsonb_agg(to_jsonb(s)-'window_start_nanosecond_remainder'-'window_end_nanosecond_remainder' ORDER BY id)::text FROM shadow_runs s",
                            String.class);
            assertEquals(before, after);
            assertEquals("20", currentFlywayVersion(jdbc));
            assertEquals(
                    0,
                    jdbc.queryForObject(
                            "SELECT sum(window_start_nanosecond_remainder + window_end_nanosecond_remainder) FROM shadow_runs",
                            Integer.class));
            assertEquals(null, repository(jdbc).findById(unbound).orElseThrow().windowStart());
            assertEquals(
                    repository(jdbc).findById(bound).orElseThrow().windowStart(),
                    repository(jdbc).findById(bound).orElseThrow().windowEnd());
            UUID oldWriter = insertLegacyRun(jdbc, fixture, null, "precision-old-default-writer");
            assertEquals(null, repository(jdbc).findById(oldWriter).orElseThrow().windowStart());
            // 再次 migrate/validate 不修改已升级行，也不 repair 或改写历史 migration。
            String upgraded =
                    jdbc.queryForObject(
                            "SELECT jsonb_agg(to_jsonb(s) ORDER BY id)::text FROM shadow_runs s",
                            String.class);
            migrate(config, schema, null);
            assertEquals(
                    upgraded,
                    jdbc.queryForObject(
                            "SELECT jsonb_agg(to_jsonb(s) ORDER BY id)::text FROM shadow_runs s",
                            String.class));
        } finally {
            dropSchema(config, schema);
        }
    }

    private static ShadowRun withWindow(ShadowRun source, Instant start, Instant end) {
        return copyMatrixRun(
                source,
                source.publishId(),
                source.artifactDigest(),
                source.strategyVersionId(),
                source.datasetId(),
                source.evaluationId(),
                start,
                end,
                source.sideEffectPolicy(),
                source.authorizationBoundary());
    }

    private static void assertMaterializedFacts(
            JdbcTemplate jdbc, ShadowRunCreationPlan plan, UUID shadowRunId) {
        var facts =
                jdbc.queryForMap(
                        "SELECT publish_id, artifact_digest, status, started_at, paper_run_id FROM shadow_runs WHERE id = ?",
                        shadowRunId);
        assertEquals(plan.publishRecordId(), facts.get("publish_id"));
        assertEquals(plan.artifactDigest(), facts.get("artifact_digest"));
        assertEquals("CREATED", facts.get("status"));
        assertEquals(null, facts.get("started_at"));
        assertEquals(null, facts.get("paper_run_id"));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM shadow_run_events WHERE shadow_run_id = ? AND event_type = 'CREATED'",
                        Integer.class,
                        shadowRunId));
    }

    private static ShadowRunCreationPlan materializationPlan(
            Fixture fixture, String commandIdentity) {
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
                        new ShadowRunCreationPlan.SideEffectPolicy(
                                true, true, true, true, true, true),
                        "strategy-release-manifest.v1",
                        "publish:" + fixture.publishId(),
                        "trace-gatex5",
                        "b".repeat(64))
                .bindMaterializationCommand(commandIdentity);
    }

    private static ShadowRunCreationPlan copyWithWindowConflict(ShadowRunCreationPlan source) {
        return new ShadowRunCreationPlan(
                source.releaseAnchorId(),
                source.publishRecordId(),
                source.artifactDigest(),
                source.strategyVersionId(),
                source.datasetId(),
                source.evaluationId(),
                source.windowStart(),
                source.windowEnd().plusSeconds(1),
                source.inputReference(),
                source.authorizationBoundary(),
                source.sideEffectPolicy(),
                source.manifestSchemaVersion(),
                source.provenanceReference(),
                source.traceId(),
                source.shadowRunIdempotencyKey());
    }

    private static ShadowRunFactRepository failingAuditRepository(
            ShadowRunFactRepository delegate) {
        return (ShadowRunFactRepository)
                Proxy.newProxyInstance(
                        ShadowRunFactRepository.class.getClassLoader(),
                        new Class<?>[] {ShadowRunFactRepository.class},
                        (proxy, method, args) -> {
                            if ("appendEvent".equals(method.getName())) {
                                throw new IllegalStateException("forced audit failure");
                            }
                            try {
                                return method.invoke(delegate, args);
                            } catch (InvocationTargetException exception) {
                                throw exception.getCause();
                            }
                        });
    }

    private static void assertSchemaContract(JdbcTemplate jdbc) {
        assertEquals(
                "YES",
                jdbc.queryForObject(
                        "SELECT is_nullable FROM information_schema.columns "
                                + "WHERE table_schema = current_schema() AND table_name = 'shadow_runs' "
                                + "AND column_name = 'artifact_digest'",
                        String.class));
        assertEquals(
                64,
                jdbc.queryForObject(
                        "SELECT character_maximum_length FROM information_schema.columns "
                                + "WHERE table_schema = current_schema() AND table_name = 'shadow_runs' "
                                + "AND column_name = 'artifact_digest'",
                        Integer.class));
        for (String constraint :
                List.of(
                        "chk_shadow_runs_artifact_digest_sha256",
                        "chk_shadow_runs_artifact_requires_publish")) {
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM pg_constraint "
                                    + "WHERE conrelid = 'shadow_runs'::regclass AND conname = ? AND convalidated",
                            Integer.class,
                            constraint));
        }
        assertEquals(
                Boolean.TRUE,
                jdbc.queryForObject(
                        "SELECT col_description(to_regclass('shadow_runs'), ordinal_position) IS NOT NULL "
                                + "FROM information_schema.columns WHERE table_schema = current_schema() "
                                + "AND table_name = 'shadow_runs' AND column_name = 'artifact_digest'",
                        Boolean.class));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = current_schema() "
                                + "AND tablename = 'shadow_runs' AND indexdef ILIKE '%publish_id%' "
                                + "AND indexdef ILIKE '%artifact_digest%'",
                        Integer.class));
    }

    private static void assertConstraintRejections(JdbcTemplate jdbc, Fixture fixture) {
        for (String invalid :
                List.of("a".repeat(63), "a".repeat(65), "A".repeat(64), "g".repeat(64), "")) {
            assertThrows(
                    DataAccessException.class,
                    () ->
                            insertDirectRun(
                                    jdbc,
                                    fixture,
                                    fixture.publishId(),
                                    invalid,
                                    "invalid-" + UUID.randomUUID()));
        }
        assertThrows(
                DataAccessException.class,
                () ->
                        insertDirectRun(
                                jdbc,
                                fixture,
                                null,
                                DIGEST,
                                "missing-publish-" + UUID.randomUUID()));
    }

    private static void transitionAndAssert(
            JdbcShadowRunFactRepository repository,
            ShadowRun initial,
            List<ShadowRunStatus> transitions) {
        long version = initial.version();
        for (ShadowRunStatus target : transitions) {
            repository.updateStatus(
                    initial.id(),
                    target,
                    version,
                    "GATEX2_POSTGRES_REGRESSION",
                    "local disposable PostgreSQL lifecycle regression",
                    "request-gatex2",
                    "trace-gatex2");
            version++;
            ShadowRun reloaded = repository.findById(initial.id()).orElseThrow();
            assertEquals(target, reloaded.status());
            assertEquals(version, reloaded.version());
            assertBinding(
                    reloaded,
                    initial.publishId(),
                    initial.artifactDigest(),
                    initial.releaseBindingMode());
        }
    }

    private static void assertBinding(
            ShadowRun run,
            String publishId,
            String artifactDigest,
            ShadowRunReleaseBindingMode mode) {
        assertEquals(publishId, run.publishId());
        assertEquals(artifactDigest, run.artifactDigest());
        assertEquals(mode, run.releaseBindingMode());
    }

    private static ShadowRun releaseBoundRun(Fixture fixture, String suffix) {
        return new ShadowRun(
                UUID.randomUUID(),
                fixture.strategyVersionId(),
                fixture.datasetId(),
                null,
                fixture.publishId(),
                DIGEST,
                null,
                ShadowRunStatus.CREATED,
                START,
                START.plusSeconds(60),
                JsonNodeFactory.instance.objectNode().put("mode", "NO_SIDE_EFFECT_LOCAL_ONLY"),
                true,
                true,
                true,
                true,
                true,
                true,
                ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY,
                "request-" + suffix,
                "gatex2-idempotency-" + suffix + "-" + UUID.randomUUID(),
                "trace-gatex2",
                JsonNodeFactory.instance.arrayNode(),
                JsonNodeFactory.instance.arrayNode(),
                JsonNodeFactory.instance.arrayNode().add("manual-review"),
                0,
                START,
                START,
                null,
                null,
                null);
    }

    private static ShadowRun copyWithProvenance(
            ShadowRun source, String publishId, String artifactDigest) {
        return new ShadowRun(
                UUID.randomUUID(),
                source.strategyVersionId(),
                source.datasetId(),
                source.evaluationId(),
                publishId,
                artifactDigest,
                source.paperRunId(),
                source.status(),
                source.windowStart(),
                source.windowEnd(),
                source.sideEffectPolicy(),
                source.noOrderSubmission(),
                source.noCredentialAccess(),
                source.noPrivateEndpoint(),
                source.noLedgerMutation(),
                source.noAccountMutation(),
                source.noExternalPrivateIo(),
                source.authorizationBoundary(),
                source.requestId(),
                source.idempotencyKey(),
                source.traceId(),
                source.blockers(),
                source.warnings(),
                source.nextSteps(),
                source.version(),
                source.createdAt(),
                source.updatedAt(),
                source.startedAt(),
                source.stoppedAt(),
                source.completedAt());
    }

    private static UUID insertLegacyRun(
            JdbcTemplate jdbc, Fixture fixture, String publishId, String suffix) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                        INSERT INTO shadow_runs (
                            id, strategy_version_id, dataset_id, publish_id,
                            status, idempotency_key, trace_id
                        ) VALUES (?, ?, ?, ?, 'CREATED', ?, ?)
                        """,
                id,
                fixture.strategyVersionId(),
                fixture.datasetId(),
                publishId,
                "gatex2-direct-" + suffix,
                "trace-gatex2");
        return id;
    }

    private static UUID insertDirectRun(
            JdbcTemplate jdbc,
            Fixture fixture,
            String publishId,
            String artifactDigest,
            String suffix) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                        INSERT INTO shadow_runs (
                            id, strategy_version_id, dataset_id, publish_id, artifact_digest,
                            status, idempotency_key, trace_id
                        ) VALUES (?, ?, ?, ?, ?, 'CREATED', ?, ?)
                        """,
                id,
                fixture.strategyVersionId(),
                fixture.datasetId(),
                publishId,
                artifactDigest,
                "gatex2-direct-" + suffix,
                "trace-gatex2");
        return id;
    }

    private static Fixture seedFixture(JdbcTemplate jdbc, String suffix) {
        String unique = suffix + "-" + UUID.randomUUID().toString().substring(0, 8);
        Long accountId =
                jdbc.queryForObject(
                        "INSERT INTO accounts (account_code, venue, status) VALUES (?, 'PAPER', 'ACTIVE') RETURNING account_id",
                        Long.class,
                        "gatex2-account-" + unique);
        assertTrue(accountId != null && accountId > 0);
        String strategyId = "gatex2-strategy-" + unique;
        String strategyCode = "GATEX2_" + unique.replace('-', '_').toUpperCase();
        String strategyVersionId = "gatex2-version-" + unique;
        String researchId = "gatex2-research-" + unique;
        String backtestConfigId = "gatex2-backtest-config-" + unique;
        String backtestRunId = "gatex2-backtest-run-" + unique;
        String publishId = "gatex2-publish-" + unique;
        String evaluationId = "gatex2-evaluation-" + unique;
        UUID datasetId = UUID.randomUUID();

        jdbc.update(
                "INSERT INTO strategy_definitions (strategy_id, strategy_code, strategy_name, strategy_type, "
                        + "exchange_code, account_id, trade_env, enabled, config_snapshot, version) "
                        + "VALUES (?, ?, 'GateX2 PostgreSQL fixture', 'SHADOW_PROVENANCE', 'PAPER', ?, 'SIM', "
                        + "FALSE, '{}'::jsonb, 1)",
                strategyId,
                strategyCode,
                accountId);
        jdbc.update(
                "INSERT INTO research_configs (research_config_id, source_strategy_id, name, strategy_snapshot) "
                        + "VALUES (?, ?, 'GateX2 fixture', '{}'::jsonb)",
                researchId,
                strategyId);
        jdbc.update(
                "INSERT INTO backtest_configs (backtest_config_id, research_config_id, name) VALUES (?, ?, ?)",
                backtestConfigId,
                researchId,
                "GateX2 fixture");
        jdbc.update(
                "INSERT INTO backtest_runs (backtest_run_id, backtest_config_id, research_config_id, "
                        + "source_strategy_id, status, strategy_snapshot, backtest_config_snapshot, requested_at) "
                        + "VALUES (?, ?, ?, ?, 'SUCCEEDED', '{}'::jsonb, '{}'::jsonb, ?)",
                backtestRunId,
                backtestConfigId,
                researchId,
                strategyId,
                Timestamp.from(START));
        jdbc.update(
                "INSERT INTO strategy_versions (strategy_version_id, strategy_code, version, version_name, status, "
                        + "checksum, created_by) VALUES (?, ?, 1, 'GateX2 fixture', 'ACTIVE', ?, 'gatex2-test')",
                strategyVersionId,
                strategyCode,
                DIGEST);
        jdbc.update(
                "INSERT INTO backtest_eval_reports (eval_report_id, backtest_run_id, evaluation_status, evaluated_at) "
                        + "VALUES (?, ?, 'SUCCEEDED', ?)",
                evaluationId,
                backtestRunId,
                Timestamp.from(START));
        jdbc.update(
                "INSERT INTO backtest_publish_records (publish_record_id, backtest_run_id, research_config_id, "
                        + "backtest_config_id, source_strategy_id, eval_report_id, publish_status, publish_name, "
                        + "strategy_version_id) VALUES (?, ?, ?, ?, ?, ?, 'SUCCEEDED', 'GateX2 fixture', ?)",
                publishId,
                backtestRunId,
                researchId,
                backtestConfigId,
                strategyId,
                evaluationId,
                strategyVersionId);
        jdbc.update(
                "INSERT INTO marketdata_datasets (dataset_id, dataset_name, exchange_code, market_type, symbol, "
                        + "\"interval\", start_time, end_time, status, quality_status, source, created_by, request_json) "
                        + "VALUES (?, ?, 'OKX', 'SPOT', 'BTC-USDT', '1m', ?, ?, 'READY', 'OK', "
                        + "'GATEX2_TEST', 'gatex2-test', '{}'::jsonb)",
                datasetId,
                "gatex2-dataset-" + unique,
                Timestamp.from(START),
                Timestamp.from(START.plusSeconds(60)));
        return new Fixture(strategyVersionId, datasetId, publishId, evaluationId);
    }

    private static JdbcShadowRunFactRepository repository(JdbcTemplate jdbc) {
        DriverManagerDataSource dataSource = (DriverManagerDataSource) jdbc.getDataSource();
        DataSourceTransactionManager transactionManager =
                new DataSourceTransactionManager(dataSource);
        ObjectMapper objectMapper = new ObjectMapper();
        return new JdbcShadowRunFactRepository(
                jdbc,
                objectMapper,
                new JdbcShadowRunIllegalTransitionAuditWriter(
                        jdbc, objectMapper, transactionManager),
                new JdbcAdmissionMutationCoordinator(jdbc, transactionManager, 256));
    }

    private static void prepareEligibleAdmissionFacts(JdbcTemplate jdbc, Fixture fixture) {
        jdbc.update(
                "UPDATE backtest_runs SET strategy_version_id = ?, "
                        + "config_snapshot_json = jsonb_build_object('startTime', ?, 'endTime', ?), "
                        + "dataset_snapshot_json = jsonb_build_object('datasetId', ?) WHERE backtest_run_id = ("
                        + "SELECT backtest_run_id FROM backtest_publish_records WHERE publish_record_id = ?)",
                fixture.strategyVersionId(),
                START.toString(),
                START.plusSeconds(60).toString(),
                fixture.datasetId().toString(),
                fixture.publishId());
        jdbc.update(
                "UPDATE backtest_publish_records SET publish_status = 'FAILED' WHERE publish_record_id = ?",
                fixture.publishId());
        jdbc.update(
                "UPDATE backtest_publish_records SET publish_status = 'SUCCEEDED', "
                        + "artifact_storage_key = ?, manifest_storage_key = ? "
                        + "WHERE publish_record_id = ?",
                "artifact_" + fixture.publishId(),
                "manifest_" + fixture.publishId(),
                fixture.publishId());
        jdbc.update(
                "INSERT INTO paper_trading_runs (paper_run_id, publish_id, strategy_version_id, status, trade_env, "
                        + "exchange_code, market_type, symbol, interval_code, created_by, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'STOPPED', 'SIM', 'OKX', 'SPOT', 'BTC-USDT', '1m', 'test', ?, ?)",
                "paper-" + fixture.publishId(),
                fixture.publishId(),
                fixture.strategyVersionId(),
                Timestamp.from(START),
                Timestamp.from(START));
    }

    private static ShadowRun provenanceMatrixRun(Fixture fixture, String commandIdentity) {
        ObjectNode policy =
                JsonNodeFactory.instance
                        .objectNode()
                        .put("policyVersion", "gate-x5-release-materialization.v1")
                        .put("inputReference", "dataset:" + fixture.datasetId())
                        .put("provenanceReference", "publish:" + fixture.publishId())
                        .put("noOrderSubmission", true)
                        .put("noCredentialAccess", true)
                        .put("noPrivateEndpoint", true)
                        .put("noLedgerMutation", true)
                        .put("noAccountMutation", true)
                        .put("noExternalPrivateIo", true);
        return new ShadowRun(
                UUID.randomUUID(),
                fixture.strategyVersionId(),
                fixture.datasetId(),
                fixture.evaluationId(),
                fixture.publishId(),
                DIGEST,
                null,
                ShadowRunStatus.CREATED,
                START,
                START.plusSeconds(60),
                policy,
                true,
                true,
                true,
                true,
                true,
                true,
                ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY,
                commandIdentity,
                commandIdentity,
                "trace-gatex5b-conflict",
                JsonNodeFactory.instance.arrayNode(),
                JsonNodeFactory.instance.arrayNode(),
                JsonNodeFactory.instance.arrayNode(),
                0,
                START,
                START,
                null,
                null,
                null);
    }

    private static ShadowRun copyMatrixRun(
            ShadowRun source,
            String publishId,
            String artifactDigest,
            String strategyVersionId,
            UUID datasetId,
            String evaluationId,
            Instant windowStart,
            Instant windowEnd,
            JsonNode sideEffectPolicy,
            ShadowRunAuthorizationBoundary authorizationBoundary) {
        return new ShadowRun(
                UUID.randomUUID(),
                strategyVersionId,
                datasetId,
                evaluationId,
                publishId,
                artifactDigest,
                source.paperRunId(),
                source.status(),
                windowStart,
                windowEnd,
                sideEffectPolicy,
                source.noOrderSubmission(),
                source.noCredentialAccess(),
                source.noPrivateEndpoint(),
                source.noLedgerMutation(),
                source.noAccountMutation(),
                source.noExternalPrivateIo(),
                authorizationBoundary,
                source.requestId(),
                source.idempotencyKey(),
                source.traceId(),
                source.blockers(),
                source.warnings(),
                source.nextSteps(),
                source.version(),
                source.createdAt(),
                source.updatedAt(),
                source.startedAt(),
                source.stoppedAt(),
                source.completedAt());
    }

    private static WriterHarness writerHarness(
            JdbcTemplate jdbc, ShadowRunFactRepository repository, Fixture fixture) {
        DataSourceTransactionManager transactionManager = transactionManager(jdbc);
        JdbcAdmissionMutationCoordinator coordinator =
                new JdbcAdmissionMutationCoordinator(jdbc, transactionManager, 256);
        JdbcStrategyReleaseAdmissionStateRepository stateRepository =
                new JdbcStrategyReleaseAdmissionStateRepository(jdbc, coordinator);
        stateRepository.bindVerifiedReleaseIdentity(
                new VerifiedStrategyReleaseIdentity(
                        fixture.publishId(),
                        fixture.strategyVersionId(),
                        fixture.datasetId(),
                        fixture.evaluationId(),
                        DIGEST,
                        "c".repeat(64),
                        "strategy-release-manifest.v1"));
        JdbcStrategyReleaseAdmissionPreviewFactsRepository factsRepository =
                new JdbcStrategyReleaseAdmissionPreviewFactsRepository(jdbc);
        AdmissionGuardDecisionService decisionService =
                new AdmissionGuardDecisionService(
                        new StrategyValidationOverviewQueryService(
                                () ->
                                        new StrategyValidationOverviewFacts(
                                                0, 0, 0, 0, 0, 0, Optional.empty())));
        AdmissionGuardFingerprinter fingerprinter = new AdmissionGuardFingerprinter();
        return new WriterHarness(
                repository,
                coordinator,
                stateRepository,
                factsRepository,
                decisionService,
                fingerprinter,
                new ObjectMapper(),
                fixture.publishId());
    }

    private static DataSourceTransactionManager transactionManager(JdbcTemplate jdbc) {
        return new DataSourceTransactionManager(jdbc.getDataSource());
    }

    private record WriterHarness(
            ShadowRunFactRepository repository,
            JdbcAdmissionMutationCoordinator coordinator,
            StrategyReleaseAdmissionStateRepository stateRepository,
            StrategyReleaseAdmissionPreviewFactsRepository factsRepository,
            AdmissionGuardDecisionService decisionService,
            AdmissionGuardFingerprinter fingerprinter,
            ObjectMapper objectMapper,
            String publishRecordId) {
        ShadowRunMaterializationWriter writer() {
            return writerFor(repository);
        }

        ShadowRunMaterializationWriter writerFor(ShadowRunFactRepository targetRepository) {
            return new ShadowRunMaterializationWriter(
                    targetRepository,
                    objectMapper,
                    coordinator,
                    stateRepository,
                    factsRepository,
                    decisionService,
                    fingerprinter);
        }

        AdmissionGuard guard() {
            return fingerprinter.issue(
                    stateRepository.loadByPublishRecordId(publishRecordId),
                    factsRepository.loadByPublishRecordId(publishRecordId),
                    START.plusSeconds(120));
        }
    }

    private static void migrate(PostgresConfig config, String schema, MigrationVersion target) {
        String database = ownedDatabaseName(schema);
        if (!OWNED_DATABASES.contains(database)) {
            // 基线的 SECURITY DEFINER 函数固定绑定 public，必须隔离整个库而非改写历史函数。
            jdbc(config, null).execute("CREATE DATABASE \"" + database + "\"");
            OWNED_DATABASES.add(database);
        }
        var configuration =
                Flyway.configure()
                        .dataSource(
                                databaseUrl(config.url(), database),
                                config.user(),
                                config.password())
                        .locations("classpath:db/migration")
                        .schemas("public")
                        .defaultSchema("public");
        if (target != null) {
            configuration.target(target);
        }
        Flyway flyway = configuration.load();
        flyway.migrate();
        flyway.validate();
    }

    private static JdbcTemplate jdbc(PostgresConfig config, String schema) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(
                schema == null
                        ? config.url()
                        : databaseUrl(config.url(), ownedDatabaseName(schema)));
        dataSource.setUsername(config.user());
        dataSource.setPassword(config.password());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.setQueryTimeout(30);
        return jdbc;
    }

    private static String databaseUrl(String controlUrl, String database) {
        return controlUrl.substring(0, controlUrl.lastIndexOf('/') + 1)
                + database
                + "?connectTimeout=5&socketTimeout=30";
    }

    private static String ownedDatabaseName(String schema) {
        if (schema == null || !schema.matches("gatex2_(fresh|upgrade)_[0-9a-f]{32}")) {
            throw new IllegalArgumentException("owned random database identity required");
        }
        return "shadow_creation_http_" + schema.substring(schema.length() - 32);
    }

    private static String currentFlywayVersion(JdbcTemplate jdbc) {
        return jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success = TRUE ORDER BY installed_rank DESC LIMIT 1",
                String.class);
    }

    private static String randomSchema(String suffix) {
        return "gatex2_" + suffix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static void dropSchema(PostgresConfig config, String schema) {
        String database = ownedDatabaseName(schema);
        if (OWNED_DATABASES.contains(database)) {
            jdbc(config, null).execute("DROP DATABASE \"" + database + "\"");
            OWNED_DATABASES.remove(database);
        }
    }

    private static PostgresConfig requireLocalDisposableConfig() {
        PostgresConfig config = PostgresConfig.fromSystemProperties();
        if (!config.required()) {
            assumeTrue(
                    config.configured(),
                    "local disposable PostgreSQL properties are not configured");
        }
        assertTrue(config.configured(), "missing required local disposable PostgreSQL properties");
        assertTrue(
                config.localhost(), "PostgreSQL proof requires an owned loopback control database");
        return config;
    }

    private record Fixture(
            String strategyVersionId, UUID datasetId, String publishId, String evaluationId) {}

    private record PostgresConfig(String url, String user, String password, boolean required) {

        private static PostgresConfig fromSystemProperties() {
            return new PostgresConfig(
                    property(URL_PROPERTY),
                    propertyOrEnvironment(USER_PROPERTY, "TASK_SHADOW_CREATION_DB_USER"),
                    propertyOrEnvironment(PASSWORD_PROPERTY, "TASK_SHADOW_CREATION_DB_PASSWORD"),
                    Boolean.parseBoolean(property(REQUIRED_PROPERTY)));
        }

        private boolean configured() {
            return !url.isBlank() && !user.isBlank() && !password.isBlank();
        }

        private boolean localhost() {
            try {
                String normalized = url.replaceFirst("^jdbc:", "");
                String host = URI.create(normalized).getHost();
                return "127.0.0.1".equals(host)
                        && url.matches(
                                "jdbc:postgresql://127\\.0\\.0\\.1:15442/shadow_creation_http_[a-f0-9]{32}");
            } catch (IllegalArgumentException ex) {
                return false;
            }
        }

        private static String property(String name) {
            return System.getProperty(name, "").trim();
        }

        // 本地证明通过子进程环境传递连接材料，避免把值写入 Maven 参数和 Surefire properties。
        private static String propertyOrEnvironment(String name, String environment) {
            String configured = property(name);
            return configured.isBlank()
                    ? System.getenv().getOrDefault(environment, "")
                    : configured;
        }
    }
}
