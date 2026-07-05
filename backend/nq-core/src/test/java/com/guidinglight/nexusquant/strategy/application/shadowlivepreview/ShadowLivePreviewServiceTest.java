package com.guidinglight.nexusquant.strategy.application.shadowlivepreview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyEvaluationGate;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyEvaluationGateDecision;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyEvaluationGateEvidence;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyEvaluationGateFactRepository;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyEvaluationGateQuery;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyEvaluationGateReason;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyEvaluationGateScope;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyEvaluationGateService;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyEvaluationGateStatus;
import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparison;
import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonEvidence;
import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonFactRepository;
import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonQuery;
import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonReason;
import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonScope;
import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonService;
import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonStatus;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class ShadowLivePreviewServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-07-05T12:00:00Z"), ZoneOffset.UTC);
    private static final UUID DATASET_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void shouldFailClosedWithoutStrategyVersionIdAndAvoidDelegates() {
        StubGateService gateService = new StubGateService(readyGate());
        StubComparisonService comparisonService = new StubComparisonService(readyComparison());
        ShadowLivePreviewService service = new ShadowLivePreviewService(gateService, comparisonService, FIXED_CLOCK);

        ShadowLivePreview preview = service.preview(new ShadowLivePreviewQuery(
                "strategy-alpha",
                null,
                DATASET_ID,
                "eval-1",
                "pub-1",
                "ptr-1",
                "shr-1"
        ));

        assertEquals(0, gateService.callCount);
        assertEquals(0, comparisonService.callCount);
        assertEquals(ShadowLivePreviewStatus.PREVIEW_BLOCKED_MISSING_STRATEGY_VERSION, preview.previewStatus());
        assertEquals("SKELETON_AVAILABLE", preview.runnerStatus());
        assertEquals("MISSING", preview.inputFactStatus());
        assertEquals("NOT_EXECUTED", preview.orderIntentPreviewStatus());
        assertHasEvidence(preview.missingEvidence(), "STRATEGY_VERSION");
        assertHasReason(preview.blockers(), "STRATEGY_VERSION_ID_REQUIRED");
        assertEquals(Instant.parse("2026-07-05T12:00:00Z"), preview.generatedAt());
    }

    @Test
    void shouldBlockWhenStrategyVersionDoesNotExist() {
        ShadowLivePreview preview = previewWith(
                gateWithStatus(StrategyEvaluationGateStatus.BLOCKED_MISSING_STRATEGY_VERSION),
                comparisonWithStatus(PaperShadowComparisonStatus.BLOCKED_MISSING_STRATEGY_VERSION)
        );

        assertEquals(ShadowLivePreviewStatus.PREVIEW_BLOCKED_MISSING_STRATEGY_VERSION, preview.previewStatus());
        assertFalse(preview.missingEvidence().isEmpty());
        assertHasReason(preview.blockers(), "MISSING_STRATEGY_VERSION");
    }

    @Test
    void shouldBlockWhenEvaluationGateBlocked() {
        ShadowLivePreview preview = previewWith(
                gateWithStatus(StrategyEvaluationGateStatus.BLOCKED_EVALUATION_FAILED),
                comparisonWithStatus(PaperShadowComparisonStatus.BLOCKED_EVALUATION_GATE)
        );

        assertEquals(ShadowLivePreviewStatus.PREVIEW_BLOCKED_EVALUATION_GATE, preview.previewStatus());
        assertEquals("BLOCKED_EVALUATION_FAILED", preview.evaluationGateStatus());
        assertHasReason(preview.blockers(), "EVALUATION_GATE_BLOCKED");
        assertHasEvidence(preview.missingEvidence(), "EVALUATION_GATE");
    }

    @Test
    void shouldBlockWhenPaperShadowComparisonBlocked() {
        ShadowLivePreview preview = previewWith(
                readyGate(),
                comparisonWithStatus(PaperShadowComparisonStatus.BLOCKED_EVALUATION_GATE)
        );

        assertEquals(ShadowLivePreviewStatus.PREVIEW_BLOCKED_PAPER_SHADOW_COMPARISON, preview.previewStatus());
        assertEquals("BLOCKED_EVALUATION_GATE", preview.paperShadowComparisonStatus());
        assertHasEvidence(preview.missingEvidence(), "PAPER_SHADOW_COMPARISON");
        assertHasReason(preview.blockers(), "PAPER_SHADOW_COMPARISON_BLOCKED");
    }

    @Test
    void shouldBlockWhenPaperRunMissing() {
        ShadowLivePreview preview = previewWith(
                readyGate(),
                comparisonWithStatus(PaperShadowComparisonStatus.BLOCKED_MISSING_PAPER_RUN)
        );

        assertEquals(ShadowLivePreviewStatus.PREVIEW_BLOCKED_MISSING_PAPER_EVIDENCE, preview.previewStatus());
        assertEquals("BLOCKED", preview.inputFactStatus());
        assertHasReason(preview.blockers(), "PAPER_EVIDENCE_MISSING");
        assertHasEvidence(preview.missingEvidence(), "PAPER_SHADOW_COMPARISON");
    }

    @Test
    void shouldBlockWhenShadowFactsUnavailable() {
        ShadowLivePreview preview = previewWith(
                readyGate(),
                comparisonWithStatus(PaperShadowComparisonStatus.BLOCKED_SHADOW_NOT_IMPLEMENTED)
        );

        assertEquals(ShadowLivePreviewStatus.PREVIEW_BLOCKED_SHADOW_FACTS_NOT_AVAILABLE, preview.previewStatus());
        assertEquals("NOT_IMPLEMENTED", preview.traceStatus());
        assertHasReason(preview.blockers(), "SHADOW_FACTS_NOT_AVAILABLE");
        assertHasEvidence(preview.missingEvidence(), "SHADOW_FACTS");
    }

    @Test
    void shouldBlockWhenDataQualityInsufficient() {
        ShadowLivePreview preview = previewWith(
                gateWithStatus(StrategyEvaluationGateStatus.BLOCKED_DATA_QUALITY),
                comparisonWithStatus(PaperShadowComparisonStatus.BLOCKED_DATA_QUALITY)
        );

        assertEquals(ShadowLivePreviewStatus.PREVIEW_BLOCKED_DATA_QUALITY, preview.previewStatus());
        assertEquals("BLOCKED_DATA_QUALITY", preview.evaluationGateStatus());
        assertHasReason(preview.blockers(), "DATA_QUALITY_BLOCKED");
        assertHasEvidence(preview.missingEvidence(), "DATASET");
    }

    @Test
    void shouldBlockWhenTraceChainIncomplete() {
        ShadowLivePreview preview = previewWith(
                readyGate(),
                comparisonWithStatus(PaperShadowComparisonStatus.BLOCKED_TRACE_INCOMPLETE)
        );

        assertEquals(ShadowLivePreviewStatus.PREVIEW_BLOCKED_TRACE_CHAIN_INCOMPLETE, preview.previewStatus());
        assertEquals("BLOCKED_TRACE_INCOMPLETE", preview.traceStatus());
        assertHasReason(preview.blockers(), "TRACE_CHAIN_INCOMPLETE");
        assertHasEvidence(preview.missingEvidence(), "TRACE_CHAIN");
    }

    @Test
    void shouldReturnReadyForNoSideEffectPreviewWhenEvidenceSatisfied() {
        ShadowLivePreview preview = previewWith(readyGate(), readyComparison());

        assertEquals(ShadowLivePreviewStatus.READY_FOR_NO_SIDE_EFFECT_PREVIEW, preview.previewStatus());
        assertEquals("SKELETON_AVAILABLE", preview.runnerStatus());
        assertEquals("READY_FOR_SHADOW_REVIEW", preview.evaluationGateStatus());
        assertEquals("READY_FOR_COMPARISON", preview.paperShadowComparisonStatus());
        assertEquals("SATISFIED", preview.inputFactStatus());
        assertEquals("PREVIEW_ONLY", preview.traceStatus());
        assertEquals("NOT_EXECUTED", preview.orderIntentPreviewStatus());
        assertEquals("PREVIEW_ONLY", preview.riskPreflightPreviewStatus());
        assertTrue(preview.blockers().isEmpty());
        assertTrue(preview.missingEvidence().isEmpty());
        assertHasReason(preview.warnings(), "SHADOW_LIVE_SKELETON_NOT_TRADING_AUTHORIZATION");
    }

    @Test
    void shouldExposeSideEffectPolicyAndKeepServiceReadOnly() throws Exception {
        ShadowLivePreview preview = previewWith(readyGate(), readyComparison());
        Method previewMethod = ShadowLivePreviewService.class.getMethod("preview", ShadowLivePreviewQuery.class);
        Transactional transactional = previewMethod.getAnnotation(Transactional.class);

        assertNotNull(transactional);
        assertTrue(transactional.readOnly());
        assertHasPolicy(preview.sideEffectPolicy(), "NO_DB_WRITE");
        assertHasPolicy(preview.sideEffectPolicy(), "NO_EXTERNAL_IO");
        assertHasPolicy(preview.sideEffectPolicy(), "NO_CREDENTIAL_ACCESS");
        assertHasPolicy(preview.sideEffectPolicy(), "NO_PRIVATE_ENDPOINT");
        assertHasPolicy(preview.sideEffectPolicy(), "NO_ORDER_SUBMISSION");
        assertHasPolicy(preview.sideEffectPolicy(), "NO_LEDGER_MUTATION");
        assertHasPolicy(preview.sideEffectPolicy(), "NO_ACCOUNT_MUTATION");
        assertTrue(Arrays.stream(ShadowLivePreviewService.class.getDeclaredMethods())
                .map(Method::getName)
                .noneMatch(name -> name.startsWith("save")
                        || name.startsWith("create")
                        || name.startsWith("update")
                        || name.startsWith("delete")
                        || name.startsWith("start")
                        || name.startsWith("execute")));
    }

    @Test
    void shouldNotExposeAuthorizationOrSensitiveTermsInReadModel() {
        ShadowLivePreview preview = previewWith(readyGate(), readyComparison());
        String serializedShape = preview.toString();

        assertFalse(serializedShape.contains("tradingReady"));
        assertFalse(serializedShape.contains("liveReady"));
        assertFalse(serializedShape.contains("authorizedForTrading"));
        assertFalse(serializedShape.contains("TRADE_APPROVED"));
        assertFalse(serializedShape.contains("LIVE_READY"));
        assertFalse(serializedShape.contains("AUTHORIZED"));
        assertFalse(serializedShape.contains("apiKey"));
        assertFalse(serializedShape.contains("secret"));
        assertFalse(serializedShape.contains("token"));
        assertFalse(serializedShape.contains("passphrase"));
        assertFalse(serializedShape.contains("credential"));
        assertFalse(serializedShape.contains("private key"));
        assertFalse(serializedShape.contains("encrypted_payload"));
        assertFalse(serializedShape.contains("decrypted_payload"));
    }

    private ShadowLivePreview previewWith(StrategyEvaluationGate gate, PaperShadowComparison comparison) {
        StubGateService gateService = new StubGateService(gate);
        StubComparisonService comparisonService = new StubComparisonService(comparison);
        ShadowLivePreviewService service = new ShadowLivePreviewService(gateService, comparisonService, FIXED_CLOCK);
        ShadowLivePreview preview = service.preview(query());
        assertEquals(1, gateService.callCount);
        assertEquals(1, comparisonService.callCount);
        return preview;
    }

    private ShadowLivePreviewQuery query() {
        return new ShadowLivePreviewQuery(
                "strategy-alpha",
                "sv-1",
                DATASET_ID,
                "eval-1",
                "pub-1",
                "ptr-1",
                "shr-1"
        );
    }

    private StrategyEvaluationGate readyGate() {
        return gate(
                StrategyEvaluationGateStatus.READY_FOR_SHADOW_REVIEW,
                List.of(
                        evidence("STRATEGY_VERSION", "SATISFIED"),
                        evidence("DATASET", "SATISFIED"),
                        evidence("EVALUATION", "SATISFIED"),
                        evidence("PUBLISH_TRACE", "SATISFIED"),
                        evidence("PAPER_EVIDENCE", "SATISFIED")
                ),
                List.of()
        );
    }

    private StrategyEvaluationGate gateWithStatus(StrategyEvaluationGateStatus status) {
        List<StrategyEvaluationGateEvidence> evidence = switch (status) {
            case BLOCKED_MISSING_STRATEGY_VERSION -> List.of(
                    evidence("STRATEGY_VERSION", "MISSING"),
                    evidence("DATASET", "NOT_AVAILABLE"),
                    evidence("EVALUATION", "NOT_AVAILABLE"),
                    evidence("PUBLISH_TRACE", "NOT_AVAILABLE"),
                    evidence("PAPER_EVIDENCE", "NOT_AVAILABLE")
            );
            case BLOCKED_DATA_QUALITY, BLOCKED_MISSING_DATASET -> List.of(
                    evidence("STRATEGY_VERSION", "SATISFIED"),
                    evidence("DATASET", "FAILED"),
                    evidence("EVALUATION", "NOT_AVAILABLE"),
                    evidence("PUBLISH_TRACE", "NOT_AVAILABLE"),
                    evidence("PAPER_EVIDENCE", "NOT_AVAILABLE")
            );
            case BLOCKED_MISSING_PAPER_EVIDENCE -> List.of(
                    evidence("STRATEGY_VERSION", "SATISFIED"),
                    evidence("DATASET", "SATISFIED"),
                    evidence("EVALUATION", "SATISFIED"),
                    evidence("PUBLISH_TRACE", "SATISFIED"),
                    evidence("PAPER_EVIDENCE", "MISSING")
            );
            default -> List.of(
                    evidence("STRATEGY_VERSION", "SATISFIED"),
                    evidence("DATASET", "SATISFIED"),
                    evidence("EVALUATION", "FAILED"),
                    evidence("PUBLISH_TRACE", "NOT_AVAILABLE"),
                    evidence("PAPER_EVIDENCE", "NOT_AVAILABLE")
            );
        };
        return gate(status, evidence, List.of(new StrategyEvaluationGateReason(
                status.name(),
                "BLOCKER",
                "Gate blocked for test."
        )));
    }

    private StrategyEvaluationGate gate(
            StrategyEvaluationGateStatus status,
            List<StrategyEvaluationGateEvidence> requiredEvidence,
            List<StrategyEvaluationGateReason> blockers
    ) {
        return new StrategyEvaluationGate(
                new StrategyEvaluationGateScope("strategy-alpha", "sv-1", DATASET_ID, "eval-1", "pub-1", "ptr-1"),
                "strategy-alpha",
                "sv-1",
                DATASET_ID,
                "eval-1",
                "pub-1",
                "ptr-1",
                status,
                status == StrategyEvaluationGateStatus.READY_FOR_SHADOW_REVIEW
                        ? StrategyEvaluationGateDecision.RESEARCH_EVALUATION_READY_FOR_SHADOW_REVIEW
                        : StrategyEvaluationGateDecision.RESEARCH_EVALUATION_BLOCKED,
                status == StrategyEvaluationGateStatus.READY_FOR_SHADOW_REVIEW ? "SUCCEEDED" : "FAILED",
                status == StrategyEvaluationGateStatus.BLOCKED_DATA_QUALITY ? "GAP_DETECTED" : "OK",
                status == StrategyEvaluationGateStatus.BLOCKED_MISSING_PAPER_EVIDENCE ? "NOT_AVAILABLE" : "STOPPED",
                status == StrategyEvaluationGateStatus.READY_FOR_SHADOW_REVIEW ? "SUCCEEDED" : "NOT_AVAILABLE",
                requiredEvidence,
                requiredEvidence.stream().filter(evidence -> !"SATISFIED".equals(evidence.status())).toList(),
                blockers,
                List.of(new StrategyEvaluationGateReason(
                        "EVALUATION_GATE_NOT_TRADING_AUTHORIZATION",
                        "WARNING",
                        "Evaluation gate is not trading authorization."
                )),
                List.of("Gate next step."),
                Instant.parse("2026-07-05T10:00:00Z")
        );
    }

    private PaperShadowComparison readyComparison() {
        return comparison(
                PaperShadowComparisonStatus.READY_FOR_COMPARISON,
                "SATISFIED",
                "COMPLETED",
                List.of(
                        comparisonEvidence("STRATEGY_VERSION", "SATISFIED"),
                        comparisonEvidence("DATASET", "SATISFIED"),
                        comparisonEvidence("EVALUATION_GATE", "SATISFIED"),
                        comparisonEvidence("PUBLISH_TRACE", "SATISFIED"),
                        comparisonEvidence("PAPER_RUN", "SATISFIED"),
                        comparisonEvidence("SHADOW_RUN", "SATISFIED"),
                        comparisonEvidence("TRACE_CHAIN", "SATISFIED")
                ),
                List.of()
        );
    }

    private PaperShadowComparison comparisonWithStatus(PaperShadowComparisonStatus status) {
        String shadowEvidenceStatus = switch (status) {
            case BLOCKED_SHADOW_NOT_IMPLEMENTED, NOT_IMPLEMENTED -> "NOT_IMPLEMENTED";
            case BLOCKED_MISSING_SHADOW_RUN -> "NOT_AVAILABLE";
            default -> "FAILED";
        };
        String shadowRunStatus = "NOT_IMPLEMENTED".equals(shadowEvidenceStatus)
                ? "NOT_IMPLEMENTED"
                : "NOT_AVAILABLE";
        List<PaperShadowComparisonEvidence> evidence = List.of(
                comparisonEvidence("STRATEGY_VERSION", "SATISFIED"),
                comparisonEvidence("DATASET", status == PaperShadowComparisonStatus.BLOCKED_DATA_QUALITY ? "FAILED" : "SATISFIED"),
                comparisonEvidence("EVALUATION_GATE", status == PaperShadowComparisonStatus.BLOCKED_EVALUATION_GATE ? "FAILED" : "SATISFIED"),
                comparisonEvidence("PUBLISH_TRACE", "SATISFIED"),
                comparisonEvidence("PAPER_RUN", status == PaperShadowComparisonStatus.BLOCKED_MISSING_PAPER_RUN ? "MISSING" : "SATISFIED"),
                comparisonEvidence("SHADOW_RUN", shadowEvidenceStatus),
                comparisonEvidence("TRACE_CHAIN", status == PaperShadowComparisonStatus.BLOCKED_TRACE_INCOMPLETE ? "FAILED" : "NOT_AVAILABLE")
        );
        return comparison(
                status,
                shadowEvidenceStatus,
                shadowRunStatus,
                evidence,
                List.of(new PaperShadowComparisonReason(
                        status == PaperShadowComparisonStatus.BLOCKED_EVALUATION_GATE
                                ? "EVALUATION_GATE_BLOCKED"
                                : status.name(),
                        "BLOCKER",
                        "Comparison blocked for test."
                ))
        );
    }

    private PaperShadowComparison comparison(
            PaperShadowComparisonStatus status,
            String shadowEvidenceStatus,
            String shadowRunStatus,
            List<PaperShadowComparisonEvidence> requiredEvidence,
            List<PaperShadowComparisonReason> blockers
    ) {
        return new PaperShadowComparison(
                new PaperShadowComparisonScope("strategy-alpha", "sv-1", DATASET_ID, "eval-1", "pub-1", "ptr-1", "shr-1"),
                "strategy-alpha",
                "sv-1",
                DATASET_ID,
                "eval-1",
                "pub-1",
                "ptr-1",
                "shr-1",
                "STOPPED",
                shadowRunStatus,
                status,
                status == PaperShadowComparisonStatus.BLOCKED_EVALUATION_GATE ? "BLOCKED_EVALUATION_GATE" : "PASSED",
                status == PaperShadowComparisonStatus.BLOCKED_MISSING_PAPER_RUN ? "NOT_AVAILABLE" : "SATISFIED",
                shadowEvidenceStatus,
                status == PaperShadowComparisonStatus.BLOCKED_DATA_QUALITY ? "GAP_DETECTED" : "OK",
                status == PaperShadowComparisonStatus.READY_FOR_COMPARISON,
                requiredEvidence,
                requiredEvidence.stream().filter(evidence -> !"SATISFIED".equals(evidence.status())).toList(),
                blockers,
                List.of(new PaperShadowComparisonReason(
                        "COMPARISON_NOT_TRADING_AUTHORIZATION",
                        "WARNING",
                        "Comparison is read-only evidence readiness only."
                )),
                List.of("Comparison next step."),
                Instant.parse("2026-07-05T11:00:00Z")
        );
    }

    private StrategyEvaluationGateEvidence evidence(String code, String status) {
        return new StrategyEvaluationGateEvidence(code, status, code + " " + status);
    }

    private PaperShadowComparisonEvidence comparisonEvidence(String code, String status) {
        return new PaperShadowComparisonEvidence(code, status, code + " " + status);
    }

    private void assertHasEvidence(Iterable<ShadowLivePreviewEvidence> evidenceItems, String code) {
        for (ShadowLivePreviewEvidence evidence : evidenceItems) {
            if (code.equals(evidence.code())) {
                return;
            }
        }
        throw new AssertionError("expected evidence code: " + code);
    }

    private void assertHasReason(Iterable<ShadowLivePreviewReason> reasons, String code) {
        for (ShadowLivePreviewReason reason : reasons) {
            if (code.equals(reason.code())) {
                return;
            }
        }
        throw new AssertionError("expected reason code: " + code);
    }

    private void assertHasPolicy(Iterable<ShadowLivePreviewSideEffectPolicy> policies, String code) {
        for (ShadowLivePreviewSideEffectPolicy policy : policies) {
            if (code.equals(policy.code()) && "FORBIDDEN".equals(policy.status())) {
                return;
            }
        }
        throw new AssertionError("expected side-effect policy: " + code);
    }

    private static final class StubGateService extends StrategyEvaluationGateService {
        private StrategyEvaluationGate gate;
        private int callCount;

        private StubGateService(StrategyEvaluationGate gate) {
            super(failingGateRepository());
            this.gate = gate;
        }

        @Override
        public StrategyEvaluationGate evaluate(StrategyEvaluationGateQuery query) {
            callCount++;
            return gate;
        }
    }

    private static final class StubComparisonService extends PaperShadowComparisonService {
        private PaperShadowComparison comparison;
        private int callCount;

        private StubComparisonService(PaperShadowComparison comparison) {
            super(failingComparisonRepository());
            this.comparison = comparison;
        }

        @Override
        public PaperShadowComparison compare(PaperShadowComparisonQuery query) {
            callCount++;
            return comparison;
        }
    }

    private static StrategyEvaluationGateFactRepository failingGateRepository() {
        return query -> {
            throw new AssertionError("stub service should not delegate to gate repository");
        };
    }

    private static PaperShadowComparisonFactRepository failingComparisonRepository() {
        return query -> {
            throw new AssertionError("stub service should not delegate to comparison repository");
        };
    }
}
