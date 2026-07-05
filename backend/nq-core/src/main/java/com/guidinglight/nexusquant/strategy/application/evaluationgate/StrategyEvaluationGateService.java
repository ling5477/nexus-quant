package com.guidinglight.nexusquant.strategy.application.evaluationgate;

import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyEvaluationGateFacts.DatasetFact;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyEvaluationGateFacts.EvaluationFact;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyEvaluationGateFacts.PaperEvidenceFact;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyEvaluationGateFacts.PublishTraceFact;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyEvaluationGateFacts.StrategyVersionFact;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * StrategyEvaluationGateService 编排 GateQ-1 strategy evaluation gate 只读基线。
 *
 * <p>Why: GateQ-1 需要把 strategy version、dataset quality、evaluation、publish trace 和 Paper evidence
 * 组合成一个 fail-closed 诊断结果。所有判定在 core 中完成，infra 只负责读取本地表；本 service 不启动
 * backtest / publish / Paper / Shadow，不写库，不调用交易所，不读取 credential material，也不输出交易授权。
 */
@Service
public class StrategyEvaluationGateService {

    private final StrategyEvaluationGateFactRepository factRepository;
    private final Clock clock;

    /**
     * 生产构造器：注入只读事实端口。
     *
     * @param factRepository 只读 facts repository；不得包含写侧或外联能力
     */
    @Autowired
    public StrategyEvaluationGateService(StrategyEvaluationGateFactRepository factRepository) {
        this(factRepository, Clock.systemUTC());
    }

    StrategyEvaluationGateService(StrategyEvaluationGateFactRepository factRepository, Clock clock) {
        this.factRepository = Objects.requireNonNull(factRepository, "factRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 生成 Strategy Evaluation Gate 结果。
     *
     * <p>幂等/副作用：该方法只读本地事实并生成 DTO；不会写数据库、不会调用外部网络、不会读取凭证、
     * 不会创建或启动 Paper / Shadow run，也不会改变 evaluation / publish / paper run 状态。
     *
     * @param query 查询范围；strategyVersionId 缺失时直接 fail-closed
     * @return gate 诊断结果；满足全部证据时最多返回 READY_FOR_SHADOW_REVIEW
     */
    @Transactional(readOnly = true)
    public StrategyEvaluationGate evaluate(StrategyEvaluationGateQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        Instant generatedAt = Instant.now(clock);
        if (query.strategyVersionId() == null) {
            return blockedWithoutRepository(query, generatedAt);
        }
        StrategyEvaluationGateFacts facts = factRepository.loadFacts(query);
        if (facts == null) {
            return unknown(query, generatedAt);
        }
        return evaluateWithFacts(query, facts, generatedAt);
    }

    private StrategyEvaluationGate blockedWithoutRepository(StrategyEvaluationGateQuery query, Instant generatedAt) {
        List<StrategyEvaluationGateEvidence> requiredEvidence = List.of(
                missing("STRATEGY_VERSION", "strategyVersionId is required for fail-closed evaluation gate."),
                notAvailable("DATASET", "dataset evidence cannot be evaluated without strategyVersionId."),
                notAvailable("EVALUATION", "evaluation evidence cannot be evaluated without strategyVersionId."),
                notAvailable("PUBLISH_TRACE", "publish trace cannot be evaluated without strategyVersionId."),
                notAvailable("PAPER_EVIDENCE", "Paper evidence cannot be evaluated without strategyVersionId.")
        );
        return gate(
                query,
                null,
                StrategyEvaluationGateStatus.BLOCKED_MISSING_STRATEGY_VERSION,
                requiredEvidence,
                List.of(blocker("STRATEGY_VERSION_ID_REQUIRED", "strategyVersionId is required.")),
                baseWarnings(),
                List.of("Provide a concrete strategyVersionId and rerun the read-only evaluation gate."),
                generatedAt
        );
    }

    private StrategyEvaluationGate unknown(StrategyEvaluationGateQuery query, Instant generatedAt) {
        return gate(
                query,
                null,
                StrategyEvaluationGateStatus.UNKNOWN,
                List.of(notAvailable("FACTS", "Local facts are not available for this scope.")),
                List.of(blocker("FACTS_NOT_AVAILABLE", "The local facts repository returned no evaluation facts.")),
                baseWarnings(),
                List.of("Verify local fact-source availability before starting any Shadow review."),
                generatedAt
        );
    }

    private StrategyEvaluationGate evaluateWithFacts(
            StrategyEvaluationGateQuery query,
            StrategyEvaluationGateFacts facts,
            Instant generatedAt
    ) {
        StrategyVersionFact strategyVersion = facts.strategyVersion();
        DatasetFact dataset = facts.dataset();
        EvaluationFact evaluation = facts.evaluation();
        PublishTraceFact publish = facts.publishTrace();
        PaperEvidenceFact paper = facts.paperEvidence();

        List<StrategyEvaluationGateEvidence> evidence = new ArrayList<>();
        evidence.add(strategyVersionEvidence(strategyVersion));
        evidence.add(datasetEvidence(dataset));
        evidence.add(evaluationEvidence(evaluation));
        evidence.add(publishEvidence(publish));
        evidence.add(paperEvidence(paper));

        List<StrategyEvaluationGateReason> blockers = new ArrayList<>();
        StrategyEvaluationGateStatus status = firstBlockingStatus(
                strategyVersion,
                dataset,
                evaluation,
                publish,
                paper,
                blockers
        );
        List<StrategyEvaluationGateReason> warnings = warnings(evaluation, dataset, paper);
        List<String> nextSteps = nextSteps(status);
        return gate(query, facts, status, evidence, blockers, warnings, nextSteps, generatedAt);
    }

    private StrategyEvaluationGateStatus firstBlockingStatus(
            StrategyVersionFact strategyVersion,
            DatasetFact dataset,
            EvaluationFact evaluation,
            PublishTraceFact publish,
            PaperEvidenceFact paper,
            List<StrategyEvaluationGateReason> blockers
    ) {
        if (!strategyVersion.present()) {
            blockers.add(blocker("STRATEGY_VERSION_NOT_FOUND", "Strategy version fact is missing."));
            return StrategyEvaluationGateStatus.BLOCKED_MISSING_STRATEGY_VERSION;
        }
        if (!strategyVersion.matchesRequestedStrategy()) {
            blockers.add(blocker("STRATEGY_SCOPE_MISMATCH", "Requested strategyId does not match strategy version."));
            return StrategyEvaluationGateStatus.BLOCKED_MISSING_STRATEGY_VERSION;
        }
        if (!strategyVersion.activeForEvaluation()) {
            blockers.add(blocker("STRATEGY_VERSION_NOT_ACTIVE", "Strategy version must be ACTIVE for GateQ-1 review."));
            return StrategyEvaluationGateStatus.BLOCKED_MISSING_STRATEGY_VERSION;
        }
        if (!dataset.present()) {
            blockers.add(blocker("DATASET_MISSING", "Dataset fact is missing or datasetId was not provided."));
            return StrategyEvaluationGateStatus.BLOCKED_MISSING_DATASET;
        }
        if (!dataset.qualitySufficient()) {
            blockers.add(blocker(
                    "DATASET_QUALITY_BLOCKED",
                    "Dataset must be READY/OK with no missing, duplicate or invalid bars."
            ));
            return StrategyEvaluationGateStatus.BLOCKED_DATA_QUALITY;
        }
        if (!evaluation.present()) {
            blockers.add(blocker("EVALUATION_MISSING", "Evaluation report is missing."));
            return StrategyEvaluationGateStatus.BLOCKED_MISSING_EVALUATION;
        }
        if (!evaluation.succeeded()) {
            blockers.add(blocker("EVALUATION_FAILED", "Evaluation report must be SUCCEEDED."));
            return StrategyEvaluationGateStatus.BLOCKED_EVALUATION_FAILED;
        }
        if (!publish.present() || !publish.succeeded()) {
            blockers.add(blocker("PUBLISH_TRACE_MISSING", "Successful publish trace is missing."));
            return StrategyEvaluationGateStatus.BLOCKED_NOT_PUBLISHED;
        }
        if (!paper.sufficient()) {
            blockers.add(blocker(
                    "PAPER_EVIDENCE_MISSING",
                    "A SIM Paper run with RUNNING or STOPPED evidence is required."
            ));
            return StrategyEvaluationGateStatus.BLOCKED_MISSING_PAPER_EVIDENCE;
        }
        return StrategyEvaluationGateStatus.READY_FOR_SHADOW_REVIEW;
    }

    private StrategyEvaluationGateEvidence strategyVersionEvidence(StrategyVersionFact fact) {
        if (!fact.present()) {
            return missing("STRATEGY_VERSION", "Strategy version does not exist.");
        }
        if (!fact.matchesRequestedStrategy()) {
            return failed("STRATEGY_VERSION", "Strategy version does not match requested strategyId.");
        }
        if (!fact.activeForEvaluation()) {
            return failed("STRATEGY_VERSION", "Strategy version status must be ACTIVE.");
        }
        return satisfied("STRATEGY_VERSION", "Strategy version exists and is ACTIVE.");
    }

    private StrategyEvaluationGateEvidence datasetEvidence(DatasetFact fact) {
        if (!fact.present()) {
            return missing("DATASET", "Dataset fact is missing.");
        }
        if (!fact.qualitySufficient()) {
            return failed("DATASET", "Dataset quality is not sufficient for Shadow review.");
        }
        return satisfied("DATASET", "Dataset exists and quality facts are OK.");
    }

    private StrategyEvaluationGateEvidence evaluationEvidence(EvaluationFact fact) {
        if (!fact.present()) {
            return missing("EVALUATION", "Evaluation report is missing.");
        }
        if (!fact.succeeded()) {
            return failed("EVALUATION", "Evaluation report is not SUCCEEDED.");
        }
        return satisfied("EVALUATION", "Evaluation report is SUCCEEDED.");
    }

    private StrategyEvaluationGateEvidence publishEvidence(PublishTraceFact fact) {
        if (!fact.present()) {
            return missing("PUBLISH_TRACE", "Publish trace is missing.");
        }
        if (!fact.succeeded()) {
            return failed("PUBLISH_TRACE", "Publish trace is not SUCCEEDED.");
        }
        return satisfied("PUBLISH_TRACE", "Publish trace is SUCCEEDED.");
    }

    private StrategyEvaluationGateEvidence paperEvidence(PaperEvidenceFact fact) {
        if (!fact.present()) {
            return missing("PAPER_EVIDENCE", "Paper evidence is missing.");
        }
        if (!fact.sufficient()) {
            return failed("PAPER_EVIDENCE", "Paper evidence is not a sufficient SIM Paper run.");
        }
        return satisfied("PAPER_EVIDENCE", "SIM Paper evidence exists.");
    }

    private List<StrategyEvaluationGateReason> warnings(
            EvaluationFact evaluation,
            DatasetFact dataset,
            PaperEvidenceFact paper
    ) {
        List<StrategyEvaluationGateReason> warnings = new ArrayList<>(baseWarnings());
        if (evaluation.present() && !evaluation.metricsComplete()) {
            warnings.add(warning(
                    "EVALUATION_METRICS_INCOMPLETE",
                    "Evaluation report exists but some metrics are missing; review should keep confidence limited."
            ));
        }
        if (dataset.present() && dataset.latestCoverageAt() == null) {
            warnings.add(warning(
                    "DATASET_COVERAGE_NOT_AVAILABLE",
                    "Dataset coverage timestamp is not available; do not infer freshness from this gate."
            ));
        }
        if (paper.present() && "RUNNING".equalsIgnoreCase(paper.status())) {
            warnings.add(warning(
                    "PAPER_RUN_STILL_RUNNING",
                    "Paper run is still RUNNING; Shadow review should treat results as provisional."
            ));
        }
        return warnings;
    }

    private List<StrategyEvaluationGateReason> baseWarnings() {
        return List.of(
                warning(
                        "EVALUATION_GATE_NOT_TRADING_AUTHORIZATION",
                        "Evaluation gate is research/evaluation readiness only; it is not trading authorization."
                ),
                warning(
                        "SHADOW_LIVE_NOT_IMPLEMENTED",
                        "Shadow Live runner is not implemented in this baseline and must not be started by this API."
                ),
                warning(
                        "PYTHON_OFFLINE_ARTIFACT_NOT_JAVA_BOUND",
                        "Python Research remains offline foundation unless separately imported and bound to Java facts."
                )
        );
    }

    private List<String> nextSteps(StrategyEvaluationGateStatus status) {
        return switch (status) {
            case READY_FOR_SHADOW_REVIEW -> List.of(
                    "Start a separate Shadow review task if approved; keep no-order, no-LIVE and no-sensitive-material boundary.",
                    "Do not start Shadow Live runner from this read-only evaluation gate."
            );
            case BLOCKED_MISSING_STRATEGY_VERSION -> List.of(
                    "Select an existing ACTIVE strategyVersionId and rerun the read-only gate."
            );
            case BLOCKED_MISSING_DATASET -> List.of(
                    "Bind or select an existing datasetId with READY/OK local quality facts."
            );
            case BLOCKED_DATA_QUALITY -> List.of(
                    "Review Data Quality Center facts and fix dataset gaps before Shadow review."
            );
            case BLOCKED_MISSING_EVALUATION -> List.of(
                    "Create or select an existing successful evaluation report before publish/Shadow review."
            );
            case BLOCKED_EVALUATION_FAILED -> List.of(
                    "Investigate failed evaluation and rerun evaluation in a separate allowed workflow."
            );
            case BLOCKED_NOT_PUBLISHED -> List.of(
                    "Create or select an existing SUCCEEDED publish trace tied to this strategy version and evaluation."
            );
            case BLOCKED_MISSING_PAPER_EVIDENCE -> List.of(
                    "Select an existing SIM Paper run with RUNNING or STOPPED evidence before Shadow review."
            );
            case UNKNOWN, NOT_AVAILABLE -> List.of(
                    "Verify local fact-source availability; do not infer readiness from missing facts."
            );
        };
    }

    private StrategyEvaluationGate gate(
            StrategyEvaluationGateQuery query,
            StrategyEvaluationGateFacts facts,
            StrategyEvaluationGateStatus status,
            List<StrategyEvaluationGateEvidence> requiredEvidence,
            List<StrategyEvaluationGateReason> blockers,
            List<StrategyEvaluationGateReason> warnings,
            List<String> nextSteps,
            Instant generatedAt
    ) {
        List<StrategyEvaluationGateEvidence> missingEvidence = requiredEvidence.stream()
                .filter(evidence -> !"SATISFIED".equals(evidence.status()))
                .toList();
        StrategyEvaluationGateScope scope = scope(query, facts);
        return new StrategyEvaluationGate(
                scope,
                scope.strategyId(),
                scope.strategyVersionId(),
                scope.datasetId(),
                scope.evaluationId(),
                scope.publishId(),
                scope.paperRunId(),
                status,
                decision(status),
                evaluationStatus(facts),
                datasetQualityStatus(facts),
                paperEvidenceStatus(facts),
                publishTraceStatus(facts),
                requiredEvidence,
                missingEvidence,
                blockers,
                warnings,
                nextSteps,
                generatedAt
        );
    }

    private StrategyEvaluationGateScope scope(StrategyEvaluationGateQuery query, StrategyEvaluationGateFacts facts) {
        if (facts == null) {
            return new StrategyEvaluationGateScope(
                    query.strategyId(),
                    query.strategyVersionId(),
                    query.datasetId(),
                    query.evaluationId(),
                    query.publishId(),
                    query.paperRunId()
            );
        }
        return new StrategyEvaluationGateScope(
                firstNonBlank(query.strategyId(), facts.strategyVersion().strategyId(), facts.strategyVersion().strategyCode()),
                firstNonBlank(query.strategyVersionId(), facts.strategyVersion().strategyVersionId()),
                query.datasetId() == null ? facts.dataset().datasetId() : query.datasetId(),
                firstNonBlank(query.evaluationId(), facts.evaluation().evaluationId(), facts.publishTrace().evaluationId()),
                firstNonBlank(query.publishId(), facts.publishTrace().publishId(), facts.paperEvidence().publishId()),
                firstNonBlank(query.paperRunId(), facts.paperEvidence().paperRunId())
        );
    }

    private StrategyEvaluationGateDecision decision(StrategyEvaluationGateStatus status) {
        return switch (status) {
            case READY_FOR_SHADOW_REVIEW -> StrategyEvaluationGateDecision.RESEARCH_EVALUATION_READY_FOR_SHADOW_REVIEW;
            case UNKNOWN -> StrategyEvaluationGateDecision.RESEARCH_EVALUATION_UNKNOWN;
            case NOT_AVAILABLE -> StrategyEvaluationGateDecision.RESEARCH_EVALUATION_NOT_AVAILABLE;
            default -> StrategyEvaluationGateDecision.RESEARCH_EVALUATION_BLOCKED;
        };
    }

    private String evaluationStatus(StrategyEvaluationGateFacts facts) {
        if (facts == null || !facts.evaluation().present()) {
            return "NOT_AVAILABLE";
        }
        return facts.evaluation().status();
    }

    private String datasetQualityStatus(StrategyEvaluationGateFacts facts) {
        if (facts == null) {
            return "NOT_AVAILABLE";
        }
        return facts.dataset().effectiveQualityStatus();
    }

    private String paperEvidenceStatus(StrategyEvaluationGateFacts facts) {
        if (facts == null || !facts.paperEvidence().present()) {
            return "NOT_AVAILABLE";
        }
        return facts.paperEvidence().status();
    }

    private String publishTraceStatus(StrategyEvaluationGateFacts facts) {
        if (facts == null || !facts.publishTrace().present()) {
            return "NOT_AVAILABLE";
        }
        return facts.publishTrace().status();
    }

    private StrategyEvaluationGateEvidence satisfied(String code, String message) {
        return new StrategyEvaluationGateEvidence(code, "SATISFIED", message);
    }

    private StrategyEvaluationGateEvidence missing(String code, String message) {
        return new StrategyEvaluationGateEvidence(code, "MISSING", message);
    }

    private StrategyEvaluationGateEvidence failed(String code, String message) {
        return new StrategyEvaluationGateEvidence(code, "FAILED", message);
    }

    private StrategyEvaluationGateEvidence notAvailable(String code, String message) {
        return new StrategyEvaluationGateEvidence(code, "NOT_AVAILABLE", message);
    }

    private StrategyEvaluationGateReason blocker(String code, String message) {
        return new StrategyEvaluationGateReason(code, "BLOCKER", message);
    }

    private StrategyEvaluationGateReason warning(String code, String message) {
        return new StrategyEvaluationGateReason(code, "WARNING", message);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
