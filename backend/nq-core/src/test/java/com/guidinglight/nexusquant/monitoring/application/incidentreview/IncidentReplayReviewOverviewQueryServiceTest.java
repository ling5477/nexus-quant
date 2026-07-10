package com.guidinglight.nexusquant.monitoring.application.incidentreview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.monitoring.domain.port.IncidentReplayReviewOverviewFacts;
import com.guidinglight.nexusquant.monitoring.domain.port.IncidentReplayReviewOverviewFacts.ReviewEvidenceFact;
import com.guidinglight.nexusquant.monitoring.domain.port.IncidentReplayReviewOverviewQueryPort;
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata.Availability;
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata.FreshnessStatus;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class IncidentReplayReviewOverviewQueryServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-09T10:00:00Z"), ZoneOffset.UTC);
    private static final Instant FRESH_TIME = Instant.parse("2026-07-09T09:59:00Z");
    private static final Instant STALE_TIME = Instant.parse("2026-06-28T10:00:00Z");
    private static final Instant FUTURE_TIME = Instant.parse("2026-07-09T10:01:00Z");

    @Test
    void shouldReturnSafeOverviewForEmptyFactsWithoutCreatingReviewRecords() {
        IncidentReplayReviewOverviewReadModel model = service(IncidentReplayReviewOverviewFacts.empty())
                .overview("trace-empty");

        assertEquals(0, model.totalReviewItems());
        assertTrue(model.reviewItems().isEmpty());
        assertEquals("LOCAL_DB_INCIDENT_REPLAY_REVIEW", model.evidenceMetadata().source());
        assertEquals(Availability.UNAVAILABLE, model.evidenceMetadata().availability());
        assertEquals(FreshnessStatus.UNKNOWN, model.evidenceMetadata().freshnessStatus());
        assertEquals(null, model.evidenceMetadata().lastCalculatedAt());
        assertEquals(null, model.evidenceMetadata().ageSeconds());
        assertTrue(model.diagnosticOnly());
        assertTrue(model.noSideEffect());
        assertTrue(model.notTradingAuthorization());
        assertTrue(model.liveDisabled());
        assertFalse(model.realProviderImplemented());
        assertFalse(model.privateTradingImplemented());
        assertFalse(model.aiDhRuntimeIntegrated());
        assertHasMessage(model.blockers(), "LIVE_DISABLED");
        assertHasMessage(model.blockers(), "REAL_PROVIDER_NOT_IMPLEMENTED");
        assertHasMessage(model.blockers(), "PRIVATE_TRADING_NOT_IMPLEMENTED");
        assertHasMessage(model.blockers(), "NOT_TRADING_AUTHORIZATION");
        assertHasMessage(model.warnings(), "NO_REVIEW_EVIDENCE");
        assertHasNextStep(model.nextSteps(), "INSPECT_LOCAL_REVIEW_EVIDENCE");
        assertEquals("NO_REVIEW_EVIDENCE", model.evidenceAnchors().getFirst().sourceVersion());
    }

    @Test
    void shouldDeriveReviewItemsFromAlertRecoveryReplayShadowAndConsistencyFacts() {
        IncidentReplayReviewOverviewReadModel model = service(facts(
                fact("PAPER_ALERT", "alt-high", "OPEN", "HIGH", "paper-alert:alt-high", null, null, "paper-1", null, "High alert", FRESH_TIME, null),
                fact("PAPER_ALERT", "alt-acked", "ACKED", "LOW", "paper-alert:alt-acked", null, null, "paper-1", null, "Acked alert fact", FRESH_TIME, null),
                fact("RECOVERY_EVENT", "rec-ok", "SUCCEEDED", "INFO", "recovery-event:rec-ok", null, null, "paper-1", null, "Recovery succeeded", FRESH_TIME, null),
                fact("TRADE_REPLAY", "trr-1", "ORDER_FILLED", "INFO", "trade-replay:trr-1", "trr-1", null, "paper-1", null, "Replay fact", FRESH_TIME, null),
                fact("SHADOW_EVENT", "evt-1", "FAILED", "HIGH", "shadow-event:evt-1", null, "22222222-2222-2222-2222-222222222222", "paper-1", null, "Shadow failed", FRESH_TIME, "trace-shadow"),
                fact("CONSISTENCY_DIVERGENCE", "11111111-1111-1111-1111-111111111111", "DIVERGED", "HIGH", "consistency-report:11111111-1111-1111-1111-111111111111", null, "22222222-2222-2222-2222-222222222222", "paper-1", "11111111-1111-1111-1111-111111111111", "Consistency diverged", FRESH_TIME, "trace-consistency")
        )).overview("trace-review");

        assertEquals(6, model.totalReviewItems());
        assertEquals(2, model.needsOperatorReviewCount());
        assertEquals(1, model.acknowledgedRecommendationCount());
        assertEquals(1, model.closedRecommendationCount());
        assertEquals(1, model.evidenceReviewCount());
        assertEquals(1, model.blockedCount());
        assertEquals(2L, model.severityBuckets().get("HIGH"));
        assertEquals(6L, model.freshnessSummary().get("FRESH"));
        assertEquals(Availability.AVAILABLE, model.evidenceMetadata().availability());
        assertEquals(FRESH_TIME, model.evidenceMetadata().lastCalculatedAt());
        assertEquals(FreshnessStatus.FRESH, model.evidenceMetadata().freshnessStatus());
        assertEquals(60L, model.evidenceMetadata().ageSeconds());
        assertHasMessage(model.warnings(), "ACKNOWLEDGE_RECOMMENDED_ONLY");
        assertHasMessage(model.warnings(), "CLOSED_RECOMMENDATION_ONLY");
        assertHasMessage(model.warnings(), "HIGH_CRITICAL_ARE_PRIORITY_ONLY");
        assertHasNextStep(model.nextSteps(), "MANUALLY_ESCALATE_PRIORITY_ITEMS");
        assertTrue(model.reviewItems().stream().allMatch(IncidentReplayReviewOverviewReadModel.IncidentReplayReviewItem::notTradingAuthorization));
        assertTrue(model.reviewItems().stream().noneMatch(item -> item.reviewDecision() == IncidentReplayReviewDecision.CLOSEOUT_RECOMMENDED
                && item.summary().toLowerCase(Locale.ROOT).contains("incident closed")));
    }

    @Test
    void shouldIdentifyStaleEvidenceAndFailClosed() {
        IncidentReplayReviewOverviewReadModel model = service(facts(
                fact("PAPER_ALERT", "alt-stale", "OPEN", "HIGH", "paper-alert:alt-stale", null, null, "paper-1", null, "Stale alert", STALE_TIME, null)
        )).overview("trace-stale");

        IncidentReplayReviewOverviewReadModel.IncidentReplayReviewItem item = model.reviewItems().getFirst();

        assertEquals(IncidentReplayReviewFreshness.STALE, item.evidenceFreshness());
        assertEquals(IncidentReplayReviewState.BLOCKED, item.reviewState());
        assertEquals(IncidentReplayReviewDecision.STALE_EVIDENCE, item.reviewDecision());
        assertEquals(Availability.AVAILABLE, model.evidenceMetadata().availability());
        assertEquals(FreshnessStatus.STALE, model.evidenceMetadata().freshnessStatus());
        assertEquals(604800L, model.evidenceMetadata().staleAfterSeconds());
        assertHasMessage(model.warnings(), "STALE_EVIDENCE");
        assertHasMessage(item.blockers(), "STALE_EVIDENCE");
    }

    @Test
    void shouldFailClosedForPartialOrFutureEvidenceMetadata() {
        IncidentReplayReviewOverviewReadModel partial = service(facts(
                fact("PAPER_ALERT", "alt-missing-time", "OPEN", "HIGH", "paper-alert:alt-missing-time", null, null, "paper-1", null, "Missing time", null, null)
        )).overview("trace-partial");
        IncidentReplayReviewOverviewReadModel future = service(facts(
                fact("PAPER_ALERT", "alt-future", "OPEN", "HIGH", "paper-alert:alt-future", null, null, "paper-1", null, "Future time", FUTURE_TIME, null)
        )).overview("trace-future");

        assertEquals(Availability.PARTIAL, partial.evidenceMetadata().availability());
        assertEquals(null, partial.evidenceMetadata().lastCalculatedAt());
        assertEquals(null, partial.evidenceMetadata().ageSeconds());
        assertEquals(FreshnessStatus.UNKNOWN, partial.evidenceMetadata().freshnessStatus());
        assertEquals(Availability.AVAILABLE, future.evidenceMetadata().availability());
        assertEquals(FUTURE_TIME, future.evidenceMetadata().lastCalculatedAt());
        assertEquals(null, future.evidenceMetadata().ageSeconds());
        assertEquals(FreshnessStatus.UNKNOWN, future.evidenceMetadata().freshnessStatus());
    }

    @Test
    void shouldKeepHighCriticalAndAcknowledgeRecommendationsAsDiagnosticOnly() {
        IncidentReplayReviewOverviewReadModel model = service(facts(
                fact("PAPER_ALERT", "alt-critical", "OPEN", "CRITICAL", "paper-alert:alt-critical", null, null, "paper-1", null, "Critical alert", FRESH_TIME, null),
                fact("PAPER_ALERT", "alt-acked", "ACKED", "LOW", "paper-alert:alt-acked", null, null, "paper-1", null, "Acked alert", FRESH_TIME, null)
        )).overview("trace-boundary");

        IncidentReplayReviewOverviewReadModel.IncidentReplayReviewItem critical = model.reviewItems().get(0);
        IncidentReplayReviewOverviewReadModel.IncidentReplayReviewItem acked = model.reviewItems().get(1);

        assertEquals(IncidentReplayReviewSeverity.CRITICAL, critical.severity());
        assertEquals(IncidentReplayReviewDecision.ESCALATE_RECOMMENDED, critical.reviewDecision());
        assertEquals(IncidentReplayReviewDecision.ACKNOWLEDGE_RECOMMENDED, acked.reviewDecision());
        assertTrue(critical.notTradingAuthorization());
        assertTrue(acked.notTradingAuthorization());
        assertHasMessage(critical.warnings(), "ESCALATE_RECOMMENDED_ONLY");
        assertHasMessage(acked.warnings(), "ACKNOWLEDGE_RECOMMENDED_ONLY");
        String rendered = model.toString().toLowerCase(Locale.ROOT);
        assertFalse(rendered.contains("automatic remediation complete"));
        assertFalse(rendered.contains("incident closed"));
    }

    @Test
    void shouldGenerateDeterministicReviewItemIdAndOperatorAnchor() {
        IncidentReplayReviewOverviewQueryService service = service(facts(
                fact("CONSISTENCY_DIVERGENCE", "rpt-1", "DIVERGED", "HIGH", "consistency-report:rpt-1", null, "shadow-1", "paper-1", "rpt-1", "Consistency diverged", FRESH_TIME, "trace-1")
        ));

        IncidentReplayReviewOverviewReadModel.IncidentReplayReviewItem first = service.overview("trace-a").reviewItems().getFirst();
        IncidentReplayReviewOverviewReadModel.IncidentReplayReviewItem second = service.overview("trace-b").reviewItems().getFirst();

        assertEquals(first.reviewItemId(), second.reviewItemId());
        assertEquals(first.operatorItemId(), second.operatorItemId());
        assertTrue(first.reviewItemId().startsWith("irr-"));
        assertTrue(first.operatorItemId().startsWith("op-"));
        assertTrue(first.evidenceAnchors().stream().anyMatch(anchor -> "CONSISTENCY_EVIDENCE".equals(anchor.sourceType())));
    }

    @Test
    void shouldFilterSensitiveOrMisleadingEvidenceText() {
        IncidentReplayReviewOverviewReadModel model = service(facts(
                fact("PAPER_ALERT", "alt-safe", "OPEN", "HIGH", "paper-alert:alt-safe", null, null, "paper-1", null, "apiKey ready to trade token", FRESH_TIME, null)
        )).overview("trace-safe");

        String rendered = model.toString().toLowerCase(Locale.ROOT);

        assertFalse(rendered.contains("apikey"));
        assertFalse(rendered.contains("ready to trade"));
        assertFalse(rendered.contains("token"));
        assertTrue(rendered.contains("[filtered diagnostic text]"));
    }

    @Test
    void shouldKeepServiceDependencyAwayFromRunnerAdapterAccountLedgerAndOrderPorts() {
        List<String> dependencyNames = List.of(IncidentReplayReviewOverviewQueryService.class.getDeclaredFields()).stream()
                .filter(field -> !field.isSynthetic())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(Field::getType)
                .map(Class::getName)
                .toList();

        assertEquals(List.of(IncidentReplayReviewOverviewQueryPort.class.getName(), Clock.class.getName()), dependencyNames);
        String joined = String.join("|", dependencyNames).toLowerCase(Locale.ROOT);
        assertFalse(joined.contains("runner"));
        assertFalse(joined.contains("adapter"));
        assertFalse(joined.contains("account"));
        assertFalse(joined.contains("ledger"));
        assertFalse(joined.contains("order"));
        assertFalse(joined.contains("client"));
        assertFalse(joined.contains("credential"));
    }

    private IncidentReplayReviewOverviewQueryService service(IncidentReplayReviewOverviewFacts facts) {
        return new IncidentReplayReviewOverviewQueryService(new InMemoryQueryPort(facts), FIXED_CLOCK);
    }

    private IncidentReplayReviewOverviewFacts facts(ReviewEvidenceFact... facts) {
        return new IncidentReplayReviewOverviewFacts(List.of(facts));
    }

    private ReviewEvidenceFact fact(
            String sourceType,
            String sourceId,
            String sourceStatus,
            String sourceSeverity,
            String incidentEvidenceId,
            String replayRecordId,
            String shadowRunId,
            String paperRunId,
            String consistencyReportId,
            String summary,
            Instant occurredAt,
            String traceId
    ) {
        return new ReviewEvidenceFact(
                sourceType,
                sourceId,
                sourceStatus,
                sourceSeverity,
                incidentEvidenceId,
                replayRecordId,
                shadowRunId,
                paperRunId,
                consistencyReportId,
                summary,
                occurredAt,
                traceId
        );
    }

    private void assertHasMessage(
            Iterable<IncidentReplayReviewOverviewReadModel.BoundaryMessage> messages,
            String code
    ) {
        for (IncidentReplayReviewOverviewReadModel.BoundaryMessage message : messages) {
            if (code.equals(message.code())) {
                return;
            }
        }
        throw new AssertionError("expected message code: " + code);
    }

    private void assertHasNextStep(
            Iterable<IncidentReplayReviewOverviewReadModel.NextStep> nextSteps,
            String code
    ) {
        for (IncidentReplayReviewOverviewReadModel.NextStep step : nextSteps) {
            if (code.equals(step.code())) {
                return;
            }
        }
        throw new AssertionError("expected next step code: " + code);
    }

    private record InMemoryQueryPort(IncidentReplayReviewOverviewFacts facts)
            implements IncidentReplayReviewOverviewQueryPort {

        @Override
        public IncidentReplayReviewOverviewFacts loadOverviewFacts() {
            return facts;
        }
    }
}
