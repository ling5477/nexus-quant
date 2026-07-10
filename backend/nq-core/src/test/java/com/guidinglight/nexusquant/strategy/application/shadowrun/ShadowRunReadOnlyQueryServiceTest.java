package com.guidinglight.nexusquant.strategy.application.shadowrun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata.Availability;
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata.FreshnessStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.strategy.domain.port.ShadowRunFactRepository;
import com.guidinglight.nexusquant.strategy.domain.port.ShadowRunListQuery;
import com.guidinglight.nexusquant.strategy.domain.port.ShadowRunOverviewEvidenceFact;
import com.guidinglight.nexusquant.strategy.domain.port.ShadowRunOverviewFacts;
import com.guidinglight.nexusquant.strategy.domain.port.ShadowRunOverviewQueryPort;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowConsistencyComparisonStatus;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowConsistencyReport;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRun;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunAuthorizationBoundary;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunEvent;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunEventType;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunSnapshot;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunSnapshotType;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStatus;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStatusUpdateResult;

import java.time.Clock;
import java.lang.reflect.Field;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ShadowRunReadOnlyQueryServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final UUID RUN_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID DATASET_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final Instant NOW = Instant.parse("2026-07-06T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC);

    @Test
    void shouldReadDetailEventsSnapshotsAndLatestReportWithoutMutatingFacts() {
        InMemoryShadowRunFactRepository repository = repositoryWithRun();
        ShadowRunEvent event = event();
        ShadowRunSnapshot snapshot = snapshot();
        ShadowConsistencyReport report = report(NOW.plusSeconds(5));
        repository.events.add(event);
        repository.snapshots.add(snapshot);
        repository.reports.add(report);
        ShadowRunReadOnlyQueryService service = new ShadowRunReadOnlyQueryService(repository);

        assertEquals(repository.run, service.getDetail(RUN_ID));
        assertEquals(List.of(event), service.listEvents(RUN_ID));
        assertEquals(List.of(snapshot), service.listSnapshots(RUN_ID));
        assertEquals(report, service.getLatestConsistencyReport(RUN_ID));

        assertEquals(4, repository.findByIdCalls);
        assertEquals(1, repository.listEventsCalls);
        assertEquals(1, repository.listSnapshotsCalls);
        assertEquals(1, repository.findLatestReportCalls);
        assertEquals(0, repository.createCalls);
        assertEquals(0, repository.appendEventCalls);
        assertEquals(0, repository.appendSnapshotCalls);
        assertEquals(0, repository.createReportCalls);
        assertEquals(0, repository.updateStatusCalls);
    }

    @Test
    void shouldListShadowRunsWithFiltersWithoutMutatingFacts() {
        InMemoryShadowRunFactRepository repository = repositoryWithRun();
        ShadowRun blockedRun = run(
                UUID.fromString("66666666-6666-6666-6666-666666666667"),
                "sv-2",
                "paper-2",
                ShadowRunStatus.BLOCKED
        );
        repository.runs.put(blockedRun.id(), blockedRun);
        ShadowRunReadOnlyQueryService service = new ShadowRunReadOnlyQueryService(repository);
        ShadowRunListQuery query = new ShadowRunListQuery(
                ShadowRunStatus.COMPLETED,
                "sv-1",
                DATASET_ID,
                "paper-1",
                50,
                0
        );

        ShadowRunListResult result = service.list(query);

        assertEquals(List.of(repository.run), result.items());
        assertEquals(50, result.limit());
        assertEquals(0, result.offset());
        assertEquals(1, result.total());
        assertEquals(1, repository.listRunsCalls);
        assertEquals(1, repository.countRunsCalls);
        assertEquals(0, repository.createCalls);
        assertEquals(0, repository.appendEventCalls);
        assertEquals(0, repository.appendSnapshotCalls);
        assertEquals(0, repository.createReportCalls);
        assertEquals(0, repository.updateStatusCalls);
    }

    @Test
    void shouldReturnClearNotFoundForMissingRunAndMissingLatestReport() {
        InMemoryShadowRunFactRepository missingRunRepository = new InMemoryShadowRunFactRepository();
        ShadowRunReadOnlyQueryService missingRunService = new ShadowRunReadOnlyQueryService(missingRunRepository);

        ShadowRunReadOnlyNotFoundException runException = assertThrows(
                ShadowRunReadOnlyNotFoundException.class,
                () -> missingRunService.getDetail(RUN_ID)
        );
        assertEquals("shadow run not found: " + RUN_ID, runException.getMessage());
        assertEquals(1, missingRunRepository.findByIdCalls);
        assertEquals(0, missingRunRepository.listEventsCalls);

        InMemoryShadowRunFactRepository repository = repositoryWithRun();
        ShadowRunReadOnlyQueryService service = new ShadowRunReadOnlyQueryService(repository);

        ShadowRunReadOnlyNotFoundException reportException = assertThrows(
                ShadowRunReadOnlyNotFoundException.class,
                () -> service.getLatestConsistencyReport(RUN_ID)
        );
        assertEquals("shadow consistency report not found: " + RUN_ID, reportException.getMessage());
        assertEquals(1, repository.findByIdCalls);
        assertEquals(1, repository.findLatestReportCalls);
    }

    @Test
    void shouldKeepServiceDependencyAwayFromRunnerAdapterAccountLedgerAndOrderPorts() {
        List<String> dependencyNames = List.of(ShadowRunReadOnlyQueryService.class.getDeclaredFields()).stream()
                .filter(field -> !field.isSynthetic())
                .map(Field::getType)
                .map(Class::getName)
                .toList();

        assertEquals(List.of(ShadowRunFactRepository.class.getName()), dependencyNames);
        String joined = String.join("|", dependencyNames).toLowerCase();
        assertFalse(joined.contains("runner"));
        assertFalse(joined.contains("adapter"));
        assertFalse(joined.contains("account"));
        assertFalse(joined.contains("ledger"));
        assertFalse(joined.contains("order"));
        assertFalse(joined.contains("client"));
    }

    @Test
    void shouldReturnSafeOverviewForEmptyDataWithoutMutatingFacts() {
        InMemoryShadowRunOverviewQueryPort queryPort = new InMemoryShadowRunOverviewQueryPort(ShadowRunOverviewFacts.empty());
        ShadowRunOverviewQueryService service = new ShadowRunOverviewQueryService(queryPort, FIXED_CLOCK);

        ShadowRunOverviewReadModel overview = service.overview("trace-empty");

        assertEquals(NOW.plusSeconds(60), overview.generatedAt());
        assertEquals("LOCAL_DB_SHADOW_FACTS", overview.evidenceMetadata().source());
        assertEquals(Availability.UNAVAILABLE, overview.evidenceMetadata().availability());
        assertEquals(FreshnessStatus.UNKNOWN, overview.evidenceMetadata().freshnessStatus());
        assertEquals(null, overview.evidenceMetadata().lastCalculatedAt());
        assertTrue(overview.diagnosticOnly());
        assertTrue(overview.noSideEffect());
        assertTrue(overview.notTradingAuthorization());
        assertTrue(overview.liveDisabled());
        assertFalse(overview.realProviderImplemented());
        assertFalse(overview.privateTradingImplemented());
        assertFalse(overview.aiDhRuntimeIntegrated());
        assertEquals(0, overview.totalRuns());
        assertEquals(0, overview.staleRuns());
        assertEquals(ShadowRunOverviewDivergenceSeverity.UNKNOWN, overview.divergenceSeverity());
        assertEquals(null, overview.latestRun());
        assertEquals(null, overview.latestConsistency());
        assertTrue(overview.blockers().stream().anyMatch(message -> message.code().equals("LIVE_DISABLED")));
        assertTrue(overview.blockers().stream().anyMatch(message -> message.code().equals("REAL_PROVIDER_NOT_IMPLEMENTED")));
        assertTrue(overview.blockers().stream().anyMatch(message -> message.code().equals("PRIVATE_TRADING_NOT_IMPLEMENTED")));
        assertTrue(overview.warnings().stream().anyMatch(message -> message.code().equals("SHADOW_RUN_DIAGNOSTIC_ONLY")));
        assertTrue(overview.warnings().stream().anyMatch(message -> message.code().equals("SHADOW_RUN_MISSING")));
        assertTrue(overview.warnings().stream().anyMatch(message -> message.code().equals("CONSISTENCY_REPORT_MISSING")));
        assertTrue(overview.nextSteps().stream().allMatch(step -> !step.action().toLowerCase().contains("trade")));
        assertEquals(1, queryPort.loadCalls);
    }

    @Test
    void shouldMapOverviewCountsLatestFactsAnchorsAndHighDivergenceSeverity() {
        ShadowConsistencyReport report = report(NOW.plusSeconds(30), ShadowConsistencyComparisonStatus.DIVERGED);
        ShadowRunOverviewFacts facts = facts(
                5,
                1,
                1,
                1,
                2,
                1,
                Optional.of(run()),
                Optional.of(report)
        );
        ShadowRunOverviewQueryService service = new ShadowRunOverviewQueryService(
                new InMemoryShadowRunOverviewQueryPort(facts),
                FIXED_CLOCK
        );

        ShadowRunOverviewReadModel overview = service.overview("trace-overview");

        assertEquals(5, overview.totalRuns());
        assertEquals(1, overview.runningRuns());
        assertEquals(1, overview.blockedRuns());
        assertEquals(1, overview.failedRuns());
        assertEquals(2, overview.completedRuns());
        assertEquals(1, overview.staleRuns());
        assertEquals(Availability.PARTIAL, overview.evidenceMetadata().availability());
        assertEquals(FreshnessStatus.UNKNOWN, overview.evidenceMetadata().freshnessStatus());
        assertEquals(null, overview.evidenceMetadata().staleAfterSeconds());
        assertEquals("SOURCE_PARTIAL", overview.evidenceMetadata().staleReason());
        assertEquals(RUN_ID, overview.latestRun().shadowRunId());
        assertEquals("COMPLETED", overview.latestRun().status());
        assertEquals(report.id(), overview.latestConsistency().reportId());
        assertEquals("DIVERGED", overview.latestConsistency().comparisonStatus());
        assertEquals(ShadowRunOverviewDivergenceSeverity.HIGH, overview.divergenceSeverity());
        assertTrue(overview.warnings().stream().anyMatch(message -> message.code().equals("STALE_EVIDENCE")));
        assertTrue(overview.nextSteps().stream().anyMatch(step -> step.action().equals("compare_paper_shadow_report")));
        assertTrue(overview.evidenceAnchors().stream().anyMatch(anchor -> anchor.sourceType().equals("SHADOW_RUN")));
        assertTrue(overview.evidenceAnchors().stream().anyMatch(anchor -> anchor.sourceType().equals("SHADOW_CONSISTENCY_REPORT")));
        assertTrue(overview.evidenceAnchors().stream().anyMatch(anchor -> anchor.sourceType().equals("SHADOW_EVENT")));
        assertTrue(overview.evidenceAnchors().stream().anyMatch(anchor -> anchor.sourceType().equals("SHADOW_SNAPSHOT")));
    }

    @Test
    void shouldMapComparisonStatusToDivergenceSeverityWithoutChangingRunStatus() {
        Map<ShadowConsistencyComparisonStatus, ShadowRunOverviewDivergenceSeverity> expected = Map.of(
                ShadowConsistencyComparisonStatus.CONSISTENT, ShadowRunOverviewDivergenceSeverity.NONE,
                ShadowConsistencyComparisonStatus.NOT_COMPARABLE, ShadowRunOverviewDivergenceSeverity.MEDIUM,
                ShadowConsistencyComparisonStatus.DIVERGED, ShadowRunOverviewDivergenceSeverity.HIGH,
                ShadowConsistencyComparisonStatus.FAILED, ShadowRunOverviewDivergenceSeverity.CRITICAL
        );

        for (Map.Entry<ShadowConsistencyComparisonStatus, ShadowRunOverviewDivergenceSeverity> entry : expected.entrySet()) {
            ShadowRunOverviewQueryService service = new ShadowRunOverviewQueryService(
                    new InMemoryShadowRunOverviewQueryPort(facts(
                            1,
                            0,
                            0,
                            0,
                            1,
                            0,
                            Optional.of(run()),
                            Optional.of(report(NOW, entry.getKey()))
                    )),
                    FIXED_CLOCK
            );

            ShadowRunOverviewReadModel overview = service.overview("trace-" + entry.getKey().name());

            assertEquals(entry.getValue(), overview.divergenceSeverity());
            assertEquals("COMPLETED", overview.latestRun().status());
        }

        ShadowRunOverviewQueryService partialWithoutReasons = new ShadowRunOverviewQueryService(
                new InMemoryShadowRunOverviewQueryPort(facts(
                        1,
                        0,
                        0,
                        0,
                        1,
                        0,
                        Optional.of(run()),
                        Optional.of(report(NOW, ShadowConsistencyComparisonStatus.PARTIAL, false))
                )),
                FIXED_CLOCK
        );
        ShadowRunOverviewQueryService partialWithReasons = new ShadowRunOverviewQueryService(
                new InMemoryShadowRunOverviewQueryPort(facts(
                        1,
                        0,
                        0,
                        0,
                        1,
                        0,
                        Optional.of(run()),
                        Optional.of(report(NOW, ShadowConsistencyComparisonStatus.PARTIAL, true))
                )),
                FIXED_CLOCK
        );

        assertEquals(ShadowRunOverviewDivergenceSeverity.LOW, partialWithoutReasons.overview("trace-partial-low").divergenceSeverity());
        assertEquals(ShadowRunOverviewDivergenceSeverity.MEDIUM, partialWithReasons.overview("trace-partial-medium").divergenceSeverity());
    }

    @Test
    void shouldKeepOverviewServiceDependencyAwayFromRunnerAdapterAccountLedgerAndOrderPorts() {
        List<String> dependencyNames = List.of(ShadowRunOverviewQueryService.class.getDeclaredFields()).stream()
                .filter(field -> !field.isSynthetic())
                .map(Field::getType)
                .map(Class::getName)
                .toList();

        assertEquals(List.of(ShadowRunOverviewQueryPort.class.getName(), Clock.class.getName()), dependencyNames);
        String joined = String.join("|", dependencyNames).toLowerCase();
        assertFalse(joined.contains("runner"));
        assertFalse(joined.contains("adapter"));
        assertFalse(joined.contains("account"));
        assertFalse(joined.contains("ledger"));
        assertFalse(joined.contains("order"));
        assertFalse(joined.contains("client"));
    }

    private InMemoryShadowRunFactRepository repositoryWithRun() {
        InMemoryShadowRunFactRepository repository = new InMemoryShadowRunFactRepository();
        repository.run = run();
        repository.runs.put(RUN_ID, repository.run);
        return repository;
    }

    private ShadowRun run() {
        return run(RUN_ID, "sv-1", "paper-1", ShadowRunStatus.COMPLETED);
    }

    private ShadowRun run(UUID runId, String strategyVersionId, String paperRunId, ShadowRunStatus status) {
        return new ShadowRun(
                runId,
                strategyVersionId,
                DATASET_ID,
                "eval-1",
                "pub-1",
                paperRunId,
                status,
                NOW.minusSeconds(3600),
                NOW,
                OBJECT_MAPPER.createObjectNode().put("mode", "NO_SIDE_EFFECT_LOCAL_ONLY"),
                true,
                true,
                true,
                true,
                true,
                true,
                ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY,
                "req-shadow",
                "idem-shadow",
                "trace-shadow",
                OBJECT_MAPPER.createArrayNode(),
                OBJECT_MAPPER.createArrayNode().add("diagnostic only"),
                OBJECT_MAPPER.createArrayNode().add("review replay"),
                3,
                NOW.minusSeconds(3600),
                NOW,
                NOW.minusSeconds(3500),
                null,
                NOW
        );
    }

    private ShadowRunEvent event() {
        return new ShadowRunEvent(
                UUID.randomUUID(),
                RUN_ID,
                ShadowRunEventType.COMPLETED,
                ShadowRunStatus.RUNNING,
                ShadowRunStatus.COMPLETED,
                "COMPLETED",
                "local shadow run completed",
                OBJECT_MAPPER.createObjectNode().put("diagnosticOnly", true),
                "req-shadow",
                "trace-shadow",
                NOW
        );
    }

    private ShadowRunSnapshot snapshot() {
        return new ShadowRunSnapshot(
                UUID.randomUUID(),
                RUN_ID,
                ShadowRunSnapshotType.ORDER_INTENT_PREVIEW,
                4,
                "LOCAL_CALLER_SUPPLIED_READONLY_INPUT",
                "shadow-order-intent-preview.v1",
                "sha256-demo",
                OBJECT_MAPPER.createObjectNode().put("previewOnly", true),
                NOW,
                "trace-shadow",
                NOW
        );
    }

    private ShadowConsistencyReport report(Instant generatedAt) {
        return report(generatedAt, ShadowConsistencyComparisonStatus.CONSISTENT);
    }

    private ShadowConsistencyReport report(Instant generatedAt, ShadowConsistencyComparisonStatus status) {
        return report(generatedAt, status, true);
    }

    private ShadowConsistencyReport report(
            Instant generatedAt,
            ShadowConsistencyComparisonStatus status,
            boolean includeReason
    ) {
        return new ShadowConsistencyReport(
                UUID.randomUUID(),
                RUN_ID,
                "paper-1",
                status,
                OBJECT_MAPPER.createObjectNode().put("schemaVersion", "shadow-consistency-report.v1"),
                includeReason
                        ? OBJECT_MAPPER.createArrayNode().add("paper-shadow-divergence")
                        : OBJECT_MAPPER.createArrayNode(),
                OBJECT_MAPPER.createArrayNode().add("diagnostic report only"),
                generatedAt,
                "trace-shadow",
                generatedAt
        );
    }

    private ShadowRunOverviewFacts facts(
            long totalRuns,
            long runningRuns,
            long blockedRuns,
            long failedRuns,
            long completedRuns,
            long staleRuns,
            Optional<ShadowRun> latestRun,
            Optional<ShadowConsistencyReport> latestReport
    ) {
        return new ShadowRunOverviewFacts(
                totalRuns,
                runningRuns,
                blockedRuns,
                failedRuns,
                completedRuns,
                staleRuns,
                latestRun,
                latestReport,
                Optional.of(new ShadowRunOverviewEvidenceFact(
                        "SHADOW_EVENT",
                        UUID.randomUUID().toString(),
                        "COMPLETED",
                        NOW,
                        null
                )),
                Optional.of(new ShadowRunOverviewEvidenceFact(
                        "SHADOW_SNAPSHOT",
                        UUID.randomUUID().toString(),
                        "shadow-order-intent-preview.v1",
                        NOW,
                        "sha256-demo"
                ))
        );
    }

    private static final class InMemoryShadowRunFactRepository implements ShadowRunFactRepository {

        private final Map<UUID, ShadowRun> runs = new LinkedHashMap<>();
        private final List<ShadowRunEvent> events = new ArrayList<>();
        private final List<ShadowRunSnapshot> snapshots = new ArrayList<>();
        private final List<ShadowConsistencyReport> reports = new ArrayList<>();
        private ShadowRun run;
        private int findByIdCalls;
        private int listRunsCalls;
        private int countRunsCalls;
        private int listEventsCalls;
        private int listSnapshotsCalls;
        private int findLatestReportCalls;
        private int createCalls;
        private int appendEventCalls;
        private int appendSnapshotCalls;
        private int createReportCalls;
        private int updateStatusCalls;

        @Override
        public ShadowRun create(ShadowRun run) {
            createCalls++;
            throw new UnsupportedOperationException("read-only query service must not create shadow runs");
        }

        @Override
        public Optional<ShadowRun> findById(UUID shadowRunId) {
            findByIdCalls++;
            return Optional.ofNullable(runs.get(shadowRunId));
        }

        @Override
        public Optional<ShadowRun> findByIdempotencyKey(String idempotencyKey) {
            return runs.values().stream()
                    .filter(value -> value.idempotencyKey().equals(idempotencyKey))
                    .findFirst();
        }

        @Override
        public List<ShadowRun> listRuns(ShadowRunListQuery query) {
            listRunsCalls++;
            return runs.values().stream()
                    .filter(run -> query.status() == null || run.status() == query.status())
                    .filter(run -> query.strategyVersionId() == null
                            || run.strategyVersionId().equals(query.strategyVersionId()))
                    .filter(run -> query.datasetId() == null || run.datasetId().equals(query.datasetId()))
                    .filter(run -> query.paperRunId() == null || run.paperRunId().equals(query.paperRunId()))
                    .skip(query.offset())
                    .limit(query.limit())
                    .toList();
        }

        @Override
        public long countRuns(ShadowRunListQuery query) {
            countRunsCalls++;
            return runs.values().stream()
                    .filter(run -> query.status() == null || run.status() == query.status())
                    .filter(run -> query.strategyVersionId() == null
                            || run.strategyVersionId().equals(query.strategyVersionId()))
                    .filter(run -> query.datasetId() == null || run.datasetId().equals(query.datasetId()))
                    .filter(run -> query.paperRunId() == null || run.paperRunId().equals(query.paperRunId()))
                    .count();
        }

        @Override
        public void appendEvent(ShadowRunEvent event) {
            appendEventCalls++;
            throw new UnsupportedOperationException("read-only query service must not append events");
        }

        @Override
        public void appendSnapshot(ShadowRunSnapshot snapshot) {
            appendSnapshotCalls++;
            throw new UnsupportedOperationException("read-only query service must not append snapshots");
        }

        @Override
        public ShadowConsistencyReport createConsistencyReport(ShadowConsistencyReport report) {
            createReportCalls++;
            throw new UnsupportedOperationException("read-only query service must not create reports");
        }

        @Override
        public ShadowRunStatusUpdateResult updateStatus(
                UUID shadowRunId,
                ShadowRunStatus toStatus,
                long expectedVersion,
                String reasonCode,
                String message,
                String requestId,
                String traceId
        ) {
            updateStatusCalls++;
            throw new UnsupportedOperationException("read-only query service must not update status");
        }

        @Override
        public List<ShadowRunEvent> listEvents(UUID shadowRunId) {
            listEventsCalls++;
            return events.stream().filter(event -> event.shadowRunId().equals(shadowRunId)).toList();
        }

        @Override
        public List<ShadowRunSnapshot> listSnapshots(UUID shadowRunId) {
            listSnapshotsCalls++;
            return snapshots.stream().filter(snapshot -> snapshot.shadowRunId().equals(shadowRunId)).toList();
        }

        @Override
        public Optional<ShadowConsistencyReport> findLatestReport(UUID shadowRunId) {
            findLatestReportCalls++;
            return reports.stream()
                    .filter(report -> report.shadowRunId().equals(shadowRunId))
                    .max(Comparator.comparing(ShadowConsistencyReport::generatedAt)
                            .thenComparing(ShadowConsistencyReport::createdAt)
                            .thenComparing(ShadowConsistencyReport::id));
        }
    }

    private static final class InMemoryShadowRunOverviewQueryPort implements ShadowRunOverviewQueryPort {

        private final ShadowRunOverviewFacts facts;
        private int loadCalls;

        private InMemoryShadowRunOverviewQueryPort(ShadowRunOverviewFacts facts) {
            this.facts = facts;
        }

        @Override
        public ShadowRunOverviewFacts loadOverviewFacts() {
            loadCalls++;
            return facts;
        }
    }
}
