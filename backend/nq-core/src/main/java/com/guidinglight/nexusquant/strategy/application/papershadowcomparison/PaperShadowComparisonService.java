package com.guidinglight.nexusquant.strategy.application.papershadowcomparison;

import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonFacts.DatasetFact;
import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonFacts.EvaluationFact;
import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonFacts.PaperRunFact;
import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonFacts.PublishTraceFact;
import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonFacts.ShadowRunFact;
import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonFacts.StrategyVersionFact;

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
 * PaperShadowComparisonService 编排 GateQ-2 Paper vs Shadow 只读对照 baseline。
 *
 * <p>Why: GateQ-2 只需要把已有 strategy version、dataset、evaluation、publish 和 Paper facts
 * 组合成可复盘的比较准备度，并显式返回 Shadow 未实现 / 缺失。该 service 不写库、不启动 Paper run、
 * 不启动 Shadow runner、不调用真实交易所、不读取敏感材料，也不输出交易授权。
 */
@Service
public class PaperShadowComparisonService {

    private final PaperShadowComparisonFactRepository factRepository;
    private final Clock clock;

    /**
     * 生产构造器：注入只读事实端口。
     *
     * @param factRepository 只读 facts repository；不得包含写侧、runner 或外联能力
     */
    @Autowired
    public PaperShadowComparisonService(PaperShadowComparisonFactRepository factRepository) {
        this(factRepository, Clock.systemUTC());
    }

    PaperShadowComparisonService(PaperShadowComparisonFactRepository factRepository, Clock clock) {
        this.factRepository = Objects.requireNonNull(factRepository, "factRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 生成 Paper vs Shadow 只读对照结果。
     *
     * <p>幂等/副作用：该方法只读本地事实并生成 DTO；不会写数据库、不会调用外部网络、不会读取
     * 敏感材料、不会创建或启动 Paper / Shadow run，也不会改变 evaluation / publish / Paper 状态。
     *
     * @param query 查询范围；strategyVersionId 缺失时直接 fail-closed 且不访问 repository
     * @return 对照诊断结果；Shadow 未实现时必须 fail-closed
     */
    @Transactional(readOnly = true)
    public PaperShadowComparison compare(PaperShadowComparisonQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        Instant generatedAt = Instant.now(clock);
        if (query.strategyVersionId() == null) {
            return blockedWithoutRepository(query, generatedAt);
        }
        PaperShadowComparisonFacts facts = factRepository.loadFacts(query);
        if (facts == null) {
            return unknown(query, generatedAt);
        }
        return compareWithFacts(query, facts, generatedAt);
    }

    private PaperShadowComparison blockedWithoutRepository(PaperShadowComparisonQuery query, Instant generatedAt) {
        List<PaperShadowComparisonEvidence> requiredEvidence = List.of(
                missing("STRATEGY_VERSION", "strategyVersionId is required for fail-closed comparison."),
                notAvailable("DATASET", "dataset evidence cannot be evaluated without strategyVersionId."),
                notAvailable("EVALUATION_GATE", "evaluation gate cannot be evaluated without strategyVersionId."),
                notAvailable("PUBLISH_TRACE", "publish trace cannot be evaluated without strategyVersionId."),
                notAvailable("PAPER_RUN", "Paper run evidence cannot be evaluated without strategyVersionId."),
                notImplemented("SHADOW_RUN", "Shadow run evidence is not implemented in current baseline."),
                notAvailable("TRACE_CHAIN", "Trace chain cannot be evaluated without strategyVersionId.")
        );
        return comparison(
                query,
                null,
                PaperShadowComparisonStatus.BLOCKED_MISSING_STRATEGY_VERSION,
                requiredEvidence,
                List.of(blocker("STRATEGY_VERSION_ID_REQUIRED", "strategyVersionId is required.")),
                baseWarnings(),
                List.of("Provide a concrete strategyVersionId and rerun the read-only comparison."),
                generatedAt
        );
    }

    private PaperShadowComparison unknown(PaperShadowComparisonQuery query, Instant generatedAt) {
        return comparison(
                query,
                null,
                PaperShadowComparisonStatus.UNKNOWN,
                List.of(notAvailable("FACTS", "Local facts are not available for this scope.")),
                List.of(blocker("FACTS_NOT_AVAILABLE", "The local facts repository returned no comparison facts.")),
                baseWarnings(),
                List.of("Verify local fact-source availability; do not infer comparison readiness from missing facts."),
                generatedAt
        );
    }

    private PaperShadowComparison compareWithFacts(
            PaperShadowComparisonQuery query,
            PaperShadowComparisonFacts facts,
            Instant generatedAt
    ) {
        StrategyVersionFact strategyVersion = facts.strategyVersion();
        DatasetFact dataset = facts.dataset();
        EvaluationFact evaluation = facts.evaluation();
        PublishTraceFact publish = facts.publishTrace();
        PaperRunFact paper = facts.paperRun();
        ShadowRunFact shadow = facts.shadowRun();

        List<PaperShadowComparisonEvidence> evidence = new ArrayList<>();
        evidence.add(strategyVersionEvidence(strategyVersion));
        evidence.add(datasetEvidence(dataset));
        evidence.add(evaluationGateEvidence(evaluation));
        evidence.add(publishEvidence(publish));
        evidence.add(paperRunEvidence(paper));
        evidence.add(shadowRunEvidence(shadow));
        evidence.add(traceChainEvidence(facts));

        List<PaperShadowComparisonReason> blockers = new ArrayList<>();
        PaperShadowComparisonStatus status = firstBlockingStatus(
                strategyVersion,
                dataset,
                evaluation,
                publish,
                paper,
                shadow,
                facts,
                blockers
        );
        List<PaperShadowComparisonReason> warnings = warnings(evaluation, dataset, paper, shadow);
        List<String> nextSteps = nextSteps(status);
        return comparison(query, facts, status, evidence, blockers, warnings, nextSteps, generatedAt);
    }

    private PaperShadowComparisonStatus firstBlockingStatus(
            StrategyVersionFact strategyVersion,
            DatasetFact dataset,
            EvaluationFact evaluation,
            PublishTraceFact publish,
            PaperRunFact paper,
            ShadowRunFact shadow,
            PaperShadowComparisonFacts facts,
            List<PaperShadowComparisonReason> blockers
    ) {
        if (!strategyVersion.present()) {
            blockers.add(blocker("STRATEGY_VERSION_NOT_FOUND", "Strategy version fact is missing."));
            return PaperShadowComparisonStatus.BLOCKED_MISSING_STRATEGY_VERSION;
        }
        if (!strategyVersion.matchesRequestedStrategy()) {
            blockers.add(blocker("STRATEGY_SCOPE_MISMATCH", "Requested strategyId does not match strategy version."));
            return PaperShadowComparisonStatus.BLOCKED_MISSING_STRATEGY_VERSION;
        }
        if (!strategyVersion.activeForComparison()) {
            blockers.add(blocker("STRATEGY_VERSION_NOT_ACTIVE", "Strategy version must be ACTIVE for comparison."));
            return PaperShadowComparisonStatus.BLOCKED_MISSING_STRATEGY_VERSION;
        }
        if (!dataset.present()) {
            blockers.add(blocker("DATASET_MISSING", "Dataset fact is missing."));
            return PaperShadowComparisonStatus.BLOCKED_TRACE_INCOMPLETE;
        }
        if (!dataset.qualitySufficient()) {
            blockers.add(blocker("DATASET_QUALITY_BLOCKED", "Dataset quality is insufficient for comparison."));
            return PaperShadowComparisonStatus.BLOCKED_DATA_QUALITY;
        }
        if (!evaluation.present() || !evaluation.passedGate()) {
            blockers.add(blocker("EVALUATION_GATE_BLOCKED", "Evaluation gate is missing or not passed."));
            return PaperShadowComparisonStatus.BLOCKED_EVALUATION_GATE;
        }
        if (!publish.present() || !publish.succeeded()) {
            blockers.add(blocker("PUBLISH_TRACE_INCOMPLETE", "Successful publish trace is missing."));
            return PaperShadowComparisonStatus.BLOCKED_TRACE_INCOMPLETE;
        }
        if (!paper.comparableEvidence()) {
            blockers.add(blocker("PAPER_RUN_MISSING_OR_NOT_COMPARABLE", "Comparable SIM Paper run is missing."));
            return PaperShadowComparisonStatus.BLOCKED_MISSING_PAPER_RUN;
        }
        if (!shadow.runnerImplemented()) {
            blockers.add(blocker("SHADOW_RUNNER_NOT_IMPLEMENTED", "Shadow runner and shadow fact source are not implemented."));
            return PaperShadowComparisonStatus.BLOCKED_SHADOW_NOT_IMPLEMENTED;
        }
        if (!shadow.present()) {
            blockers.add(blocker("SHADOW_RUN_MISSING", "Shadow run fact is missing."));
            return PaperShadowComparisonStatus.BLOCKED_MISSING_SHADOW_RUN;
        }
        if (!shadow.comparableEvidence()) {
            blockers.add(blocker("SHADOW_RUN_NOT_COMPARABLE", "Shadow run status is not comparable."));
            return PaperShadowComparisonStatus.BLOCKED_MISSING_SHADOW_RUN;
        }
        if (!traceChainComplete(facts)) {
            blockers.add(blocker("TRACE_CHAIN_INCOMPLETE", "Strategy, dataset, evaluation, publish, Paper and Shadow trace chain is incomplete."));
            return PaperShadowComparisonStatus.BLOCKED_TRACE_INCOMPLETE;
        }
        return PaperShadowComparisonStatus.READY_FOR_COMPARISON;
    }

    private PaperShadowComparisonEvidence strategyVersionEvidence(StrategyVersionFact fact) {
        if (!fact.present()) {
            return missing("STRATEGY_VERSION", "Strategy version does not exist.");
        }
        if (!fact.matchesRequestedStrategy()) {
            return failed("STRATEGY_VERSION", "Strategy version does not match requested strategyId.");
        }
        if (!fact.activeForComparison()) {
            return failed("STRATEGY_VERSION", "Strategy version status must be ACTIVE.");
        }
        return satisfied("STRATEGY_VERSION", "Strategy version exists and is ACTIVE.");
    }

    private PaperShadowComparisonEvidence datasetEvidence(DatasetFact fact) {
        if (!fact.present()) {
            return missing("DATASET", "Dataset fact is missing.");
        }
        if (!fact.qualitySufficient()) {
            return failed("DATASET", "Dataset quality is not sufficient for comparison.");
        }
        return satisfied("DATASET", "Dataset exists and quality facts are OK.");
    }

    private PaperShadowComparisonEvidence evaluationGateEvidence(EvaluationFact fact) {
        if (!fact.present()) {
            return missing("EVALUATION_GATE", "Evaluation report is missing.");
        }
        if (!fact.passedGate()) {
            return failed("EVALUATION_GATE", "Evaluation report is not SUCCEEDED.");
        }
        return satisfied("EVALUATION_GATE", "Evaluation gate passed with SUCCEEDED report.");
    }

    private PaperShadowComparisonEvidence publishEvidence(PublishTraceFact fact) {
        if (!fact.present()) {
            return missing("PUBLISH_TRACE", "Publish trace is missing.");
        }
        if (!fact.succeeded()) {
            return failed("PUBLISH_TRACE", "Publish trace is not SUCCEEDED.");
        }
        return satisfied("PUBLISH_TRACE", "Publish trace is SUCCEEDED.");
    }

    private PaperShadowComparisonEvidence paperRunEvidence(PaperRunFact fact) {
        if (!fact.present()) {
            return missing("PAPER_RUN", "Paper run fact is missing.");
        }
        if (!fact.comparableEvidence()) {
            return failed("PAPER_RUN", "Paper run must be SIM and RUNNING or STOPPED.");
        }
        return satisfied("PAPER_RUN", "Comparable SIM Paper run exists.");
    }

    private PaperShadowComparisonEvidence shadowRunEvidence(ShadowRunFact fact) {
        if (!fact.runnerImplemented()) {
            return notImplemented("SHADOW_RUN", "Shadow run fact source is not implemented.");
        }
        if (!fact.present()) {
            return notAvailable("SHADOW_RUN", "Shadow run fact is missing.");
        }
        if (!fact.comparableEvidence()) {
            return failed("SHADOW_RUN", "Shadow run is not in a comparable status.");
        }
        return satisfied("SHADOW_RUN", "Comparable Shadow run exists.");
    }

    private PaperShadowComparisonEvidence traceChainEvidence(PaperShadowComparisonFacts facts) {
        if (!facts.shadowRun().runnerImplemented() || !facts.shadowRun().present()) {
            return notAvailable("TRACE_CHAIN", "Trace chain cannot be complete without Shadow run fact.");
        }
        if (!traceChainComplete(facts)) {
            return failed("TRACE_CHAIN", "Trace chain IDs are incomplete or inconsistent.");
        }
        return satisfied("TRACE_CHAIN", "Strategy, dataset, evaluation, publish, Paper and Shadow IDs are consistent.");
    }

    private boolean traceChainComplete(PaperShadowComparisonFacts facts) {
        StrategyVersionFact strategyVersion = facts.strategyVersion();
        DatasetFact dataset = facts.dataset();
        EvaluationFact evaluation = facts.evaluation();
        PublishTraceFact publish = facts.publishTrace();
        PaperRunFact paper = facts.paperRun();
        ShadowRunFact shadow = facts.shadowRun();
        if (!strategyVersion.present()
                || !dataset.present()
                || !evaluation.present()
                || !publish.present()
                || !paper.present()
                || !shadow.runnerImplemented()
                || !shadow.present()) {
            return false;
        }
        String strategyVersionId = strategyVersion.strategyVersionId();
        return equalsId(strategyVersionId, publish.strategyVersionId())
                && equalsId(evaluation.evaluationId(), publish.evaluationId())
                && equalsId(strategyVersionId, paper.strategyVersionId())
                && equalsId(publish.publishId(), paper.publishId())
                && equalsId(strategyVersionId, shadow.strategyVersionId())
                && equalsId(publish.publishId(), shadow.publishId())
                && (shadow.evaluationId() == null || equalsId(evaluation.evaluationId(), shadow.evaluationId()))
                && (shadow.datasetId() == null || Objects.equals(dataset.datasetId(), shadow.datasetId()));
    }

    private List<PaperShadowComparisonReason> warnings(
            EvaluationFact evaluation,
            DatasetFact dataset,
            PaperRunFact paper,
            ShadowRunFact shadow
    ) {
        List<PaperShadowComparisonReason> warnings = new ArrayList<>(baseWarnings());
        if (evaluation.present() && !evaluation.metricsComplete()) {
            warnings.add(warning(
                    "EVALUATION_METRICS_INCOMPLETE",
                    "Evaluation report exists but some metrics are missing; comparison confidence remains limited."
            ));
        }
        if (dataset.present() && dataset.latestCoverageAt() == null) {
            warnings.add(warning(
                    "DATASET_COVERAGE_NOT_AVAILABLE",
                    "Dataset coverage timestamp is not available; do not infer freshness from this comparison."
            ));
        }
        if (paper.present() && "RUNNING".equalsIgnoreCase(paper.status())) {
            warnings.add(warning(
                    "PAPER_RUN_STILL_RUNNING",
                    "Paper run is still RUNNING; comparison should treat Paper results as provisional."
            ));
        }
        if (!shadow.runnerImplemented()) {
            warnings.add(warning(
                    "SHADOW_FACT_SOURCE_NOT_IMPLEMENTED",
                    "Shadow fact source is not implemented; current response must stay fail-closed."
            ));
        }
        return warnings;
    }

    private List<PaperShadowComparisonReason> baseWarnings() {
        return List.of(
                warning(
                        "COMPARISON_NOT_TRADING_AUTHORIZATION",
                        "READY_FOR_COMPARISON is read-only evidence readiness only; it is not trading authorization."
                ),
                warning(
                        "SHADOW_RUNNER_NOT_IMPLEMENTED",
                        "Shadow runner is not implemented and must not be started by this API."
                ),
                warning(
                        "PYTHON_OFFLINE_ARTIFACT_NOT_BOUND",
                        "Python Research artifacts remain offline unless separately imported and bound to Java facts."
                )
        );
    }

    private List<String> nextSteps(PaperShadowComparisonStatus status) {
        return switch (status) {
            case READY_FOR_COMPARISON -> List.of(
                    "Use this result only for read-only Paper vs Shadow inspection.",
                    "Keep no-order, no-LIVE and no-sensitive-material boundary in any follow-up."
            );
            case BLOCKED_MISSING_STRATEGY_VERSION -> List.of(
                    "Select an existing ACTIVE strategyVersionId and rerun the read-only comparison."
            );
            case BLOCKED_EVALUATION_GATE -> List.of(
                    "Select or create a successful evaluation report in a separate allowed workflow."
            );
            case BLOCKED_MISSING_PAPER_RUN -> List.of(
                    "Select an existing SIM Paper run with RUNNING or STOPPED evidence before comparison."
            );
            case BLOCKED_SHADOW_NOT_IMPLEMENTED -> List.of(
                    "Do not fabricate Shadow facts; implement Shadow read-only fact source in a separate approved GateQ task."
            );
            case BLOCKED_MISSING_SHADOW_RUN -> List.of(
                    "Select an existing comparable Shadow run after Shadow fact source exists."
            );
            case BLOCKED_DATA_QUALITY -> List.of(
                    "Fix dataset quality gaps before Paper vs Shadow comparison."
            );
            case BLOCKED_TRACE_INCOMPLETE -> List.of(
                    "Repair or select a complete strategyVersion -> dataset -> evaluation -> publish -> Paper -> Shadow trace chain."
            );
            case UNKNOWN, NOT_AVAILABLE, NOT_IMPLEMENTED -> List.of(
                    "Verify local fact-source availability; do not infer comparison readiness from missing facts."
            );
        };
    }

    private PaperShadowComparison comparison(
            PaperShadowComparisonQuery query,
            PaperShadowComparisonFacts facts,
            PaperShadowComparisonStatus status,
            List<PaperShadowComparisonEvidence> requiredEvidence,
            List<PaperShadowComparisonReason> blockers,
            List<PaperShadowComparisonReason> warnings,
            List<String> nextSteps,
            Instant generatedAt
    ) {
        List<PaperShadowComparisonEvidence> missingEvidence = requiredEvidence.stream()
                .filter(evidence -> !"SATISFIED".equals(evidence.status()))
                .toList();
        PaperShadowComparisonScope scope = scope(query, facts);
        return new PaperShadowComparison(
                scope,
                scope.strategyId(),
                scope.strategyVersionId(),
                scope.datasetId(),
                scope.evaluationId(),
                scope.publishId(),
                scope.paperRunId(),
                scope.shadowRunId(),
                paperRunStatus(facts),
                shadowRunStatus(facts),
                status,
                evaluationGateStatus(facts),
                paperEvidenceStatus(facts),
                shadowEvidenceStatus(facts),
                dataQualityStatus(facts),
                status == PaperShadowComparisonStatus.READY_FOR_COMPARISON,
                requiredEvidence,
                missingEvidence,
                blockers,
                warnings,
                nextSteps,
                generatedAt
        );
    }

    private PaperShadowComparisonScope scope(PaperShadowComparisonQuery query, PaperShadowComparisonFacts facts) {
        if (facts == null) {
            return new PaperShadowComparisonScope(
                    query.strategyId(),
                    query.strategyVersionId(),
                    query.datasetId(),
                    query.evaluationId(),
                    query.publishId(),
                    query.paperRunId(),
                    query.shadowRunId()
            );
        }
        return new PaperShadowComparisonScope(
                firstNonBlank(query.strategyId(), facts.strategyVersion().strategyId(), facts.strategyVersion().strategyCode()),
                firstNonBlank(query.strategyVersionId(), facts.strategyVersion().strategyVersionId()),
                query.datasetId() == null ? facts.dataset().datasetId() : query.datasetId(),
                firstNonBlank(query.evaluationId(), facts.evaluation().evaluationId(), facts.publishTrace().evaluationId(), facts.shadowRun().evaluationId()),
                firstNonBlank(query.publishId(), facts.publishTrace().publishId(), facts.paperRun().publishId(), facts.shadowRun().publishId()),
                firstNonBlank(query.paperRunId(), facts.paperRun().paperRunId()),
                firstNonBlank(query.shadowRunId(), facts.shadowRun().shadowRunId())
        );
    }

    private String paperRunStatus(PaperShadowComparisonFacts facts) {
        if (facts == null || !facts.paperRun().present()) {
            return "NOT_AVAILABLE";
        }
        return facts.paperRun().status();
    }

    private String shadowRunStatus(PaperShadowComparisonFacts facts) {
        if (facts == null) {
            return "NOT_AVAILABLE";
        }
        return facts.shadowRun().effectiveStatus();
    }

    private String evaluationGateStatus(PaperShadowComparisonFacts facts) {
        if (facts == null || !facts.evaluation().present()) {
            return "NOT_AVAILABLE";
        }
        return facts.evaluation().passedGate() ? "PASSED" : "BLOCKED_EVALUATION_GATE";
    }

    private String paperEvidenceStatus(PaperShadowComparisonFacts facts) {
        if (facts == null || !facts.paperRun().present()) {
            return "NOT_AVAILABLE";
        }
        return facts.paperRun().comparableEvidence() ? "SATISFIED" : "FAILED";
    }

    private String shadowEvidenceStatus(PaperShadowComparisonFacts facts) {
        if (facts == null) {
            return "NOT_AVAILABLE";
        }
        ShadowRunFact shadow = facts.shadowRun();
        if (!shadow.runnerImplemented()) {
            return "NOT_IMPLEMENTED";
        }
        if (!shadow.present()) {
            return "NOT_AVAILABLE";
        }
        return shadow.comparableEvidence() ? "SATISFIED" : "FAILED";
    }

    private String dataQualityStatus(PaperShadowComparisonFacts facts) {
        if (facts == null) {
            return "NOT_AVAILABLE";
        }
        return facts.dataset().effectiveQualityStatus();
    }

    private PaperShadowComparisonEvidence satisfied(String code, String message) {
        return new PaperShadowComparisonEvidence(code, "SATISFIED", message);
    }

    private PaperShadowComparisonEvidence missing(String code, String message) {
        return new PaperShadowComparisonEvidence(code, "MISSING", message);
    }

    private PaperShadowComparisonEvidence failed(String code, String message) {
        return new PaperShadowComparisonEvidence(code, "FAILED", message);
    }

    private PaperShadowComparisonEvidence notAvailable(String code, String message) {
        return new PaperShadowComparisonEvidence(code, "NOT_AVAILABLE", message);
    }

    private PaperShadowComparisonEvidence notImplemented(String code, String message) {
        return new PaperShadowComparisonEvidence(code, "NOT_IMPLEMENTED", message);
    }

    private PaperShadowComparisonReason blocker(String code, String message) {
        return new PaperShadowComparisonReason(code, "BLOCKER", message);
    }

    private PaperShadowComparisonReason warning(String code, String message) {
        return new PaperShadowComparisonReason(code, "WARNING", message);
    }

    private boolean equalsId(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
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
