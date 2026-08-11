package com.guidinglight.nexusquant.strategy.application.evaluationgate;

import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyValidationOverviewReadModel.BoundaryMessage;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyValidationOverviewReadModel.EvidenceAnchor;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyValidationOverviewReadModel.LatestDecision;
import com.guidinglight.nexusquant.strategy.application.evaluationgate.StrategyValidationOverviewReadModel.NextStep;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyValidationOverviewFacts;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyValidationOverviewFacts.LatestDecisionFact;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyValidationOverviewQueryPort;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * StrategyValidationOverviewQueryService 组装 GateS-3 Strategy Evaluation Gate runtime baseline。
 *
 * <p>职责：只读聚合 strategy/evaluation/publish/Paper/Shadow 本地事实，输出 validation 层面的概览和
 * latest decision。该 service 不创建 evaluation report、不发布 strategy、不创建 Paper/Shadow run、不启动
 * runner/scheduler、不调用 adapter、不读取 credential，也不修改 account/order/ledger 状态。
 */
@Service
public class StrategyValidationOverviewQueryService {

    private final StrategyValidationOverviewQueryPort queryPort;
    private final Clock clock;

    /**
     * 生产构造器。
     *
     * @param queryPort SELECT-only overview query port
     */
    @Autowired
    public StrategyValidationOverviewQueryService(StrategyValidationOverviewQueryPort queryPort) {
        this(queryPort, Clock.systemUTC());
    }

    StrategyValidationOverviewQueryService(StrategyValidationOverviewQueryPort queryPort, Clock clock) {
        this.queryPort = Objects.requireNonNull(queryPort, "queryPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 查询 Strategy Validation overview。
     *
     * <p>事务：read-only。副作用：无。空事实表返回 NO_EVIDENCE 和稳定空概览，不抛 500。APPROVED
     * 只代表 validation 层面证据完整，仍固定 notTradingAuthorization=true。
     *
     * @param traceId 当前请求 trace id，只用于响应追踪
     * @return GateS-3 read model
     */
    @Transactional(readOnly = true)
    public StrategyValidationOverviewReadModel overview(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        Instant generatedAt = clock.instant();
        StrategyValidationOverviewFacts facts = queryPort.loadOverviewFacts();
        LatestDecision latestDecision = facts.latestDecision()
                .map(fact -> latestDecision(fact, traceId))
                .orElseGet(() -> noEvidenceDecision(generatedAt, traceId));

        return new StrategyValidationOverviewReadModel(
                generatedAt,
                true,
                true,
                true,
                true,
                false,
                false,
                false,
                facts.totalStrategyVersions(),
                facts.evaluatedStrategyVersions(),
                facts.approvedForValidation(),
                facts.rejectedForValidation(),
                facts.needsReview(),
                facts.blocked(),
                latestDecision,
                blockers(latestDecision),
                warnings(latestDecision),
                nextSteps(latestDecision),
                evidenceAnchors(facts, latestDecision),
                traceId
        );
    }

    private LatestDecision noEvidenceDecision(Instant generatedAt, String traceId) {
        return new LatestDecision(
                null,
                null,
                null,
                null,
                null,
                null,
                StrategyValidationDecision.NO_EVIDENCE,
                List.of("No strategy validation evidence exists in local read model facts."),
                List.of("No evaluation, publish, Paper or Shadow evidence was available; readiness must not be inferred."),
                generatedAt,
                traceId
        );
    }

    private LatestDecision latestDecision(LatestDecisionFact fact, String traceId) {
        StrategyValidationDecision decision = evaluateDecision(fact);
        return new LatestDecision(
                fact.strategyVersionId(),
                fact.datasetId(),
                fact.evaluationReportId(),
                fact.publishId(),
                fact.paperRunId(),
                fact.shadowRunId(),
                decision,
                decisionReasons(fact, decision),
                limitations(fact, decision),
                fact.generatedAt(),
                traceId
        );
    }

    /**
     * 复用 canonical validation 规则评估一条服务端已读取的 immutable fact。
     *
     * <p>该方法不读取 repository、不写库，也不产生交易授权；供按 publish anchor 的只读编排复用，
     * 避免 Release admission 再维护第二套 validation taxonomy 或判断分支。
     *
     * @param fact 同一 publish/release anchor 的 validation fact；null 按 NO_EVIDENCE fail-closed
     * @return 既有 {@link StrategyValidationDecision}，其中 APPROVED 仍只表示 validation 层面通过
     */
    public StrategyValidationDecision evaluateDecision(LatestDecisionFact fact) {
        if (fact == null || !fact.hasEvaluationReport()) {
            return StrategyValidationDecision.NO_EVIDENCE;
        }
        if (!"ACTIVE".equals(fact.strategyVersionStatus())) {
            return StrategyValidationDecision.BLOCKED;
        }
        if (fact.evaluationFailed()) {
            return StrategyValidationDecision.REJECTED;
        }
        if (!fact.evaluationSucceeded()) {
            return StrategyValidationDecision.NEEDS_REVIEW;
        }
        if (!fact.publishSucceeded() || !fact.paperEvidenceSufficient()) {
            return StrategyValidationDecision.NEEDS_REVIEW;
        }
        if ("BLOCKED".equals(fact.shadowRunStatus()) || "FAILED".equals(fact.shadowRunStatus())) {
            return StrategyValidationDecision.BLOCKED;
        }
        if (fact.shadowRunId() != null && fact.consistencyStatus() == null) {
            return StrategyValidationDecision.STALE_EVIDENCE;
        }
        if ("FAILED".equals(fact.consistencyStatus())) {
            return StrategyValidationDecision.BLOCKED;
        }
        if ("DIVERGED".equals(fact.consistencyStatus())
                || "NOT_COMPARABLE".equals(fact.consistencyStatus())
                || "PARTIAL".equals(fact.consistencyStatus())) {
            return StrategyValidationDecision.NEEDS_REVIEW;
        }
        return StrategyValidationDecision.APPROVED;
    }

    private List<String> decisionReasons(LatestDecisionFact fact, StrategyValidationDecision decision) {
        return switch (decision) {
            case NO_EVIDENCE -> List.of("Evaluation report evidence is missing.");
            case REJECTED -> List.of("Evaluation report failed or reports an error state.");
            case BLOCKED -> blockedReasons(fact);
            case STALE_EVIDENCE -> List.of("Shadow evidence exists but latest consistency report is missing.");
            case NEEDS_REVIEW -> needsReviewReasons(fact);
            case APPROVED -> List.of("Validation evidence is complete enough for Paper / Shadow observation review.");
        };
    }

    private List<String> blockedReasons(LatestDecisionFact fact) {
        List<String> reasons = new ArrayList<>();
        if (!"ACTIVE".equals(fact.strategyVersionStatus())) {
            reasons.add("Strategy version is not ACTIVE.");
        }
        if ("BLOCKED".equals(fact.shadowRunStatus()) || "FAILED".equals(fact.shadowRunStatus())) {
            reasons.add("Latest Shadow Run is BLOCKED or FAILED.");
        }
        if ("FAILED".equals(fact.consistencyStatus())) {
            reasons.add("Latest consistency report failed.");
        }
        return reasons.isEmpty() ? List.of("Validation evidence has a blocking status.") : reasons;
    }

    private List<String> needsReviewReasons(LatestDecisionFact fact) {
        List<String> reasons = new ArrayList<>();
        if (!fact.evaluationSucceeded()) {
            reasons.add("Evaluation report is not SUCCEEDED.");
        }
        if (!fact.publishSucceeded()) {
            reasons.add("Successful publish trace is missing.");
        }
        if (!fact.paperEvidenceSufficient()) {
            reasons.add("Sufficient SIM Paper evidence is missing.");
        }
        if ("DIVERGED".equals(fact.consistencyStatus())
                || "NOT_COMPARABLE".equals(fact.consistencyStatus())
                || "PARTIAL".equals(fact.consistencyStatus())) {
            reasons.add("Latest Paper / Shadow consistency status requires review.");
        }
        return reasons.isEmpty() ? List.of("Validation evidence is incomplete and requires review.") : reasons;
    }

    private List<String> limitations(LatestDecisionFact fact, StrategyValidationDecision decision) {
        List<String> limitations = new ArrayList<>();
        limitations.add("Decision is validation-layer only and is not trading authorization.");
        limitations.add("LIVE, real provider, private trading adapter and AI/DH runtime are not enabled.");
        if (decision != StrategyValidationDecision.APPROVED) {
            limitations.add("The read model does not infer profitability, win rate or live execution readiness.");
        }
        if (fact.datasetId() == null) {
            limitations.add("Dataset anchor is missing; data-quality freshness cannot be inferred.");
        }
        return limitations;
    }

    private List<BoundaryMessage> blockers(LatestDecision latestDecision) {
        if (latestDecision.decision() == StrategyValidationDecision.BLOCKED) {
            return List.of(message(
                    "VALIDATION_EVIDENCE_BLOCKED",
                    "CRITICAL",
                    "Validation evidence contains a blocking status.",
                    "STRATEGY_VALIDATION",
                    latestDecision.strategyVersionId()
            ));
        }
        return List.of();
    }

    private List<BoundaryMessage> warnings(LatestDecision latestDecision) {
        List<BoundaryMessage> warnings = new ArrayList<>();
        warnings.add(message(
                "LIVE_DISABLED",
                "CRITICAL",
                "LIVE is disabled; validation overview is diagnostic only.",
                "SYSTEM_BOUNDARY",
                null
        ));
        warnings.add(message(
                "REAL_PROVIDER_NOT_IMPLEMENTED",
                "CRITICAL",
                "Real provider and RealClient are not implemented.",
                "SYSTEM_BOUNDARY",
                null
        ));
        warnings.add(message(
                "PRIVATE_TRADING_NOT_IMPLEMENTED",
                "CRITICAL",
                "Private trading adapter is not implemented.",
                "SYSTEM_BOUNDARY",
                null
        ));
        warnings.add(message(
                "VALIDATION_IS_NOT_TRADING_AUTHORIZATION",
                "CRITICAL",
                "APPROVED means validation evidence only; it is not trading authorization.",
                "SYSTEM_BOUNDARY",
                latestDecision.strategyVersionId()
        ));
        if (latestDecision.decision() == StrategyValidationDecision.NO_EVIDENCE) {
            warnings.add(message(
                    "VALIDATION_EVIDENCE_MISSING",
                    "WARNING",
                    "No evaluation evidence is available; do not infer readiness.",
                    "STRATEGY_VALIDATION",
                    latestDecision.strategyVersionId()
            ));
        }
        if (latestDecision.decision() == StrategyValidationDecision.STALE_EVIDENCE) {
            warnings.add(message(
                    "STALE_EVIDENCE",
                    "WARNING",
                    "Shadow evidence exists but consistency evidence is incomplete.",
                    "STRATEGY_VALIDATION",
                    latestDecision.strategyVersionId()
            ));
        }
        return warnings;
    }

    private List<NextStep> nextSteps(LatestDecision latestDecision) {
        List<NextStep> steps = new ArrayList<>();
        steps.add(new NextStep(
                "REVIEW_VALIDATION_BOUNDARY",
                "backend",
                "Review diagnostic-only and not-trading-authorization boundary",
                "Boundary warnings are acknowledged before any future GateS action",
                true
        ));
        switch (latestDecision.decision()) {
            case APPROVED -> steps.add(new NextStep(
                    "REVIEW_APPROVED_VALIDATION_EVIDENCE",
                    "backend",
                    "Review validation evidence before any separate Paper / Shadow observation work",
                    "Strategy validation evidence is reviewed without starting runner or trading actions",
                    false
            ));
            case NO_EVIDENCE -> steps.add(new NextStep(
                    "ADD_OR_SELECT_EVALUATION_EVIDENCE",
                    "backend",
                    "Create or select evaluation evidence in a separate allowed workflow",
                    "Evaluation report evidence exists in local facts",
                    false
            ));
            case NEEDS_REVIEW, STALE_EVIDENCE -> steps.add(new NextStep(
                    "REVIEW_INCOMPLETE_VALIDATION_EVIDENCE",
                    "backend",
                    "Review incomplete validation evidence and limitations",
                    "Missing publish, Paper or consistency evidence is explained",
                    false
            ));
            case REJECTED, BLOCKED -> steps.add(new NextStep(
                    "RESOLVE_VALIDATION_BLOCKERS",
                    "backend",
                    "Resolve validation blockers in a separate task before further observation",
                    "Blocking or rejected evidence is remediated and retested",
                    false
            ));
        }
        return steps;
    }

    private List<EvidenceAnchor> evidenceAnchors(
            StrategyValidationOverviewFacts facts,
            LatestDecision latestDecision
    ) {
        List<EvidenceAnchor> anchors = new ArrayList<>();
        addAnchor(anchors, "STRATEGY_VERSION", latestDecision.strategyVersionId(), null, latestDecision.generatedAt(), null);
        addAnchor(anchors, "DATASET", latestDecision.datasetId() == null ? null : latestDecision.datasetId().toString(), null, null, null);
        addAnchor(anchors, "EVALUATION_REPORT", latestDecision.evaluationReportId(), latestDecision.decision().name(), latestDecision.generatedAt(), null);
        addAnchor(anchors, "PUBLISH_RECORD", latestDecision.publishId(), null, null, null);
        addAnchor(anchors, "PAPER_RUN", latestDecision.paperRunId(), null, null, null);
        addAnchor(anchors, "SHADOW_RUN", latestDecision.shadowRunId() == null ? null : latestDecision.shadowRunId().toString(), null, null, null);
        if (anchors.isEmpty() && facts.totalStrategyVersions() == 0) {
            anchors.add(new EvidenceAnchor("STRATEGY_VALIDATION", null, "NO_EVIDENCE", latestDecision.generatedAt(), null));
        }
        return anchors;
    }

    private void addAnchor(
            List<EvidenceAnchor> anchors,
            String sourceType,
            String sourceId,
            String sourceVersion,
            Instant sourceTimestamp,
            String checksum
    ) {
        if (sourceId != null && !sourceId.isBlank()) {
            anchors.add(new EvidenceAnchor(sourceType, sourceId, sourceVersion, sourceTimestamp, checksum));
        }
    }

    private BoundaryMessage message(String code, String severity, String message, String sourceType, String sourceId) {
        return new BoundaryMessage(code, severity, message, sourceType, sourceId);
    }
}
