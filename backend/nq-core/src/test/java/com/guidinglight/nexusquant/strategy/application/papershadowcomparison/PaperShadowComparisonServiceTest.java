package com.guidinglight.nexusquant.strategy.application.papershadowcomparison;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonFacts.DatasetFact;
import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonFacts.EvaluationFact;
import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonFacts.PaperRunFact;
import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonFacts.PublishTraceFact;
import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonFacts.ShadowRunFact;
import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonFacts.StrategyVersionFact;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class PaperShadowComparisonServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-05T11:00:00Z"), ZoneOffset.UTC);
    private static final UUID DATASET_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void shouldFailClosedWithoutStrategyVersionIdAndAvoidRepository() {
        GuardedFactsRepository repository = new GuardedFactsRepository(readyFacts());
        PaperShadowComparisonService service = new PaperShadowComparisonService(repository, FIXED_CLOCK);

        PaperShadowComparison comparison = service.compare(new PaperShadowComparisonQuery(
                "strategy-alpha",
                null,
                DATASET_ID,
                "eval-1",
                "pub-1",
                "ptr-1",
                "shr-1"
        ));

        assertEquals(0, repository.callCount);
        assertEquals(PaperShadowComparisonStatus.BLOCKED_MISSING_STRATEGY_VERSION, comparison.comparisonStatus());
        assertFalse(comparison.comparable());
        assertHasEvidence(comparison.missingEvidence(), "STRATEGY_VERSION");
        assertHasReason(comparison.blockers(), "STRATEGY_VERSION_ID_REQUIRED");
        assertEquals(Instant.parse("2026-07-05T11:00:00Z"), comparison.generatedAt());
    }

    @Test
    void shouldBlockWhenStrategyVersionDoesNotExist() {
        PaperShadowComparison comparison = compare(readyFactsWith(StrategyVersionFact.missing()));

        assertEquals(PaperShadowComparisonStatus.BLOCKED_MISSING_STRATEGY_VERSION, comparison.comparisonStatus());
        assertHasReason(comparison.blockers(), "STRATEGY_VERSION_NOT_FOUND");
        assertFalse(comparison.comparable());
    }

    @Test
    void shouldBlockWhenEvaluationGateBlocked() {
        PaperShadowComparison comparison = compare(readyFactsWith(new EvaluationFact(
                true,
                "eval-1",
                "bt-1",
                "FAILED",
                true,
                Instant.parse("2026-07-05T08:00:00Z")
        )));

        assertEquals(PaperShadowComparisonStatus.BLOCKED_EVALUATION_GATE, comparison.comparisonStatus());
        assertEquals("BLOCKED_EVALUATION_GATE", comparison.evaluationGateStatus());
        assertHasReason(comparison.blockers(), "EVALUATION_GATE_BLOCKED");
        assertHasEvidence(comparison.missingEvidence(), "EVALUATION_GATE");
    }

    @Test
    void shouldBlockWhenPaperRunMissing() {
        PaperShadowComparison comparison = compare(readyFactsWith(PaperRunFact.missing("ptr-1")));

        assertEquals(PaperShadowComparisonStatus.BLOCKED_MISSING_PAPER_RUN, comparison.comparisonStatus());
        assertEquals("NOT_AVAILABLE", comparison.paperRunStatus());
        assertEquals("NOT_AVAILABLE", comparison.paperEvidenceStatus());
        assertHasReason(comparison.blockers(), "PAPER_RUN_MISSING_OR_NOT_COMPARABLE");
        assertHasEvidence(comparison.missingEvidence(), "PAPER_RUN");
    }

    @Test
    void shouldBlockWhenShadowRunnerNotImplementedEvenWhenExistingEvidenceIsReady() {
        PaperShadowComparison comparison = compare(readyFactsWith(ShadowRunFact.notImplemented("shr-1")));

        assertEquals(PaperShadowComparisonStatus.BLOCKED_SHADOW_NOT_IMPLEMENTED, comparison.comparisonStatus());
        assertEquals("NOT_IMPLEMENTED", comparison.shadowRunStatus());
        assertEquals("NOT_IMPLEMENTED", comparison.shadowEvidenceStatus());
        assertFalse(comparison.comparable());
        assertHasReason(comparison.blockers(), "SHADOW_RUNNER_NOT_IMPLEMENTED");
        assertHasEvidence(comparison.missingEvidence(), "SHADOW_RUN");
    }

    @Test
    void shouldBlockWhenShadowRunMissingAfterFactSourceExists() {
        PaperShadowComparison comparison = compare(readyFactsWith(ShadowRunFact.missing("shr-1")));

        assertEquals(PaperShadowComparisonStatus.BLOCKED_MISSING_SHADOW_RUN, comparison.comparisonStatus());
        assertEquals("NOT_AVAILABLE", comparison.shadowRunStatus());
        assertEquals("NOT_AVAILABLE", comparison.shadowEvidenceStatus());
        assertHasReason(comparison.blockers(), "SHADOW_RUN_MISSING");
        assertHasEvidence(comparison.missingEvidence(), "SHADOW_RUN");
    }

    @Test
    void shouldBlockWhenDataQualityInsufficient() {
        PaperShadowComparison comparison = compare(readyFactsWith(new DatasetFact(
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

        assertEquals(PaperShadowComparisonStatus.BLOCKED_DATA_QUALITY, comparison.comparisonStatus());
        assertEquals("GAP_DETECTED", comparison.dataQualityStatus());
        assertHasReason(comparison.blockers(), "DATASET_QUALITY_BLOCKED");
        assertHasEvidence(comparison.missingEvidence(), "DATASET");
    }

    @Test
    void shouldBlockWhenTraceChainIncomplete() {
        PaperShadowComparison comparison = compare(readyFactsWith(new PublishTraceFact(
                true,
                "pub-1",
                "bt-1",
                "eval-1",
                "sv-other",
                "SUCCEEDED",
                Instant.parse("2026-07-05T08:20:00Z")
        )));

        assertEquals(PaperShadowComparisonStatus.BLOCKED_TRACE_INCOMPLETE, comparison.comparisonStatus());
        assertHasReason(comparison.blockers(), "TRACE_CHAIN_INCOMPLETE");
        assertHasEvidence(comparison.missingEvidence(), "TRACE_CHAIN");
    }

    @Test
    void shouldReturnReadyForComparisonForComparableFixtureWithoutAuthorizationFields() {
        PaperShadowComparison comparison = compare(readyFacts());

        assertEquals(PaperShadowComparisonStatus.READY_FOR_COMPARISON, comparison.comparisonStatus());
        assertTrue(comparison.comparable());
        assertEquals("PASSED", comparison.evaluationGateStatus());
        assertEquals("SATISFIED", comparison.paperEvidenceStatus());
        assertEquals("SATISFIED", comparison.shadowEvidenceStatus());
        assertEquals("OK", comparison.dataQualityStatus());
        assertTrue(comparison.missingEvidence().isEmpty());
        assertTrue(comparison.blockers().isEmpty());
        assertHasReason(comparison.warnings(), "COMPARISON_NOT_TRADING_AUTHORIZATION");
        String serializedShape = comparison.toString();
        assertFalse(serializedShape.contains("tradingReady"));
        assertFalse(serializedShape.contains("liveReady"));
        assertFalse(serializedShape.contains("authorizedForTrading"));
        assertFalse(serializedShape.contains("TRADE_APPROVED"));
        assertFalse(serializedShape.contains("LIVE_READY"));
    }

    @Test
    void shouldKeepServiceReadOnlyAndRepositoryWithoutWriteMethods() throws Exception {
        Method compare = PaperShadowComparisonService.class.getMethod("compare", PaperShadowComparisonQuery.class);
        Transactional transactional = compare.getAnnotation(Transactional.class);

        assertNotNull(transactional);
        assertTrue(transactional.readOnly());
        assertTrue(Arrays.stream(PaperShadowComparisonFactRepository.class.getDeclaredMethods())
                .map(Method::getName)
                .allMatch("loadFacts"::equals));
        assertFalse(Arrays.stream(PaperShadowComparisonFactRepository.class.getDeclaredMethods())
                .map(Method::getName)
                .anyMatch(name -> name.startsWith("save")
                        || name.startsWith("create")
                        || name.startsWith("update")
                        || name.startsWith("delete")
                        || name.startsWith("start")));
    }

    private PaperShadowComparison compare(PaperShadowComparisonFacts facts) {
        GuardedFactsRepository repository = new GuardedFactsRepository(facts);
        PaperShadowComparisonService service = new PaperShadowComparisonService(repository, FIXED_CLOCK);
        PaperShadowComparison comparison = service.compare(new PaperShadowComparisonQuery(
                "strategy-alpha",
                "sv-1",
                DATASET_ID,
                "eval-1",
                "pub-1",
                "ptr-1",
                "shr-1"
        ));
        assertEquals(1, repository.callCount);
        return comparison;
    }

    private PaperShadowComparisonFacts readyFacts() {
        return new PaperShadowComparisonFacts(
                readyStrategyVersion(),
                readyDataset(),
                readyEvaluation(),
                readyPublish(),
                readyPaperRun(),
                readyShadowRun()
        );
    }

    private PaperShadowComparisonFacts readyFactsWith(StrategyVersionFact strategyVersion) {
        return new PaperShadowComparisonFacts(
                strategyVersion,
                readyDataset(),
                readyEvaluation(),
                readyPublish(),
                readyPaperRun(),
                readyShadowRun()
        );
    }

    private PaperShadowComparisonFacts readyFactsWith(DatasetFact dataset) {
        return new PaperShadowComparisonFacts(
                readyStrategyVersion(),
                dataset,
                readyEvaluation(),
                readyPublish(),
                readyPaperRun(),
                readyShadowRun()
        );
    }

    private PaperShadowComparisonFacts readyFactsWith(EvaluationFact evaluation) {
        return new PaperShadowComparisonFacts(
                readyStrategyVersion(),
                readyDataset(),
                evaluation,
                readyPublish(),
                readyPaperRun(),
                readyShadowRun()
        );
    }

    private PaperShadowComparisonFacts readyFactsWith(PublishTraceFact publishTrace) {
        return new PaperShadowComparisonFacts(
                readyStrategyVersion(),
                readyDataset(),
                readyEvaluation(),
                publishTrace,
                readyPaperRun(),
                readyShadowRun()
        );
    }

    private PaperShadowComparisonFacts readyFactsWith(PaperRunFact paperRun) {
        return new PaperShadowComparisonFacts(
                readyStrategyVersion(),
                readyDataset(),
                readyEvaluation(),
                readyPublish(),
                paperRun,
                readyShadowRun()
        );
    }

    private PaperShadowComparisonFacts readyFactsWith(ShadowRunFact shadowRun) {
        return new PaperShadowComparisonFacts(
                readyStrategyVersion(),
                readyDataset(),
                readyEvaluation(),
                readyPublish(),
                readyPaperRun(),
                shadowRun
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

    private PaperRunFact readyPaperRun() {
        return new PaperRunFact(
                true,
                "ptr-1",
                "pub-1",
                "sv-1",
                "STOPPED",
                "SIM",
                Instant.parse("2026-07-05T08:30:00Z")
        );
    }

    private ShadowRunFact readyShadowRun() {
        return new ShadowRunFact(
                true,
                true,
                "shr-1",
                "pub-1",
                "sv-1",
                DATASET_ID,
                "eval-1",
                "COMPLETED",
                Instant.parse("2026-07-05T08:40:00Z")
        );
    }

    private void assertHasEvidence(Iterable<PaperShadowComparisonEvidence> evidenceItems, String code) {
        for (PaperShadowComparisonEvidence evidence : evidenceItems) {
            if (code.equals(evidence.code())) {
                return;
            }
        }
        throw new AssertionError("expected evidence code: " + code);
    }

    private void assertHasReason(Iterable<PaperShadowComparisonReason> reasons, String code) {
        for (PaperShadowComparisonReason reason : reasons) {
            if (code.equals(reason.code())) {
                return;
            }
        }
        throw new AssertionError("expected reason code: " + code);
    }

    private static final class GuardedFactsRepository implements PaperShadowComparisonFactRepository {
        private final PaperShadowComparisonFacts facts;
        private int callCount;

        private GuardedFactsRepository(PaperShadowComparisonFacts facts) {
            this.facts = facts;
        }

        @Override
        public PaperShadowComparisonFacts loadFacts(PaperShadowComparisonQuery query) {
            callCount++;
            return facts;
        }
    }
}
