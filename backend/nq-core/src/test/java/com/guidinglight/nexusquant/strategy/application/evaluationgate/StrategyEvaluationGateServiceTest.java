package com.guidinglight.nexusquant.strategy.application.evaluationgate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyEvaluationGateFacts.DatasetFact;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyEvaluationGateFacts.EvaluationFact;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyEvaluationGateFacts.PaperEvidenceFact;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyEvaluationGateFacts.PublishTraceFact;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyEvaluationGateFacts.StrategyVersionFact;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class StrategyEvaluationGateServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-05T10:00:00Z"), ZoneOffset.UTC);
    private static final UUID DATASET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void shouldFailClosedWithoutStrategyVersionIdAndAvoidRepository() {
        GuardedFactsRepository repository = new GuardedFactsRepository(readyFacts());
        StrategyEvaluationGateService service = new StrategyEvaluationGateService(repository, FIXED_CLOCK);

        StrategyEvaluationGate gate = service.evaluate(new StrategyEvaluationGateQuery(
                "strategy-alpha",
                null,
                DATASET_ID,
                "eval-1",
                "pub-1",
                "ptr-1"
        ));

        assertEquals(0, repository.callCount);
        assertEquals(StrategyEvaluationGateStatus.BLOCKED_MISSING_STRATEGY_VERSION, gate.gateStatus());
        assertEquals(StrategyEvaluationGateDecision.RESEARCH_EVALUATION_BLOCKED, gate.gateDecision());
        assertHasEvidence(gate.missingEvidence(), "STRATEGY_VERSION");
        assertHasReason(gate.blockers(), "STRATEGY_VERSION_ID_REQUIRED");
        assertEquals(Instant.parse("2026-07-05T10:00:00Z"), gate.generatedAt());
    }

    @Test
    void shouldBlockWhenStrategyVersionDoesNotExist() {
        StrategyEvaluationGate gate = evaluate(readyFactsWith(StrategyVersionFact.missing()));

        assertEquals(StrategyEvaluationGateStatus.BLOCKED_MISSING_STRATEGY_VERSION, gate.gateStatus());
        assertHasReason(gate.blockers(), "STRATEGY_VERSION_NOT_FOUND");
        assertFalse(isReady(gate));
    }

    @Test
    void shouldBlockWhenDatasetMissing() {
        StrategyEvaluationGate gate = evaluate(readyFactsWith(DatasetFact.missing(DATASET_ID)));

        assertEquals(StrategyEvaluationGateStatus.BLOCKED_MISSING_DATASET, gate.gateStatus());
        assertEquals("NOT_AVAILABLE", gate.datasetQualityStatus());
        assertHasReason(gate.blockers(), "DATASET_MISSING");
        assertHasEvidence(gate.missingEvidence(), "DATASET");
    }

    @Test
    void shouldBlockWhenEvaluationMissing() {
        StrategyEvaluationGate gate = evaluate(readyFactsWith(EvaluationFact.missing("eval-1")));

        assertEquals(StrategyEvaluationGateStatus.BLOCKED_MISSING_EVALUATION, gate.gateStatus());
        assertEquals("NOT_AVAILABLE", gate.evaluationStatus());
        assertHasReason(gate.blockers(), "EVALUATION_MISSING");
        assertHasEvidence(gate.missingEvidence(), "EVALUATION");
    }

    @Test
    void shouldBlockWhenEvaluationFailed() {
        StrategyEvaluationGate gate = evaluate(readyFactsWith(new EvaluationFact(
                true,
                "eval-1",
                "bt-1",
                "FAILED",
                true,
                Instant.parse("2026-07-05T08:00:00Z")
        )));

        assertEquals(StrategyEvaluationGateStatus.BLOCKED_EVALUATION_FAILED, gate.gateStatus());
        assertEquals("FAILED", gate.evaluationStatus());
        assertHasReason(gate.blockers(), "EVALUATION_FAILED");
        assertHasEvidence(gate.missingEvidence(), "EVALUATION");
    }

    @Test
    void shouldBlockWhenDatasetQualityInsufficient() {
        StrategyEvaluationGate gate = evaluate(readyFactsWith(new DatasetFact(
                true,
                DATASET_ID,
                "READY",
                "GAP_DETECTED",
                "GAP_DETECTED",
                100L,
                2L,
                2L,
                0L,
                0L,
                Instant.parse("2026-07-05T08:10:00Z")
        )));

        assertEquals(StrategyEvaluationGateStatus.BLOCKED_DATA_QUALITY, gate.gateStatus());
        assertEquals("GAP_DETECTED", gate.datasetQualityStatus());
        assertHasReason(gate.blockers(), "DATASET_QUALITY_BLOCKED");
        assertHasEvidence(gate.missingEvidence(), "DATASET");
    }

    @Test
    void shouldBlockWhenPublishTraceMissing() {
        StrategyEvaluationGate gate = evaluate(readyFactsWith(PublishTraceFact.missing("pub-1")));

        assertEquals(StrategyEvaluationGateStatus.BLOCKED_NOT_PUBLISHED, gate.gateStatus());
        assertEquals("NOT_AVAILABLE", gate.publishTraceStatus());
        assertHasReason(gate.blockers(), "PUBLISH_TRACE_MISSING");
        assertHasEvidence(gate.missingEvidence(), "PUBLISH_TRACE");
    }

    @Test
    void shouldBlockWhenPaperEvidenceMissing() {
        StrategyEvaluationGate gate = evaluate(readyFactsWith(PaperEvidenceFact.missing("ptr-1")));

        assertEquals(StrategyEvaluationGateStatus.BLOCKED_MISSING_PAPER_EVIDENCE, gate.gateStatus());
        assertEquals("NOT_AVAILABLE", gate.paperEvidenceStatus());
        assertHasReason(gate.blockers(), "PAPER_EVIDENCE_MISSING");
        assertHasEvidence(gate.missingEvidence(), "PAPER_EVIDENCE");
    }

    @Test
    void shouldReturnReadyForShadowReviewOnlyWhenAllEvidenceSatisfied() {
        StrategyEvaluationGate gate = evaluate(readyFacts());

        assertEquals(StrategyEvaluationGateStatus.READY_FOR_SHADOW_REVIEW, gate.gateStatus());
        assertEquals(
                StrategyEvaluationGateDecision.RESEARCH_EVALUATION_READY_FOR_SHADOW_REVIEW,
                gate.gateDecision()
        );
        assertEquals("SUCCEEDED", gate.evaluationStatus());
        assertEquals("OK", gate.datasetQualityStatus());
        assertEquals("STOPPED", gate.paperEvidenceStatus());
        assertEquals("SUCCEEDED", gate.publishTraceStatus());
        assertTrue(gate.missingEvidence().isEmpty());
        assertTrue(gate.blockers().isEmpty());
        assertHasReason(gate.warnings(), "EVALUATION_GATE_NOT_TRADING_AUTHORIZATION");
        assertFalse(gate.nextSteps().toString().contains("tradingReady"));
        assertFalse(gate.nextSteps().toString().contains("liveReady"));
        assertFalse(gate.nextSteps().toString().contains("authorizedForTrading"));
    }

    private StrategyEvaluationGate evaluate(StrategyEvaluationGateFacts facts) {
        GuardedFactsRepository repository = new GuardedFactsRepository(facts);
        StrategyEvaluationGateService service = new StrategyEvaluationGateService(repository, FIXED_CLOCK);
        StrategyEvaluationGate gate = service.evaluate(new StrategyEvaluationGateQuery(
                "strategy-alpha",
                "sv-1",
                DATASET_ID,
                "eval-1",
                "pub-1",
                "ptr-1"
        ));
        assertEquals(1, repository.callCount);
        return gate;
    }

    private StrategyEvaluationGateFacts readyFacts() {
        return new StrategyEvaluationGateFacts(
                readyStrategyVersion(),
                readyDataset(),
                readyEvaluation(),
                readyPublish(),
                readyPaperEvidence()
        );
    }

    private StrategyEvaluationGateFacts readyFactsWith(StrategyVersionFact strategyVersion) {
        return new StrategyEvaluationGateFacts(
                strategyVersion,
                readyDataset(),
                readyEvaluation(),
                readyPublish(),
                readyPaperEvidence()
        );
    }

    private StrategyEvaluationGateFacts readyFactsWith(DatasetFact dataset) {
        return new StrategyEvaluationGateFacts(
                readyStrategyVersion(),
                dataset,
                readyEvaluation(),
                readyPublish(),
                readyPaperEvidence()
        );
    }

    private StrategyEvaluationGateFacts readyFactsWith(EvaluationFact evaluation) {
        return new StrategyEvaluationGateFacts(
                readyStrategyVersion(),
                readyDataset(),
                evaluation,
                readyPublish(),
                readyPaperEvidence()
        );
    }

    private StrategyEvaluationGateFacts readyFactsWith(PublishTraceFact publishTrace) {
        return new StrategyEvaluationGateFacts(
                readyStrategyVersion(),
                readyDataset(),
                readyEvaluation(),
                publishTrace,
                readyPaperEvidence()
        );
    }

    private StrategyEvaluationGateFacts readyFactsWith(PaperEvidenceFact paperEvidence) {
        return new StrategyEvaluationGateFacts(
                readyStrategyVersion(),
                readyDataset(),
                readyEvaluation(),
                readyPublish(),
                paperEvidence
        );
    }

    private StrategyVersionFact readyStrategyVersion() {
        return new StrategyVersionFact(true, true, "strategy-alpha", "strategy-alpha", "sv-1", "ACTIVE");
    }

    private DatasetFact readyDataset() {
        return new DatasetFact(
                true,
                DATASET_ID,
                "READY",
                "OK",
                "OK",
                100L,
                0L,
                0L,
                0L,
                0L,
                Instant.parse("2026-07-05T08:10:00Z")
        );
    }

    private EvaluationFact readyEvaluation() {
        return new EvaluationFact(
                true,
                "eval-1",
                "bt-1",
                "SUCCEEDED",
                true,
                Instant.parse("2026-07-05T08:00:00Z")
        );
    }

    private PublishTraceFact readyPublish() {
        return new PublishTraceFact(
                true,
                "pub-1",
                "bt-1",
                "eval-1",
                "sv-1",
                "SUCCEEDED",
                Instant.parse("2026-07-05T08:20:00Z")
        );
    }

    private PaperEvidenceFact readyPaperEvidence() {
        return new PaperEvidenceFact(
                true,
                "ptr-1",
                "pub-1",
                "sv-1",
                "STOPPED",
                "SIM",
                Instant.parse("2026-07-05T08:30:00Z")
        );
    }

    private boolean isReady(StrategyEvaluationGate gate) {
        return gate.gateStatus() == StrategyEvaluationGateStatus.READY_FOR_SHADOW_REVIEW;
    }

    private void assertHasEvidence(Iterable<StrategyEvaluationGateEvidence> evidenceItems, String code) {
        for (StrategyEvaluationGateEvidence evidence : evidenceItems) {
            if (code.equals(evidence.code())) {
                return;
            }
        }
        throw new AssertionError("expected evidence code: " + code);
    }

    private void assertHasReason(Iterable<StrategyEvaluationGateReason> reasons, String code) {
        for (StrategyEvaluationGateReason reason : reasons) {
            if (code.equals(reason.code())) {
                return;
            }
        }
        throw new AssertionError("expected reason code: " + code);
    }

    private static final class GuardedFactsRepository implements StrategyEvaluationGateFactRepository {
        private final StrategyEvaluationGateFacts facts;
        private int callCount;

        private GuardedFactsRepository(StrategyEvaluationGateFacts facts) {
            this.facts = facts;
        }

        @Override
        public StrategyEvaluationGateFacts loadFacts(StrategyEvaluationGateQuery query) {
            callCount++;
            return facts;
        }
    }
}
