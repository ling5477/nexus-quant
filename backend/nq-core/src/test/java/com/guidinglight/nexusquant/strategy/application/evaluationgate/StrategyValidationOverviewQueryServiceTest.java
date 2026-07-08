package com.guidinglight.nexusquant.strategy.application.evaluationgate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.strategy.domain.port.StrategyValidationOverviewFacts;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyValidationOverviewFacts.LatestDecisionFact;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyValidationOverviewQueryPort;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class StrategyValidationOverviewQueryServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-08T09:00:00Z"), ZoneOffset.UTC);
    private static final UUID DATASET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SHADOW_RUN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void shouldReturnNoEvidenceSafeOverviewForEmptyFacts() {
        StrategyValidationOverviewReadModel model = service(facts(null)).overview("trace-empty");

        assertEquals(StrategyValidationDecision.NO_EVIDENCE, model.latestDecision().decision());
        assertEquals(0, model.totalStrategyVersions());
        assertTrue(model.diagnosticOnly());
        assertTrue(model.noSideEffect());
        assertTrue(model.notTradingAuthorization());
        assertTrue(model.liveDisabled());
        assertFalse(model.realProviderImplemented());
        assertFalse(model.privateTradingImplemented());
        assertFalse(model.aiDhRuntimeIntegrated());
        assertHasMessage(model.warnings(), "LIVE_DISABLED");
        assertHasMessage(model.warnings(), "VALIDATION_IS_NOT_TRADING_AUTHORIZATION");
        assertEquals("trace-empty", model.traceId());
    }

    @Test
    void shouldReturnBlockedWhenEvidenceHasBlocker() {
        StrategyValidationOverviewReadModel model = service(facts(new LatestDecisionFact(
                "sv-1",
                DATASET_ID,
                "eval-1",
                "pub-1",
                "paper-1",
                SHADOW_RUN_ID,
                "ACTIVE",
                "SUCCEEDED",
                "SUCCEEDED",
                "STOPPED",
                "SIM",
                "FAILED",
                "CONSISTENT",
                Instant.parse("2026-07-08T08:00:00Z"),
                Instant.parse("2026-07-08T08:00:00Z")
        ))).overview("trace-blocked");

        assertEquals(StrategyValidationDecision.BLOCKED, model.latestDecision().decision());
        assertHasMessage(model.blockers(), "VALIDATION_EVIDENCE_BLOCKED");
        assertHasMessage(model.warnings(), "REAL_PROVIDER_NOT_IMPLEMENTED");
        assertTrue(model.latestDecision().decisionReasons().toString().contains("Shadow Run"));
    }

    @Test
    void shouldReturnNeedsReviewWhenEvidenceIsIncomplete() {
        StrategyValidationOverviewReadModel model = service(facts(new LatestDecisionFact(
                "sv-1",
                DATASET_ID,
                "eval-1",
                null,
                null,
                null,
                "ACTIVE",
                "SUCCEEDED",
                null,
                null,
                null,
                null,
                null,
                Instant.parse("2026-07-08T08:00:00Z"),
                Instant.parse("2026-07-08T08:00:00Z")
        ))).overview("trace-review");

        assertEquals(StrategyValidationDecision.NEEDS_REVIEW, model.latestDecision().decision());
        assertTrue(model.latestDecision().decisionReasons().toString().contains("publish"));
        assertTrue(model.latestDecision().limitations().toString().contains("not trading authorization"));
    }

    @Test
    void shouldReturnApprovedWithoutTradingAuthorization() {
        StrategyValidationOverviewReadModel model = service(facts(new LatestDecisionFact(
                "sv-1",
                DATASET_ID,
                "eval-1",
                "pub-1",
                "paper-1",
                null,
                "ACTIVE",
                "SUCCEEDED",
                "SUCCEEDED",
                "STOPPED",
                "SIM",
                null,
                null,
                Instant.parse("2026-07-08T08:00:00Z"),
                Instant.parse("2026-07-08T08:00:00Z")
        ))).overview("trace-approved");

        assertEquals(StrategyValidationDecision.APPROVED, model.latestDecision().decision());
        assertTrue(model.notTradingAuthorization());
        assertHasMessage(model.warnings(), "VALIDATION_IS_NOT_TRADING_AUTHORIZATION");
        assertFalse(model.latestDecision().limitations().toString().contains("tradingReady"));
        assertFalse(model.nextSteps().toString().contains("liveReady"));
        assertFalse(model.nextSteps().toString().contains("authorizedForTrading"));
    }

    @Test
    void shouldReturnStaleEvidenceWhenShadowRunExistsWithoutConsistency() {
        StrategyValidationOverviewReadModel model = service(facts(new LatestDecisionFact(
                "sv-1",
                DATASET_ID,
                "eval-1",
                "pub-1",
                "paper-1",
                SHADOW_RUN_ID,
                "ACTIVE",
                "SUCCEEDED",
                "SUCCEEDED",
                "STOPPED",
                "SIM",
                "COMPLETED",
                null,
                Instant.parse("2026-07-08T08:00:00Z"),
                Instant.parse("2026-07-08T08:00:00Z")
        ))).overview("trace-stale");

        assertEquals(StrategyValidationDecision.STALE_EVIDENCE, model.latestDecision().decision());
        assertHasMessage(model.warnings(), "STALE_EVIDENCE");
    }

    private StrategyValidationOverviewQueryService service(StrategyValidationOverviewFacts facts) {
        return new StrategyValidationOverviewQueryService(new InMemoryQueryPort(facts), FIXED_CLOCK);
    }

    private StrategyValidationOverviewFacts facts(LatestDecisionFact latestDecision) {
        return new StrategyValidationOverviewFacts(
                latestDecision == null ? 0 : 1,
                latestDecision == null || !latestDecision.hasEvaluationReport() ? 0 : 1,
                0,
                0,
                latestDecision == null ? 0 : 1,
                0,
                Optional.ofNullable(latestDecision)
        );
    }

    private void assertHasMessage(
            Iterable<StrategyValidationOverviewReadModel.BoundaryMessage> messages,
            String code
    ) {
        for (StrategyValidationOverviewReadModel.BoundaryMessage message : messages) {
            if (code.equals(message.code())) {
                return;
            }
        }
        throw new AssertionError("expected message code: " + code);
    }

    private record InMemoryQueryPort(StrategyValidationOverviewFacts facts) implements StrategyValidationOverviewQueryPort {
        @Override
        public StrategyValidationOverviewFacts loadOverviewFacts() {
            return facts;
        }
    }
}
