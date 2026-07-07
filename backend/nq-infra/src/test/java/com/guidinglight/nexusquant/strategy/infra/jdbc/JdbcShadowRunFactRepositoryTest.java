package com.guidinglight.nexusquant.strategy.infra.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.guidinglight.nexusquant.strategy.domain.port.ShadowRunListQuery;
import com.guidinglight.nexusquant.strategy.domain.port.ShadowRunOverviewEvidenceFact;
import com.guidinglight.nexusquant.strategy.domain.port.ShadowRunOverviewFacts;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowConsistencyComparisonStatus;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowConsistencyReport;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRun;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunAuthorizationBoundary;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunEvent;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunEventType;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunOptimisticLockException;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunSnapshot;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunSnapshotType;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStateMachine;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStateTransitionException;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStatus;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

class JdbcShadowRunFactRepositoryTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Instant NOW = Instant.parse("2026-07-06T00:00:00Z");

    @Test
    void shouldCreateShadowRunWithIdempotencyKeyAndReturnExistingFact() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        ShadowRun run = run(ShadowRunStatus.CREATED, 0);
        jdbcTemplate.queryResults.add(List.of(run));
        JdbcShadowRunFactRepository repository = repository(jdbcTemplate);

        ShadowRun created = repository.create(run);

        assertEquals(run.id(), created.id());
        assertTrue(jdbcTemplate.updateSqls.getFirst().contains("ON CONFLICT (idempotency_key) DO NOTHING"));
        assertTrue(jdbcTemplate.updateSqls.getFirst().contains("CAST(? AS JSONB)"));
    }

    @Test
    void shouldFindByIdAndIdempotencyKey() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        ShadowRun run = run(ShadowRunStatus.CREATED, 0);
        jdbcTemplate.queryResults.add(List.of(run));
        jdbcTemplate.queryResults.add(List.of(run));
        JdbcShadowRunFactRepository repository = repository(jdbcTemplate);

        assertEquals(Optional.of(run), repository.findById(run.id()));
        assertEquals(Optional.of(run), repository.findByIdempotencyKey(run.idempotencyKey()));
        assertTrue(jdbcTemplate.querySqls.get(0).contains("WHERE id = ?"));
        assertTrue(jdbcTemplate.querySqls.get(1).contains("WHERE idempotency_key = ?"));
    }

    @Test
    void shouldAppendEventSnapshotAndConsistencyReportWithJsonbCasts() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        JdbcShadowRunFactRepository repository = repository(jdbcTemplate);
        UUID runId = UUID.randomUUID();

        repository.appendEvent(event(runId, ShadowRunEventType.CREATED));
        repository.appendSnapshot(snapshot(runId));
        ShadowConsistencyReport report = report(runId);
        repository.createConsistencyReport(report);

        assertTrue(jdbcTemplate.updateSqls.get(0).contains("INSERT INTO shadow_run_events"));
        assertTrue(jdbcTemplate.updateSqls.get(1).contains("INSERT INTO shadow_run_snapshots"));
        assertTrue(jdbcTemplate.updateSqls.get(2).contains("INSERT INTO shadow_consistency_reports"));
        assertTrue(jdbcTemplate.updateSqls.stream().allMatch(sql -> sql.contains("CAST(? AS JSONB)")));
    }

    @Test
    void shouldUpdateStatusWithExpectedVersionAndAppendTraceEvent() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        ShadowRun run = run(ShadowRunStatus.READY, 0);
        jdbcTemplate.queryResults.add(List.of(run));
        jdbcTemplate.updateCounts.add(1);
        jdbcTemplate.updateCounts.add(1);
        JdbcShadowRunFactRepository repository = repository(jdbcTemplate);

        var result = repository.updateStatus(
                run.id(),
                ShadowRunStatus.RUNNING,
                0,
                "RUN_REQUESTED",
                "start local shadow run",
                "req-1",
                "trace-1"
        );

        assertEquals(ShadowRunStatus.READY, result.fromStatus());
        assertEquals(ShadowRunStatus.RUNNING, result.toStatus());
        assertEquals(1, result.newVersion());
        assertTrue(jdbcTemplate.updateSqls.get(0).contains("version = version + 1"));
        assertTrue(jdbcTemplate.updateSqls.get(0).contains("AND version = ?"));
        assertEquals(ShadowRunEventType.RUN_STARTED.name(), jdbcTemplate.updateArgs.get(1)[2]);
        assertFalse(jdbcTemplate.updateArgs.stream()
                .anyMatch(args -> ShadowRunEventType.ILLEGAL_STATE_TRANSITION_ATTEMPT.name().equals(args[2])));
    }

    @Test
    void shouldFailStatusUpdateOnVersionMismatch() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        ShadowRun run = run(ShadowRunStatus.READY, 3);
        jdbcTemplate.queryResults.add(List.of(run));
        JdbcShadowRunFactRepository repository = repository(jdbcTemplate);

        assertThrows(ShadowRunOptimisticLockException.class,
                () -> repository.updateStatus(
                        run.id(),
                        ShadowRunStatus.RUNNING,
                        2,
                        "RUN_REQUESTED",
                        "start local shadow run",
                        "req-1",
                        "trace-1"
                ));
        assertFalse(jdbcTemplate.updateSqls.stream().anyMatch(sql -> sql.contains("UPDATE shadow_runs")));
    }

    @Test
    void shouldRecordIllegalTransitionAttemptWhenTerminalStateReturnsToRunning() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        RecordingTransactionManager transactionManager = new RecordingTransactionManager();
        ShadowRun run = run(ShadowRunStatus.COMPLETED, 0);
        jdbcTemplate.queryResults.add(List.of(run));
        jdbcTemplate.updateCounts.add(1);
        JdbcShadowRunFactRepository repository = repository(jdbcTemplate, transactionManager);

        ShadowRunStateTransitionException ex = assertThrows(ShadowRunStateTransitionException.class,
                () -> repository.updateStatus(
                        run.id(),
                        ShadowRunStatus.RUNNING,
                        0,
                        "RUN_REQUESTED",
                        "completed run cannot restart",
                        "req-2",
                        "trace-2"
                ));

        assertEquals("SHADOW_RUN_TERMINAL_STATE_LOCKED", ex.reasonCode());
        assertTrue(jdbcTemplate.updateSqls.getFirst().contains("INSERT INTO shadow_run_events"));
        assertEquals(ShadowRunEventType.ILLEGAL_STATE_TRANSITION_ATTEMPT.name(), jdbcTemplate.updateArgs.getFirst()[2]);
        assertEquals(List.of(TransactionDefinition.PROPAGATION_REQUIRES_NEW),
                transactionManager.propagationBehaviors);
        assertFalse(jdbcTemplate.updateSqls.stream().anyMatch(sql -> sql.contains("UPDATE shadow_runs")));
    }

    @Test
    void shouldListEventsSnapshotsAndLatestReport() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        UUID runId = UUID.randomUUID();
        ShadowRunEvent event = event(runId, ShadowRunEventType.CREATED);
        ShadowRunSnapshot snapshot = snapshot(runId);
        ShadowConsistencyReport report = report(runId);
        jdbcTemplate.queryResults.add(List.of(event));
        jdbcTemplate.queryResults.add(List.of(snapshot));
        jdbcTemplate.queryResults.add(List.of(report));
        JdbcShadowRunFactRepository repository = repository(jdbcTemplate);

        assertEquals(List.of(event), repository.listEvents(runId));
        assertEquals(List.of(snapshot), repository.listSnapshots(runId));
        assertEquals(Optional.of(report), repository.findLatestReport(runId));
        assertTrue(jdbcTemplate.querySqls.get(0).contains("ORDER BY created_at ASC"));
        assertTrue(jdbcTemplate.querySqls.get(1).contains("ORDER BY snapshot_type ASC"));
        assertTrue(jdbcTemplate.querySqls.get(2).contains("ORDER BY generated_at DESC"));
    }

    @Test
    void shouldListShadowRunsWithBoundedFiltersAndCount() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        ShadowRun run = run(ShadowRunStatus.COMPLETED, 2);
        jdbcTemplate.queryResults.add(List.of(run));
        jdbcTemplate.queryForObjectResults.add(1L);
        JdbcShadowRunFactRepository repository = repository(jdbcTemplate);
        ShadowRunListQuery query = new ShadowRunListQuery(
                ShadowRunStatus.COMPLETED,
                "sv-1",
                run.datasetId(),
                "paper-1",
                25,
                5
        );

        assertEquals(List.of(run), repository.listRuns(query));
        assertEquals(1L, repository.countRuns(query));

        assertTrue(jdbcTemplate.querySqls.getFirst().contains("WHERE status = ?"));
        assertTrue(jdbcTemplate.querySqls.getFirst().contains("strategy_version_id = ?"));
        assertTrue(jdbcTemplate.querySqls.getFirst().contains("dataset_id = ?"));
        assertTrue(jdbcTemplate.querySqls.getFirst().contains("paper_run_id = ?"));
        assertTrue(jdbcTemplate.querySqls.getFirst().contains("ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?"));
        assertEquals(ShadowRunStatus.COMPLETED.name(), jdbcTemplate.queryArgs.getFirst()[0]);
        assertEquals("sv-1", jdbcTemplate.queryArgs.getFirst()[1]);
        assertEquals(run.datasetId(), jdbcTemplate.queryArgs.getFirst()[2]);
        assertEquals("paper-1", jdbcTemplate.queryArgs.getFirst()[3]);
        assertEquals(25, jdbcTemplate.queryArgs.getFirst()[4]);
        assertEquals(5, jdbcTemplate.queryArgs.getFirst()[5]);
        assertTrue(jdbcTemplate.queryForObjectSqls.getFirst().startsWith("SELECT COUNT(*) FROM shadow_runs WHERE"));
        assertFalse(jdbcTemplate.queryForObjectSqls.getFirst().contains("LIMIT"));
    }

    @Test
    void shouldLoadOverviewFactsWithSelectOnlyQueriesFromShadowTables() {
        RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
        ShadowRun run = run(ShadowRunStatus.COMPLETED, 2);
        ShadowConsistencyReport report = report(run.id());
        ShadowRunOverviewEvidenceFact eventAnchor = new ShadowRunOverviewEvidenceFact(
                "SHADOW_EVENT",
                UUID.randomUUID().toString(),
                ShadowRunEventType.COMPLETED.name(),
                NOW,
                null
        );
        ShadowRunOverviewEvidenceFact snapshotAnchor = new ShadowRunOverviewEvidenceFact(
                "SHADOW_SNAPSHOT",
                UUID.randomUUID().toString(),
                "shadow-order-intent-preview.v1",
                NOW,
                "sha256-demo"
        );
        for (long count : List.of(5L, 1L, 1L, 1L, 2L, 1L)) {
            jdbcTemplate.queryForObjectResults.add(count);
        }
        jdbcTemplate.queryResults.add(List.of(run));
        jdbcTemplate.queryResults.add(List.of(report));
        jdbcTemplate.queryResults.add(List.of(eventAnchor));
        jdbcTemplate.queryResults.add(List.of(snapshotAnchor));
        JdbcShadowRunOverviewQueryRepository repository = new JdbcShadowRunOverviewQueryRepository(
                jdbcTemplate,
                OBJECT_MAPPER
        );

        ShadowRunOverviewFacts facts = repository.loadOverviewFacts();

        assertEquals(5, facts.totalRuns());
        assertEquals(1, facts.runningRuns());
        assertEquals(1, facts.blockedRuns());
        assertEquals(1, facts.failedRuns());
        assertEquals(2, facts.completedRuns());
        assertEquals(1, facts.staleRuns());
        assertEquals(Optional.of(run), facts.latestRun());
        assertEquals(Optional.of(report), facts.latestConsistency());
        assertEquals(Optional.of(eventAnchor), facts.latestEvent());
        assertEquals(Optional.of(snapshotAnchor), facts.latestSnapshot());
        assertTrue(jdbcTemplate.updateSqls.isEmpty(), "overview repository must not execute writes");

        String sql = String.join("\n", jdbcTemplate.queryForObjectSqls) + "\n" + String.join("\n", jdbcTemplate.querySqls);
        String normalized = sql.toLowerCase(Locale.ROOT);
        assertTrue(normalized.contains("from shadow_runs"));
        assertTrue(normalized.contains("from shadow_run_events"));
        assertTrue(normalized.contains("from shadow_run_snapshots"));
        assertTrue(normalized.contains("from shadow_consistency_reports"));
        assertTrue(normalized.contains("order by updated_at desc, created_at desc, id desc limit 1"));
        assertTrue(normalized.contains("order by generated_at desc, created_at desc, id desc limit 1"));
        assertFalse(normalized.contains("insert "));
        assertFalse(normalized.contains("update "));
        assertFalse(normalized.contains("delete "));
        for (String forbiddenTable : List.of(
                "from exchange_credentials",
                "from trading_accounts",
                "from orders",
                "from order_",
                "from ledger",
                "from paper_trading_runs",
                "from strategy_versions",
                "from marketdata_",
                "from paper_run_alerts",
                "from trade_replay_records"
        )) {
            assertFalse(normalized.contains(forbiddenTable), "overview SQL must not read " + forbiddenTable + ": " + sql);
        }
    }

    @Test
    void shouldRejectCredentialLikeSnapshotPayloadBeforeRepositoryWrite() {
        ObjectNode forbiddenPayload = JsonNodeFactory.instance.objectNode();
        forbiddenPayload.put("apiKey", "redacted");

        assertThrows(IllegalArgumentException.class, () -> new ShadowRunSnapshot(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ShadowRunSnapshotType.INPUT_MARKETDATA,
                1,
                "dataset",
                "v1",
                "sha256-demo",
                forbiddenPayload,
                NOW,
                "trace-1",
                NOW
        ));
    }

    private JdbcShadowRunFactRepository repository(RecordingJdbcTemplate jdbcTemplate) {
        return repository(jdbcTemplate, new RecordingTransactionManager());
    }

    private JdbcShadowRunFactRepository repository(
            RecordingJdbcTemplate jdbcTemplate,
            RecordingTransactionManager transactionManager
    ) {
        JdbcShadowRunIllegalTransitionAuditWriter auditWriter = new JdbcShadowRunIllegalTransitionAuditWriter(
                jdbcTemplate,
                OBJECT_MAPPER,
                transactionManager
        );
        return new JdbcShadowRunFactRepository(
                jdbcTemplate,
                OBJECT_MAPPER,
                new ShadowRunStateMachine(),
                auditWriter
        );
    }

    private ShadowRun run(ShadowRunStatus status, long version) {
        UUID runId = UUID.randomUUID();
        return new ShadowRun(
                runId,
                "sv-1",
                UUID.randomUUID(),
                "eval-1",
                "pub-1",
                "paper-1",
                status,
                NOW,
                NOW.plusSeconds(60),
                JsonNodeFactory.instance.objectNode().put("mode", "NO_SIDE_EFFECT_LOCAL_ONLY"),
                true,
                true,
                true,
                true,
                true,
                true,
                ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY,
                "req-1",
                "shadow-run-idem-" + runId,
                "trace-1",
                JsonNodeFactory.instance.arrayNode().add("missing-local-runner"),
                JsonNodeFactory.instance.arrayNode().add("review-required"),
                JsonNodeFactory.instance.arrayNode().add("manual-review"),
                version,
                NOW,
                NOW,
                null,
                null,
                null
        );
    }

    private ShadowRunEvent event(UUID runId, ShadowRunEventType eventType) {
        return new ShadowRunEvent(
                UUID.randomUUID(),
                runId,
                eventType,
                null,
                ShadowRunStatus.CREATED,
                "CREATED",
                "created local shadow run",
                JsonNodeFactory.instance.objectNode().put("safe", true),
                "req-1",
                "trace-1",
                NOW
        );
    }

    private ShadowRunSnapshot snapshot(UUID runId) {
        return new ShadowRunSnapshot(
                UUID.randomUUID(),
                runId,
                ShadowRunSnapshotType.INPUT_MARKETDATA,
                1,
                "dataset",
                "v1",
                "sha256-demo",
                JsonNodeFactory.instance.objectNode().put("barCount", 1),
                NOW,
                "trace-1",
                NOW
        );
    }

    private ShadowConsistencyReport report(UUID runId) {
        return new ShadowConsistencyReport(
                UUID.randomUUID(),
                runId,
                "paper-1",
                ShadowConsistencyComparisonStatus.NOT_COMPARABLE,
                JsonNodeFactory.instance.objectNode().put("returnDelta", "NOT_AVAILABLE"),
                JsonNodeFactory.instance.arrayNode().add("shadow-runner-not-implemented"),
                JsonNodeFactory.instance.arrayNode().add("local fact model only"),
                NOW,
                "trace-1",
                NOW
        );
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {

        private final Queue<List<?>> queryResults = new ArrayDeque<>();
        private final Queue<Integer> updateCounts = new ArrayDeque<>();
        private final List<String> updateSqls = new ArrayList<>();
        private final List<Object[]> updateArgs = new ArrayList<>();
        private final List<String> querySqls = new ArrayList<>();
        private final List<Object[]> queryArgs = new ArrayList<>();
        private final Queue<Long> queryForObjectResults = new ArrayDeque<>();
        private final List<String> queryForObjectSqls = new ArrayList<>();

        @Override
        public int update(String sql, Object... args) {
            updateSqls.add(sql);
            updateArgs.add(args);
            return updateCounts.isEmpty() ? 1 : updateCounts.remove();
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            querySqls.add(sql);
            queryArgs.add(args);
            @SuppressWarnings("unchecked")
            List<T> rows = (List<T>) (queryResults.isEmpty() ? List.of() : queryResults.remove());
            return rows;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            queryForObjectSqls.add(sql);
            Long value = queryForObjectResults.isEmpty() ? 0L : queryForObjectResults.remove();
            return requiredType.cast(value);
        }
    }

    private static final class RecordingTransactionManager implements org.springframework.transaction.PlatformTransactionManager {

        private final List<Integer> propagationBehaviors = new ArrayList<>();

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            propagationBehaviors.add(definition.getPropagationBehavior());
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
