package com.guidinglight.nexusquant.strategy.application.shadowvalidation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.strategy.domain.port.ShadowValidationWorkflowOverviewFacts;
import com.guidinglight.nexusquant.strategy.domain.port.ShadowValidationWorkflowOverviewFacts.OperatorEvidenceFact;
import com.guidinglight.nexusquant.strategy.domain.port.ShadowValidationWorkflowOverviewQueryPort;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ShadowValidationWorkflowOverviewQueryServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-08T09:00:00Z"), ZoneOffset.UTC);
    private static final UUID DATASET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SHADOW_RUN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CONSISTENCY_REPORT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void shouldReturnSafeOverviewForEmptyFacts() {
        ShadowValidationWorkflowOverviewReadModel model = service(ShadowValidationWorkflowOverviewFacts.empty())
                .overview("trace-empty");

        assertEquals(0, model.totalOperatorItems());
        assertEquals(0, model.operatorItems().size());
        assertTrue(model.diagnosticOnly());
        assertTrue(model.noSideEffect());
        assertTrue(model.notTradingAuthorization());
        assertTrue(model.liveDisabled());
        assertFalse(model.realProviderImplemented());
        assertFalse(model.privateTradingImplemented());
        assertFalse(model.aiDhRuntimeIntegrated());
        assertHasMessage(model.warnings(), "NO_OPERATOR_ITEMS");
        assertHasMessage(model.warnings(), "NO_EVIDENCE");
        assertHasNextStep(model.nextSteps(), "ADD_LOCAL_EVIDENCE");
    }

    @Test
    void shouldDeriveValidationReadyOperatorItemWithoutTradingAuthorization() {
        ShadowValidationWorkflowOverviewReadModel model = service(facts(completeValidationFact())).overview("trace-ready");

        assertEquals(1, model.totalOperatorItems());
        assertEquals(1, model.readyForOperatorReviewCount());
        ShadowValidationWorkflowOverviewReadModel.OperatorItem item = model.operatorItems().getFirst();
        assertEquals(ShadowValidationWorkflowState.READY_FOR_OPERATOR_REVIEW, item.workflowState());
        assertEquals(ShadowValidationWorkflowValidationDecision.VALIDATION_READY, item.validationDecision());
        assertEquals(ShadowValidationWorkflowSeverity.INFO, item.severity());
        assertEquals(ShadowValidationWorkflowEvidenceFreshness.FRESH, item.evidenceFreshness());
        assertTrue(item.notTradingAuthorization());
        assertTrue(item.liveDisabled());
        assertFalse(item.realProviderImplemented());
        assertFalse(item.privateTradingImplemented());
        assertFalse(item.aiDhRuntimeIntegrated());
        assertHasMessage(item.warnings(), "VALIDATION_READY_IS_REVIEW_ONLY");
        assertHasMessage(item.warnings(), "METRICS_NOT_INFERRED");
        assertHasNextStep(item.nextSteps(), "MANUAL_OPERATOR_REVIEW");
        assertFalse(model.toString().toLowerCase(java.util.Locale.ROOT).contains("winrate"));
        assertFalse(model.toString().toLowerCase(java.util.Locale.ROOT).contains("totalreturn"));
    }

    @Test
    void shouldReturnNeedsEvidenceForStaleFacts() {
        OperatorEvidenceFact stale = new OperatorEvidenceFact(
                "STRATEGY_VALIDATION",
                "sv-stale",
                "sv-stale",
                DATASET_ID,
                "eval-stale",
                "paper-stale",
                SHADOW_RUN_ID,
                CONSISTENCY_REPORT_ID,
                null,
                "ACTIVE",
                "SUCCEEDED",
                "SUCCEEDED",
                "STOPPED",
                "SIM",
                "COMPLETED",
                "CONSISTENT",
                null,
                null,
                Instant.parse("2026-06-29T09:00:00Z"),
                "trace-stale"
        );

        ShadowValidationWorkflowOverviewReadModel model = service(facts(stale)).overview("trace-stale");

        ShadowValidationWorkflowOverviewReadModel.OperatorItem item = model.operatorItems().getFirst();
        assertEquals(ShadowValidationWorkflowState.NEEDS_EVIDENCE, item.workflowState());
        assertEquals(ShadowValidationWorkflowValidationDecision.STALE_EVIDENCE, item.validationDecision());
        assertEquals(ShadowValidationWorkflowEvidenceFreshness.STALE, item.evidenceFreshness());
        assertHasMessage(item.warnings(), "STALE_EVIDENCE");
        assertHasNextStep(item.nextSteps(), "ADD_OR_REFRESH_EVIDENCE");
    }

    @Test
    void shouldReturnBlockedForShadowOrIncidentBlockers() {
        ShadowValidationWorkflowOverviewReadModel model = service(facts(blockedShadowFact(), criticalIncidentFact()))
                .overview("trace-blocked");

        assertEquals(2, model.blockedCount());
        assertHasMessage(model.blockers(), "SHADOW_RUN_BLOCKED");
        assertHasMessage(model.blockers(), "INCIDENT_EVIDENCE_CRITICAL");
        for (ShadowValidationWorkflowOverviewReadModel.OperatorItem item : model.operatorItems()) {
            assertEquals(ShadowValidationWorkflowState.BLOCKED, item.workflowState());
            assertEquals(ShadowValidationWorkflowValidationDecision.BLOCKED, item.validationDecision());
            assertTrue(item.notTradingAuthorization());
        }
    }

    @Test
    void shouldReturnEvidenceReviewForConsistencyDivergence() {
        OperatorEvidenceFact diverged = new OperatorEvidenceFact(
                "CONSISTENCY_REPORT",
                CONSISTENCY_REPORT_ID.toString(),
                "sv-1",
                DATASET_ID,
                "eval-1",
                "paper-1",
                SHADOW_RUN_ID,
                CONSISTENCY_REPORT_ID,
                null,
                "ACTIVE",
                "SUCCEEDED",
                "SUCCEEDED",
                "STOPPED",
                "SIM",
                "COMPLETED",
                "DIVERGED",
                null,
                "HIGH",
                Instant.parse("2026-07-08T08:59:00Z"),
                "trace-diverged"
        );

        ShadowValidationWorkflowOverviewReadModel model = service(facts(diverged)).overview("trace-diverged");

        ShadowValidationWorkflowOverviewReadModel.OperatorItem item = model.operatorItems().getFirst();
        assertEquals(ShadowValidationWorkflowState.EVIDENCE_REVIEW, item.workflowState());
        assertEquals(ShadowValidationWorkflowValidationDecision.NEEDS_REVIEW, item.validationDecision());
        assertEquals(ShadowValidationWorkflowSeverity.HIGH, item.severity());
        assertTrue(item.blockers().isEmpty());
        assertHasNextStep(item.nextSteps(), "REVIEW_DIAGNOSTIC_ITEM");
    }

    @Test
    void shouldReturnClosedRecommendationForResolvedIncidentFactsWithoutTradingAuthorization() {
        OperatorEvidenceFact resolvedIncident = new OperatorEvidenceFact(
                "INCIDENT_REPLAY",
                "alt-resolved",
                "sv-1",
                null,
                null,
                "paper-1",
                null,
                null,
                "alt-resolved",
                null,
                null,
                null,
                "STOPPED",
                "SIM",
                null,
                null,
                "RESOLVED",
                "INFO",
                Instant.parse("2026-07-08T08:58:00Z"),
                "trace-resolved"
        );

        ShadowValidationWorkflowOverviewReadModel model = service(facts(resolvedIncident)).overview("trace-resolved");

        ShadowValidationWorkflowOverviewReadModel.OperatorItem item = model.operatorItems().getFirst();
        assertEquals(1, model.closedRecommendationCount());
        assertEquals(ShadowValidationWorkflowState.CLOSED_RECOMMENDATION, item.workflowState());
        assertEquals(ShadowValidationWorkflowValidationDecision.NO_DECISION, item.validationDecision());
        assertTrue(item.notTradingAuthorization());
        assertHasNextStep(item.nextSteps(), "REVIEW_DIAGNOSTIC_ITEM");
    }

    @Test
    void shouldGenerateDeterministicOperatorItemId() {
        ShadowValidationWorkflowOverviewQueryService service = service(facts(completeValidationFact()));

        String first = service.overview("trace-id-1").operatorItems().getFirst().operatorItemId();
        String second = service.overview("trace-id-2").operatorItems().getFirst().operatorItemId();

        assertEquals(first, second);
        assertTrue(first.startsWith("op-"));
    }

    @Test
    void shouldFilterSensitiveOrMisleadingFactText() {
        OperatorEvidenceFact sensitive = new OperatorEvidenceFact(
                "INCIDENT_REPLAY",
                "apiKey exposed",
                null,
                null,
                null,
                null,
                null,
                null,
                "token leaked",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "OPEN",
                "INFO",
                Instant.parse("2026-07-08T08:59:00Z"),
                "ready to trade"
        );

        ShadowValidationWorkflowOverviewReadModel model = service(facts(sensitive)).overview("trace-safe");

        String rendered = model.toString().toLowerCase(java.util.Locale.ROOT);
        assertFalse(rendered.contains("apikey"));
        assertFalse(rendered.contains("token leaked"));
        assertFalse(rendered.contains("ready to trade"));
        assertTrue(rendered.contains("[filtered diagnostic text]"));
    }

    private OperatorEvidenceFact completeValidationFact() {
        return new OperatorEvidenceFact(
                "STRATEGY_VALIDATION",
                "sv-1",
                "sv-1",
                DATASET_ID,
                "eval-1",
                "paper-1",
                SHADOW_RUN_ID,
                CONSISTENCY_REPORT_ID,
                null,
                "ACTIVE",
                "SUCCEEDED",
                "SUCCEEDED",
                "STOPPED",
                "SIM",
                "COMPLETED",
                "CONSISTENT",
                null,
                null,
                Instant.parse("2026-07-08T08:59:00Z"),
                "trace-ready"
        );
    }

    private OperatorEvidenceFact blockedShadowFact() {
        return new OperatorEvidenceFact(
                "SHADOW_RUN",
                SHADOW_RUN_ID.toString(),
                "sv-1",
                DATASET_ID,
                "eval-1",
                "paper-1",
                SHADOW_RUN_ID,
                CONSISTENCY_REPORT_ID,
                null,
                "ACTIVE",
                "SUCCEEDED",
                "SUCCEEDED",
                "STOPPED",
                "SIM",
                "FAILED",
                "CONSISTENT",
                null,
                null,
                Instant.parse("2026-07-08T08:59:00Z"),
                "trace-blocked"
        );
    }

    private OperatorEvidenceFact criticalIncidentFact() {
        return new OperatorEvidenceFact(
                "INCIDENT_REPLAY",
                "alt-1",
                "sv-1",
                null,
                null,
                "paper-1",
                null,
                null,
                "alt-1",
                null,
                null,
                null,
                "STOPPED",
                "SIM",
                null,
                null,
                "OPEN",
                "CRITICAL",
                Instant.parse("2026-07-08T08:58:00Z"),
                "trace-critical"
        );
    }

    private ShadowValidationWorkflowOverviewQueryService service(ShadowValidationWorkflowOverviewFacts facts) {
        return new ShadowValidationWorkflowOverviewQueryService(new InMemoryQueryPort(facts), FIXED_CLOCK);
    }

    private ShadowValidationWorkflowOverviewFacts facts(OperatorEvidenceFact... facts) {
        return new ShadowValidationWorkflowOverviewFacts(List.of(facts));
    }

    private void assertHasMessage(
            Iterable<ShadowValidationWorkflowOverviewReadModel.BoundaryMessage> messages,
            String code
    ) {
        for (ShadowValidationWorkflowOverviewReadModel.BoundaryMessage message : messages) {
            if (code.equals(message.code())) {
                return;
            }
        }
        throw new AssertionError("expected message code: " + code);
    }

    private void assertHasNextStep(
            Iterable<ShadowValidationWorkflowOverviewReadModel.NextStep> nextSteps,
            String code
    ) {
        for (ShadowValidationWorkflowOverviewReadModel.NextStep step : nextSteps) {
            if (code.equals(step.code())) {
                return;
            }
        }
        throw new AssertionError("expected next step code: " + code);
    }

    private record InMemoryQueryPort(ShadowValidationWorkflowOverviewFacts facts)
            implements ShadowValidationWorkflowOverviewQueryPort {

        @Override
        public ShadowValidationWorkflowOverviewFacts loadOverviewFacts() {
            return facts;
        }
    }
}
