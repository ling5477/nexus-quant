package com.guidinglight.nexusquant.strategy.application.shadowvalidation;

import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata;
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata.Availability;
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadataCalculator;
import com.guidinglight.nexusquant.strategy.application.shadowvalidation.ShadowValidationWorkflowOverviewReadModel.BoundaryMessage;
import com.guidinglight.nexusquant.strategy.application.shadowvalidation.ShadowValidationWorkflowOverviewReadModel.EvidenceAnchor;
import com.guidinglight.nexusquant.strategy.application.shadowvalidation.ShadowValidationWorkflowOverviewReadModel.NextStep;
import com.guidinglight.nexusquant.strategy.application.shadowvalidation.ShadowValidationWorkflowOverviewReadModel.OperatorItem;
import com.guidinglight.nexusquant.strategy.domain.port.ShadowValidationWorkflowOverviewFacts;
import com.guidinglight.nexusquant.strategy.domain.port.ShadowValidationWorkflowOverviewFacts.OperatorEvidenceFact;
import com.guidinglight.nexusquant.strategy.domain.port.ShadowValidationWorkflowOverviewQueryPort;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ShadowValidationWorkflowOverviewQueryService 组装 GateT-1 Shadow Validation Workflow overview。
 *
 * <p>职责：只读读取 GateS 已有本地事实，派生 operator item、state、decision、severity、freshness、
 * blocker、warning、nextStep 和 evidence anchor。该 service 不写库、不创建 review/acknowledge、不启动
 * runner/scheduler、不调用 adapter，不读取 credential，也不修改 account/order/ledger/Paper/Shadow 状态。
 */
@Service
public class ShadowValidationWorkflowOverviewQueryService {

    private static final Duration STALE_AFTER = Duration.ofDays(7);
    private static final int MAX_OPERATOR_ITEMS = 20;
    private static final Pattern SENSITIVE_TEXT_PATTERN = Pattern.compile(
            "api[_-]?key|secret|passphrase|token|private[_ -]?key|credentialMaterial|"
                    + "decrypted[_-]?payload|encrypted[_-]?payload|private endpoint|"
                    + "realOrderId|realAccountBalance|authorizedForTrading|tradingReady|"
                    + "liveReady|tradeApproved|can\\s*trade|ready\\s+to\\s+trade|"
                    + "trade\\s+ready|placeOrder|cancelOrder|withdraw|transfer",
            Pattern.CASE_INSENSITIVE
    );

    private final ShadowValidationWorkflowOverviewQueryPort queryPort;
    private final Clock clock;

    /**
     * 生产构造器。
     */
    @Autowired
    public ShadowValidationWorkflowOverviewQueryService(ShadowValidationWorkflowOverviewQueryPort queryPort) {
        this(queryPort, Clock.systemUTC());
    }

    ShadowValidationWorkflowOverviewQueryService(
            ShadowValidationWorkflowOverviewQueryPort queryPort,
            Clock clock
    ) {
        this.queryPort = Objects.requireNonNull(queryPort, "queryPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 查询 Shadow Validation Workflow overview。
     *
     * <p>事务：read-only。副作用：无。空数据返回 safe overview，计数为 0，并用 warning / nextStep
     * 说明没有 operator item；不会抛 500，也不会伪造收益、胜率、可交易或授权状态。
     *
     * @param traceId 当前请求 trace id
     * @return GateT-1 read model
     */
    @Transactional(readOnly = true)
    public ShadowValidationWorkflowOverviewReadModel overview(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        Instant generatedAt = clock.instant();
        ShadowValidationWorkflowOverviewFacts facts = queryPort.loadOverviewFacts();
        List<OperatorItem> items = facts.operatorEvidence().stream()
                .limit(MAX_OPERATOR_ITEMS)
                .map(fact -> operatorItem(fact, generatedAt, traceId))
                .toList();
        ReadModelEvidenceMetadata evidenceMetadata = evidenceMetadata(facts, items);

        return new ShadowValidationWorkflowOverviewReadModel(
                generatedAt,
                evidenceMetadata,
                true,
                true,
                true,
                true,
                false,
                false,
                false,
                items.size(),
                count(items, ShadowValidationWorkflowState.INTAKE),
                count(items, ShadowValidationWorkflowState.EVIDENCE_REVIEW),
                count(items, ShadowValidationWorkflowState.NEEDS_EVIDENCE),
                count(items, ShadowValidationWorkflowState.READY_FOR_OPERATOR_REVIEW),
                count(items, ShadowValidationWorkflowState.BLOCKED),
                count(items, ShadowValidationWorkflowState.CLOSED_RECOMMENDATION),
                items.isEmpty() ? null : items.getFirst(),
                items,
                overviewBlockers(items),
                overviewWarnings(items),
                overviewNextSteps(items),
                overviewEvidenceAnchors(items, generatedAt, traceId),
                traceId
        );
    }

    private ReadModelEvidenceMetadata evidenceMetadata(
            ShadowValidationWorkflowOverviewFacts facts,
            List<OperatorItem> items
    ) {
        Instant lastCalculatedAt = facts.operatorEvidence().stream()
                .map(OperatorEvidenceFact::evidenceUpdatedAt)
                .max(Instant::compareTo)
                .orElse(null);
        Availability availability = items.isEmpty()
                ? Availability.UNAVAILABLE
                : items.stream().anyMatch(item -> item.evidenceFreshness() == ShadowValidationWorkflowEvidenceFreshness.PARTIAL
                        || item.evidenceFreshness() == ShadowValidationWorkflowEvidenceFreshness.MISSING)
                        ? Availability.PARTIAL
                        : Availability.AVAILABLE;
        return new ReadModelEvidenceMetadataCalculator(clock).calculate(
                "LOCAL_DB_VALIDATION_WORKFLOW",
                availability,
                lastCalculatedAt,
                STALE_AFTER
        );
    }

    private OperatorItem operatorItem(OperatorEvidenceFact fact, Instant generatedAt, String requestTraceId) {
        ShadowValidationWorkflowEvidenceFreshness freshness = freshness(fact, generatedAt);
        ShadowValidationWorkflowValidationDecision decision = validationDecision(fact, freshness);
        ShadowValidationWorkflowState state = workflowState(fact, decision, freshness);
        ShadowValidationWorkflowSeverity severity = severity(fact, decision, state, freshness);
        String itemTraceId = fact.traceId() == null ? requestTraceId : safeText(fact.traceId());
        List<BoundaryMessage> blockers = itemBlockers(fact, decision);
        List<BoundaryMessage> warnings = itemWarnings(fact, freshness, decision);
        List<EvidenceAnchor> anchors = evidenceAnchors(fact, itemTraceId);
        return new OperatorItem(
                operatorItemId(fact),
                safeText(fact.sourceType()),
                safeText(fact.sourceId()),
                safeText(fact.strategyVersionId()),
                fact.datasetId(),
                safeText(fact.evaluationReportId()),
                safeText(fact.paperRunId()),
                fact.shadowRunId(),
                fact.consistencyReportId(),
                safeText(fact.incidentEvidenceId()),
                state,
                decision,
                severity,
                freshness,
                blockers,
                warnings,
                itemNextSteps(state, decision, freshness),
                anchors,
                itemTraceId,
                generatedAt,
                true,
                true,
                true,
                true,
                false,
                false,
                false
        );
    }

    private ShadowValidationWorkflowEvidenceFreshness freshness(OperatorEvidenceFact fact, Instant generatedAt) {
        if (fact.evidenceUpdatedAt() == null) {
            return ShadowValidationWorkflowEvidenceFreshness.MISSING;
        }
        if (fact.evidenceUpdatedAt().isBefore(generatedAt.minus(STALE_AFTER))) {
            return ShadowValidationWorkflowEvidenceFreshness.STALE;
        }
        if (hasPartialStrategyValidationEvidence(fact)) {
            return ShadowValidationWorkflowEvidenceFreshness.PARTIAL;
        }
        return ShadowValidationWorkflowEvidenceFreshness.FRESH;
    }

    private boolean hasPartialStrategyValidationEvidence(OperatorEvidenceFact fact) {
        return "STRATEGY_VALIDATION".equals(fact.sourceType())
                && fact.hasValidationEvidence()
                && (!fact.hasShadowEvidence() || !fact.hasConsistencyEvidence());
    }

    private ShadowValidationWorkflowValidationDecision validationDecision(
            OperatorEvidenceFact fact,
            ShadowValidationWorkflowEvidenceFreshness freshness
    ) {
        if (hasBlocker(fact)) {
            return ShadowValidationWorkflowValidationDecision.BLOCKED;
        }
        if (freshness == ShadowValidationWorkflowEvidenceFreshness.STALE
                || (fact.hasShadowEvidence() && !fact.hasConsistencyEvidence())) {
            return ShadowValidationWorkflowValidationDecision.STALE_EVIDENCE;
        }
        if (evaluationFailed(fact)) {
            return ShadowValidationWorkflowValidationDecision.REJECTED;
        }
        if (readyForValidation(fact, freshness)) {
            return ShadowValidationWorkflowValidationDecision.VALIDATION_READY;
        }
        if (isClosedRecommendation(fact)) {
            return ShadowValidationWorkflowValidationDecision.NO_DECISION;
        }
        if (needsReview(fact) || freshness == ShadowValidationWorkflowEvidenceFreshness.PARTIAL) {
            return ShadowValidationWorkflowValidationDecision.NEEDS_REVIEW;
        }
        return ShadowValidationWorkflowValidationDecision.NO_DECISION;
    }

    private ShadowValidationWorkflowState workflowState(
            OperatorEvidenceFact fact,
            ShadowValidationWorkflowValidationDecision decision,
            ShadowValidationWorkflowEvidenceFreshness freshness
    ) {
        if (decision == ShadowValidationWorkflowValidationDecision.BLOCKED) {
            return ShadowValidationWorkflowState.BLOCKED;
        }
        if (decision == ShadowValidationWorkflowValidationDecision.STALE_EVIDENCE
                || freshness == ShadowValidationWorkflowEvidenceFreshness.MISSING) {
            return ShadowValidationWorkflowState.NEEDS_EVIDENCE;
        }
        if (decision == ShadowValidationWorkflowValidationDecision.VALIDATION_READY) {
            return ShadowValidationWorkflowState.READY_FOR_OPERATOR_REVIEW;
        }
        if (isClosedRecommendation(fact)) {
            return ShadowValidationWorkflowState.CLOSED_RECOMMENDATION;
        }
        if (freshness == ShadowValidationWorkflowEvidenceFreshness.PARTIAL) {
            return ShadowValidationWorkflowState.NEEDS_EVIDENCE;
        }
        if (decision == ShadowValidationWorkflowValidationDecision.NEEDS_REVIEW
                || decision == ShadowValidationWorkflowValidationDecision.REJECTED) {
            return ShadowValidationWorkflowState.EVIDENCE_REVIEW;
        }
        return ShadowValidationWorkflowState.INTAKE;
    }

    private ShadowValidationWorkflowSeverity severity(
            OperatorEvidenceFact fact,
            ShadowValidationWorkflowValidationDecision decision,
            ShadowValidationWorkflowState state,
            ShadowValidationWorkflowEvidenceFreshness freshness
    ) {
        if ("CRITICAL".equals(fact.incidentSeverity())) {
            return ShadowValidationWorkflowSeverity.CRITICAL;
        }
        if (decision == ShadowValidationWorkflowValidationDecision.BLOCKED) {
            return ShadowValidationWorkflowSeverity.HIGH;
        }
        if ("HIGH".equals(fact.incidentSeverity()) || "DIVERGED".equals(fact.consistencyStatus())) {
            return ShadowValidationWorkflowSeverity.HIGH;
        }
        if (decision == ShadowValidationWorkflowValidationDecision.NEEDS_REVIEW
                || decision == ShadowValidationWorkflowValidationDecision.REJECTED
                || freshness == ShadowValidationWorkflowEvidenceFreshness.STALE
                || "PARTIAL".equals(fact.consistencyStatus())
                || "NOT_COMPARABLE".equals(fact.consistencyStatus())) {
            return ShadowValidationWorkflowSeverity.WARNING;
        }
        if (state == ShadowValidationWorkflowState.READY_FOR_OPERATOR_REVIEW
                || state == ShadowValidationWorkflowState.CLOSED_RECOMMENDATION
                || "INFO".equals(fact.incidentSeverity())) {
            return ShadowValidationWorkflowSeverity.INFO;
        }
        if (state == ShadowValidationWorkflowState.INTAKE) {
            return ShadowValidationWorkflowSeverity.NONE;
        }
        return ShadowValidationWorkflowSeverity.UNKNOWN;
    }

    private boolean readyForValidation(
            OperatorEvidenceFact fact,
            ShadowValidationWorkflowEvidenceFreshness freshness
    ) {
        return freshness == ShadowValidationWorkflowEvidenceFreshness.FRESH
                && fact.strategyVersionId() != null
                && "ACTIVE".equals(fact.strategyVersionStatus())
                && fact.evaluationReportId() != null
                && "SUCCEEDED".equals(fact.evaluationStatus())
                && "SUCCEEDED".equals(fact.publishStatus())
                && fact.paperRunId() != null
                && "SIM".equals(fact.paperTradeEnv())
                && ("RUNNING".equals(fact.paperRunStatus()) || "STOPPED".equals(fact.paperRunStatus()))
                && fact.shadowRunId() != null
                && fact.consistencyReportId() != null
                && "CONSISTENT".equals(fact.consistencyStatus());
    }

    private boolean hasBlocker(OperatorEvidenceFact fact) {
        return !"ACTIVE".equals(nullToActive(fact.strategyVersionStatus()))
                || "BLOCKED".equals(fact.shadowRunStatus())
                || "FAILED".equals(fact.shadowRunStatus())
                || "FAILED".equals(fact.consistencyStatus())
                || "CRITICAL".equals(fact.incidentSeverity())
                || "FAILED".equals(fact.incidentStatus());
    }

    private String nullToActive(String status) {
        return status == null ? "ACTIVE" : status;
    }

    private boolean evaluationFailed(OperatorEvidenceFact fact) {
        return "FAILED".equals(fact.evaluationStatus())
                || "FAILURE".equals(fact.evaluationStatus())
                || "ERROR".equals(fact.evaluationStatus());
    }

    private boolean needsReview(OperatorEvidenceFact fact) {
        return "DIVERGED".equals(fact.consistencyStatus())
                || "NOT_COMPARABLE".equals(fact.consistencyStatus())
                || "PARTIAL".equals(fact.consistencyStatus())
                || "HIGH".equals(fact.incidentSeverity())
                || "OPEN".equals(fact.incidentStatus())
                || hasPartialStrategyValidationEvidence(fact);
    }

    private boolean isClosedRecommendation(OperatorEvidenceFact fact) {
        return "INCIDENT_REPLAY".equals(fact.sourceType())
                && ("RESOLVED".equals(fact.incidentStatus()) || "SUCCEEDED".equals(fact.incidentStatus()));
    }

    private List<BoundaryMessage> itemBlockers(
            OperatorEvidenceFact fact,
            ShadowValidationWorkflowValidationDecision decision
    ) {
        List<BoundaryMessage> blockers = new ArrayList<>();
        if (decision != ShadowValidationWorkflowValidationDecision.BLOCKED) {
            return blockers;
        }
        if (!"ACTIVE".equals(nullToActive(fact.strategyVersionStatus()))) {
            blockers.add(message(
                    "STRATEGY_VERSION_NOT_ACTIVE",
                    "HIGH",
                    "Strategy version is not ACTIVE; workflow item must stay blocked.",
                    fact.sourceType(),
                    fact.sourceId()
            ));
        }
        if ("BLOCKED".equals(fact.shadowRunStatus()) || "FAILED".equals(fact.shadowRunStatus())) {
            blockers.add(message(
                    "SHADOW_RUN_BLOCKED",
                    "HIGH",
                    "Shadow Run local fact is BLOCKED or FAILED.",
                    "SHADOW_RUN",
                    uuid(fact.shadowRunId())
            ));
        }
        if ("FAILED".equals(fact.consistencyStatus())) {
            blockers.add(message(
                    "CONSISTENCY_FAILED",
                    "HIGH",
                    "Latest consistency report failed and requires operator review.",
                    "CONSISTENCY_REPORT",
                    uuid(fact.consistencyReportId())
            ));
        }
        if ("CRITICAL".equals(fact.incidentSeverity()) || "FAILED".equals(fact.incidentStatus())) {
            blockers.add(message(
                    "INCIDENT_EVIDENCE_CRITICAL",
                    "CRITICAL",
                    "Incident / replay evidence has critical or failed diagnostic status.",
                    "INCIDENT_REPLAY",
                    fact.incidentEvidenceId()
            ));
        }
        if (blockers.isEmpty()) {
            blockers.add(message(
                    "WORKFLOW_ITEM_BLOCKED",
                    "HIGH",
                    "Workflow item has a blocking local fact.",
                    fact.sourceType(),
                    fact.sourceId()
            ));
        }
        return blockers;
    }

    private List<BoundaryMessage> itemWarnings(
            OperatorEvidenceFact fact,
            ShadowValidationWorkflowEvidenceFreshness freshness,
            ShadowValidationWorkflowValidationDecision decision
    ) {
        List<BoundaryMessage> warnings = new ArrayList<>(boundaryWarnings());
        warnings.add(message(
                "METRICS_NOT_INFERRED",
                "INFO",
                "Read model does not infer profit, win rate, risk clearance or tradable state.",
                "SYSTEM_BOUNDARY",
                null
        ));
        if (freshness == ShadowValidationWorkflowEvidenceFreshness.STALE) {
            warnings.add(message(
                    "STALE_EVIDENCE",
                    "WARNING",
                    "Evidence is older than the GateT-1 freshness window and needs review.",
                    fact.sourceType(),
                    fact.sourceId()
            ));
        }
        if (freshness == ShadowValidationWorkflowEvidenceFreshness.PARTIAL) {
            warnings.add(message(
                    "NEEDS_EVIDENCE",
                    "WARNING",
                    "Validation evidence exists but Shadow or consistency evidence is incomplete.",
                    fact.sourceType(),
                    fact.sourceId()
            ));
        }
        if (decision == ShadowValidationWorkflowValidationDecision.VALIDATION_READY) {
            warnings.add(message(
                    "VALIDATION_READY_IS_REVIEW_ONLY",
                    "WARNING",
                    "VALIDATION_READY only means evidence can enter operator review; it is not trading authorization.",
                    fact.sourceType(),
                    fact.sourceId()
            ));
        }
        return warnings;
    }

    private List<NextStep> itemNextSteps(
            ShadowValidationWorkflowState state,
            ShadowValidationWorkflowValidationDecision decision,
            ShadowValidationWorkflowEvidenceFreshness freshness
    ) {
        List<NextStep> steps = new ArrayList<>();
        steps.add(new NextStep(
                "REVIEW_WORKFLOW_BOUNDARY",
                "operator",
                "Review diagnostic-only, no-side-effect and not-trading-authorization boundary",
                "Boundary remains explicit before any later workflow work",
                true
        ));
        if (state == ShadowValidationWorkflowState.NEEDS_EVIDENCE
                || decision == ShadowValidationWorkflowValidationDecision.STALE_EVIDENCE
                || freshness == ShadowValidationWorkflowEvidenceFreshness.PARTIAL) {
            steps.add(new NextStep(
                    "ADD_OR_REFRESH_EVIDENCE",
                    "operator",
                    "Inspect missing or stale validation, Shadow or consistency evidence",
                    "Required local evidence is present and fresh in a later read-only review",
                    false
            ));
        } else if (state == ShadowValidationWorkflowState.BLOCKED) {
            steps.add(new NextStep(
                    "RESOLVE_DIAGNOSTIC_BLOCKER",
                    "operator",
                    "Resolve or document the blocking local evidence in a separate allowed task",
                    "Blocking evidence is reviewed without triggering runner, scheduler or trade action",
                    false
            ));
        } else if (state == ShadowValidationWorkflowState.READY_FOR_OPERATOR_REVIEW) {
            steps.add(new NextStep(
                    "MANUAL_OPERATOR_REVIEW",
                    "operator",
                    "Manually review validation-ready evidence without approving trading",
                    "Operator has reviewed the evidence and kept trading authorization out of scope",
                    false
            ));
        } else {
            steps.add(new NextStep(
                    "REVIEW_DIAGNOSTIC_ITEM",
                    "operator",
                    "Review local diagnostic evidence and limitations",
                    "Workflow item has an explained next diagnostic action",
                    false
            ));
        }
        return steps;
    }

    private List<EvidenceAnchor> evidenceAnchors(OperatorEvidenceFact fact, String traceId) {
        List<EvidenceAnchor> anchors = new ArrayList<>();
        addAnchor(anchors, "STRATEGY_VERSION", fact.strategyVersionId(), fact.strategyVersionStatus(), fact.evidenceUpdatedAt(), traceId);
        addAnchor(anchors, "DATASET", uuid(fact.datasetId()), null, fact.evidenceUpdatedAt(), traceId);
        addAnchor(anchors, "EVALUATION_REPORT", fact.evaluationReportId(), fact.evaluationStatus(), fact.evidenceUpdatedAt(), traceId);
        addAnchor(anchors, "PUBLISH_RECORD", null, fact.publishStatus(), fact.evidenceUpdatedAt(), traceId);
        addAnchor(anchors, "PAPER_RUN", fact.paperRunId(), fact.paperRunStatus(), fact.evidenceUpdatedAt(), traceId);
        addAnchor(anchors, "SHADOW_RUN", uuid(fact.shadowRunId()), fact.shadowRunStatus(), fact.evidenceUpdatedAt(), traceId);
        addAnchor(anchors, "SHADOW_CONSISTENCY_REPORT", uuid(fact.consistencyReportId()), fact.consistencyStatus(), fact.evidenceUpdatedAt(), traceId);
        addAnchor(anchors, "INCIDENT_REPLAY", fact.incidentEvidenceId(), fact.incidentStatus(), fact.evidenceUpdatedAt(), traceId);
        if (anchors.isEmpty()) {
            anchors.add(new EvidenceAnchor(fact.sourceType(), fact.sourceId(), "NO_ANCHOR", fact.evidenceUpdatedAt(), traceId, "No detailed anchor is available."));
        }
        return anchors;
    }

    private void addAnchor(
            List<EvidenceAnchor> anchors,
            String sourceType,
            String sourceId,
            String sourceVersion,
            Instant sourceTimestamp,
            String traceId
    ) {
        if (sourceId != null && !sourceId.isBlank()) {
            anchors.add(new EvidenceAnchor(sourceType, safeText(sourceId), sourceVersion, sourceTimestamp, traceId, "Local read-only evidence anchor."));
        }
    }

    private List<BoundaryMessage> overviewBlockers(List<OperatorItem> items) {
        return items.stream()
                .flatMap(item -> item.blockers().stream())
                .collect(
                        LinkedHashMap<String, BoundaryMessage>::new,
                        (map, message) -> map.putIfAbsent(message.code() + ":" + message.sourceId(), message),
                        LinkedHashMap::putAll
                )
                .values()
                .stream()
                .toList();
    }

    private List<BoundaryMessage> overviewWarnings(List<OperatorItem> items) {
        List<BoundaryMessage> warnings = new ArrayList<>(boundaryWarnings());
        warnings.add(message(
                "NOT_TRADING_AUTHORIZATION",
                "CRITICAL",
                "Shadow Validation Workflow is derived operator diagnostics only and is not trading authorization.",
                "SYSTEM_BOUNDARY",
                null
        ));
        if (items.isEmpty()) {
            warnings.add(message(
                    "NO_OPERATOR_ITEMS",
                    "INFO",
                    "No operator items were derived from local GateS facts.",
                    "SHADOW_VALIDATION_WORKFLOW",
                    null
            ));
            warnings.add(message(
                    "NO_EVIDENCE",
                    "INFO",
                    "No validation, Shadow, consistency or incident evidence is currently available for this overview.",
                    "LOCAL_FACTS",
                    null
            ));
        }
        warnings.add(message(
                "METRICS_NOT_INFERRED",
                "INFO",
                "Overview does not invent profit, win rate, risk clearance or tradable status.",
                "SYSTEM_BOUNDARY",
                null
        ));
        return warnings;
    }

    private List<BoundaryMessage> boundaryWarnings() {
        return List.of(
                message("LIVE_DISABLED", "CRITICAL", "LIVE is disabled.", "SYSTEM_BOUNDARY", null),
                message("REAL_PROVIDER_NOT_IMPLEMENTED", "CRITICAL", "Real provider is not implemented.", "SYSTEM_BOUNDARY", null),
                message("PRIVATE_TRADING_NOT_IMPLEMENTED", "CRITICAL", "Private trading adapter is not implemented.", "SYSTEM_BOUNDARY", null),
                message("AI_DH_RUNTIME_NOT_INTEGRATED", "CRITICAL", "AI is not started and DH runtime is not integrated.", "SYSTEM_BOUNDARY", null)
        );
    }

    private List<NextStep> overviewNextSteps(List<OperatorItem> items) {
        List<NextStep> steps = new ArrayList<>();
        steps.add(new NextStep(
                "KEEP_GET_ONLY_SELECT_ONLY",
                "backend",
                "Keep workflow overview as GET-only and repository as SELECT-only",
                "No review write endpoint, runner, scheduler, adapter or trading command is added",
                true
        ));
        if (items.isEmpty()) {
            steps.add(new NextStep(
                    "ADD_LOCAL_EVIDENCE",
                    "operator",
                    "Inspect existing GateS views or generate evidence only through separately authorized workflows",
                    "Local read-only evidence exists before operator review is attempted",
                    false
            ));
        } else if (items.stream().anyMatch(item -> item.workflowState() == ShadowValidationWorkflowState.BLOCKED)) {
            steps.add(new NextStep(
                    "REVIEW_BLOCKED_OPERATOR_ITEMS",
                    "operator",
                    "Review blocked operator items and keep trading paths out of scope",
                    "Blocking facts are reviewed without account, order or ledger mutation",
                    false
            ));
        } else {
            steps.add(new NextStep(
                    "REVIEW_DERIVED_OPERATOR_ITEMS",
                    "operator",
                    "Review derived operator items and evidence anchors",
                    "Operator review remains local and diagnostic-only",
                    false
            ));
        }
        return steps;
    }

    private List<EvidenceAnchor> overviewEvidenceAnchors(
            List<OperatorItem> items,
            Instant generatedAt,
            String traceId
    ) {
        if (items.isEmpty()) {
            return List.of(new EvidenceAnchor(
                    "SHADOW_VALIDATION_WORKFLOW",
                    null,
                    "NO_EVIDENCE",
                    generatedAt,
                    traceId,
                    "No local evidence was available."
            ));
        }
        return items.stream()
                .flatMap(item -> item.evidenceAnchors().stream())
                .limit(40)
                .toList();
    }

    private long count(List<OperatorItem> items, ShadowValidationWorkflowState state) {
        return items.stream().filter(item -> item.workflowState() == state).count();
    }

    private String operatorItemId(OperatorEvidenceFact fact) {
        String seed = String.join("|",
                fact.sourceType(),
                fact.sourceId(),
                nullToEmpty(fact.strategyVersionId()),
                uuid(fact.shadowRunId()),
                uuid(fact.consistencyReportId()),
                nullToEmpty(fact.incidentEvidenceId())
        );
        return "op-" + sha256(seed).substring(0, 32);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is required for deterministic operatorItemId", ex);
        }
    }

    private BoundaryMessage message(String code, String severity, String message, String sourceType, String sourceId) {
        return new BoundaryMessage(code, severity, message, sourceType, safeText(sourceId));
    }

    private String safeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return SENSITIVE_TEXT_PATTERN.matcher(normalized).find() ? "[filtered diagnostic text]" : normalized;
    }

    private String uuid(UUID value) {
        return value == null ? null : value.toString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
