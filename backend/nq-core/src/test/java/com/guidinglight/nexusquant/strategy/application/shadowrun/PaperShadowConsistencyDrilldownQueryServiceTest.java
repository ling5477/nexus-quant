package com.guidinglight.nexusquant.strategy.application.shadowrun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.strategy.domain.port.PaperShadowConsistencyDrilldownFacts;
import com.guidinglight.nexusquant.strategy.domain.port.PaperShadowConsistencyDrilldownFacts.LatestEventFact;
import com.guidinglight.nexusquant.strategy.domain.port.PaperShadowConsistencyDrilldownFacts.LatestSnapshotFact;
import com.guidinglight.nexusquant.strategy.domain.port.PaperShadowConsistencyDrilldownFacts.SnapshotFacts;
import com.guidinglight.nexusquant.strategy.domain.port.PaperShadowConsistencyDrilldownQueryPort;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowConsistencyComparisonStatus;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowConsistencyReport;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRun;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunAuthorizationBoundary;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStatus;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PaperShadowConsistencyDrilldownQueryServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final UUID RUN_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DATASET_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant NOW = Instant.parse("2026-07-07T08:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC);

    @Test
    void shouldReturnReportDetailsSnapshotSummaryEventSummaryAndBoundaryFlags() {
        PaperShadowConsistencyDrilldownFacts facts = facts(
                Optional.of(run()),
                Optional.of(report(ShadowConsistencyComparisonStatus.DIVERGED, true)),
                completeSnapshots(),
                3
        );
        PaperShadowConsistencyDrilldownQueryService service = new PaperShadowConsistencyDrilldownQueryService(
                new InMemoryDrilldownQueryPort(facts),
                FIXED_CLOCK
        );

        PaperShadowConsistencyDrilldownReadModel model = service.drilldown(RUN_ID, "trace-drilldown");

        assertEquals(NOW.plusSeconds(60), model.generatedAt());
        assertTrue(model.diagnosticOnly());
        assertTrue(model.noSideEffect());
        assertTrue(model.notTradingAuthorization());
        assertTrue(model.liveDisabled());
        assertFalse(model.realProviderImplemented());
        assertFalse(model.privateTradingImplemented());
        assertFalse(model.aiDhRuntimeIntegrated());
        assertEquals(RUN_ID, model.shadowRun().shadowRunId());
        assertEquals("sv-1", model.shadowRun().strategyVersionId());
        assertEquals("eval-1", model.shadowRun().evaluationId());
        assertEquals("pub-1", model.shadowRun().publishId());
        assertEquals("paper-1", model.shadowRun().paperRunId());
        assertEquals("COMPLETED", model.shadowRun().status());
        assertTrue(model.shadowRun().noOrderSubmission());
        assertTrue(model.shadowRun().noCredentialAccess());
        assertTrue(model.shadowRun().noPrivateEndpoint());
        assertTrue(model.shadowRun().noLedgerMutation());
        assertTrue(model.shadowRun().noAccountMutation());
        assertTrue(model.shadowRun().noExternalPrivateIo());
        assertEquals(PaperShadowConsistencyDrilldownComparisonStatus.DIVERGED, model.comparisonStatus());
        assertEquals(ShadowRunOverviewDivergenceSeverity.HIGH, model.divergenceSeverity());
        assertEquals("DIVERGED", model.latestConsistency().comparisonStatus());
        assertEquals(0.12, model.metricDelta().get("returnDelta").decimalValue().doubleValue());
        assertEquals("paper-shadow-diverged", model.divergenceReasons().get(0).asText());
        assertEquals("diagnostic report only", model.limitations().get(0).asText());
        assertEquals(4, model.snapshotSummary().totalSnapshots());
        assertEquals(1, model.snapshotSummary().inputMarketdataSnapshots());
        assertEquals(1, model.snapshotSummary().strategyDecisionSnapshots());
        assertEquals(1, model.snapshotSummary().riskPreflightSnapshots());
        assertEquals(1, model.snapshotSummary().orderIntentPreviewSnapshots());
        assertEquals(List.of("ORDER_INTENT_PREVIEW"), model.snapshotSummary().latestSnapshotTypes());
        assertEquals(3, model.eventSummary().totalEvents());
        assertEquals("COMPLETED", model.eventSummary().latestEventType());
        assertEquals("COMPLETED", model.eventSummary().latestReasonCode());
        assertTrue(model.blockers().stream().anyMatch(message -> message.code().equals("LIVE_DISABLED")));
        assertTrue(model.blockers().stream().anyMatch(message -> message.code().equals("REAL_PROVIDER_NOT_IMPLEMENTED")));
        assertTrue(model.blockers().stream().anyMatch(message -> message.code().equals("PRIVATE_TRADING_NOT_IMPLEMENTED")));
        assertTrue(model.blockers().stream().anyMatch(message -> message.code().equals("SHADOW_RUN_DIAGNOSTIC_ONLY")));
        assertTrue(model.blockers().stream().anyMatch(message -> message.code().equals("NOT_TRADING_AUTHORIZATION")));
        assertTrue(model.warnings().isEmpty());
        assertTrue(model.nextSteps().stream().anyMatch(step -> step.action().equals("Compare latest consistency report")));
        assertTrue(model.evidenceAnchors().stream().anyMatch(anchor -> anchor.sourceType().equals("SHADOW_RUN")));
        assertTrue(model.evidenceAnchors().stream().anyMatch(anchor -> anchor.sourceType().equals("SHADOW_CONSISTENCY_REPORT")));
        assertTrue(model.evidenceAnchors().stream().anyMatch(anchor -> anchor.sourceType().equals("SHADOW_EVENT")));
        assertTrue(model.evidenceAnchors().stream().anyMatch(anchor -> anchor.sourceType().equals("SHADOW_SNAPSHOT")));
        assertEquals("trace-drilldown", model.traceId());
    }

    @Test
    void shouldReturnNoReportAndWarningsWithoutGeneratingReport() {
        InMemoryDrilldownQueryPort queryPort = new InMemoryDrilldownQueryPort(facts(
                Optional.of(run()),
                Optional.empty(),
                incompleteSnapshots(),
                0
        ));
        PaperShadowConsistencyDrilldownQueryService service = new PaperShadowConsistencyDrilldownQueryService(
                queryPort,
                FIXED_CLOCK
        );

        PaperShadowConsistencyDrilldownReadModel model = service.drilldown(RUN_ID, "trace-no-report");

        assertEquals(PaperShadowConsistencyDrilldownComparisonStatus.NO_REPORT, model.comparisonStatus());
        assertEquals(ShadowRunOverviewDivergenceSeverity.UNKNOWN, model.divergenceSeverity());
        assertEquals(null, model.latestConsistency());
        assertTrue(model.metricDelta().isObject());
        assertEquals(0, model.divergenceReasons().size());
        assertEquals(0, model.limitations().size());
        assertTrue(model.warnings().stream().anyMatch(message -> message.code().equals("NO_CONSISTENCY_REPORT")));
        assertTrue(model.warnings().stream().anyMatch(message -> message.code().equals("INCOMPLETE_SNAPSHOT_EVIDENCE")));
        assertTrue(model.nextSteps().stream()
                .anyMatch(step -> step.action().equals("Generate or inspect consistency report in future GateS batch")));
        assertTrue(model.nextSteps().stream().anyMatch(step -> step.action().equals("Inspect shadow snapshots")));
        assertEquals(1, queryPort.loadCalls);
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
            PaperShadowConsistencyDrilldownQueryService service = new PaperShadowConsistencyDrilldownQueryService(
                    new InMemoryDrilldownQueryPort(facts(
                            Optional.of(run()),
                            Optional.of(report(entry.getKey(), true)),
                            completeSnapshots(),
                            1
                    )),
                    FIXED_CLOCK
            );

            PaperShadowConsistencyDrilldownReadModel model = service.drilldown(RUN_ID, "trace-" + entry.getKey().name());

            assertEquals(entry.getValue(), model.divergenceSeverity());
            assertEquals(entry.getKey().name(), model.comparisonStatus().name());
            assertEquals("COMPLETED", model.shadowRun().status());
        }

        PaperShadowConsistencyDrilldownQueryService partialWithoutReasons = new PaperShadowConsistencyDrilldownQueryService(
                new InMemoryDrilldownQueryPort(facts(
                        Optional.of(run()),
                        Optional.of(report(ShadowConsistencyComparisonStatus.PARTIAL, false)),
                        completeSnapshots(),
                        1
                )),
                FIXED_CLOCK
        );
        PaperShadowConsistencyDrilldownQueryService partialWithReasons = new PaperShadowConsistencyDrilldownQueryService(
                new InMemoryDrilldownQueryPort(facts(
                        Optional.of(run()),
                        Optional.of(report(ShadowConsistencyComparisonStatus.PARTIAL, true)),
                        completeSnapshots(),
                        1
                )),
                FIXED_CLOCK
        );

        assertEquals(ShadowRunOverviewDivergenceSeverity.LOW,
                partialWithoutReasons.drilldown(RUN_ID, "trace-partial-low").divergenceSeverity());
        assertEquals(ShadowRunOverviewDivergenceSeverity.MEDIUM,
                partialWithReasons.drilldown(RUN_ID, "trace-partial-medium").divergenceSeverity());
    }

    @Test
    void shouldReturnNotFoundForMissingShadowRun() {
        PaperShadowConsistencyDrilldownQueryService service = new PaperShadowConsistencyDrilldownQueryService(
                new InMemoryDrilldownQueryPort(PaperShadowConsistencyDrilldownFacts.missingRun()),
                FIXED_CLOCK
        );

        ShadowRunReadOnlyNotFoundException exception = assertThrows(
                ShadowRunReadOnlyNotFoundException.class,
                () -> service.drilldown(RUN_ID, "trace-missing")
        );

        assertEquals("shadow run not found: " + RUN_ID, exception.getMessage());
    }

    @Test
    void shouldKeepDrilldownServiceDependencyAwayFromRunnerAdapterAccountLedgerAndOrderPorts() {
        List<String> dependencyNames = List.of(PaperShadowConsistencyDrilldownQueryService.class.getDeclaredFields()).stream()
                .filter(field -> !field.isSynthetic())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(Field::getType)
                .map(Class::getName)
                .toList();

        assertEquals(List.of(PaperShadowConsistencyDrilldownQueryPort.class.getName(), Clock.class.getName()), dependencyNames);
        String joined = String.join("|", dependencyNames).toLowerCase();
        assertFalse(joined.contains("runner"));
        assertFalse(joined.contains("adapter"));
        assertFalse(joined.contains("account"));
        assertFalse(joined.contains("ledger"));
        assertFalse(joined.contains("order"));
        assertFalse(joined.contains("client"));
    }

    private PaperShadowConsistencyDrilldownFacts facts(
            Optional<ShadowRun> run,
            Optional<ShadowConsistencyReport> report,
            SnapshotFacts snapshots,
            long totalEvents
    ) {
        return new PaperShadowConsistencyDrilldownFacts(
                run,
                report,
                snapshots,
                totalEvents,
                totalEvents == 0
                        ? Optional.empty()
                        : Optional.of(new LatestEventFact("evt-1", "COMPLETED", "COMPLETED", NOW.plusSeconds(5))),
                snapshots.totalSnapshots() == 0
                        ? Optional.empty()
                        : Optional.of(new LatestSnapshotFact(
                        "snp-1",
                        "ORDER_INTENT_PREVIEW",
                        "shadow-order-intent-preview.v1",
                        NOW.plusSeconds(10),
                        "sha256-demo"
                ))
        );
    }

    private SnapshotFacts completeSnapshots() {
        return new SnapshotFacts(
                4,
                1,
                1,
                1,
                1,
                NOW.plusSeconds(10),
                List.of("ORDER_INTENT_PREVIEW")
        );
    }

    private SnapshotFacts incompleteSnapshots() {
        return new SnapshotFacts(1, 1, 0, 0, 0, NOW, List.of("INPUT_MARKETDATA"));
    }

    private ShadowRun run() {
        return new ShadowRun(
                RUN_ID,
                "sv-1",
                DATASET_ID,
                "eval-1",
                "pub-1",
                "paper-1",
                ShadowRunStatus.COMPLETED,
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
                "req-1",
                "idem-1",
                "trace-1",
                OBJECT_MAPPER.createArrayNode(),
                OBJECT_MAPPER.createArrayNode(),
                OBJECT_MAPPER.createArrayNode(),
                7,
                NOW.minusSeconds(3600),
                NOW,
                NOW.minusSeconds(3500),
                null,
                NOW
        );
    }

    private ShadowConsistencyReport report(ShadowConsistencyComparisonStatus status, boolean includeReason) {
        return new ShadowConsistencyReport(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                RUN_ID,
                "paper-1",
                status,
                OBJECT_MAPPER.createObjectNode().put("returnDelta", 0.12),
                includeReason
                        ? OBJECT_MAPPER.createArrayNode().add("paper-shadow-diverged")
                        : OBJECT_MAPPER.createArrayNode(),
                OBJECT_MAPPER.createArrayNode().add("diagnostic report only"),
                NOW.plusSeconds(30),
                "trace-report",
                NOW.plusSeconds(30)
        );
    }

    private static final class InMemoryDrilldownQueryPort implements PaperShadowConsistencyDrilldownQueryPort {

        private final PaperShadowConsistencyDrilldownFacts facts;
        private int loadCalls;

        private InMemoryDrilldownQueryPort(PaperShadowConsistencyDrilldownFacts facts) {
            this.facts = facts;
        }

        @Override
        public PaperShadowConsistencyDrilldownFacts loadDrilldownFacts(UUID shadowRunId) {
            loadCalls++;
            assertEquals(RUN_ID, shadowRunId);
            return facts;
        }
    }
}
