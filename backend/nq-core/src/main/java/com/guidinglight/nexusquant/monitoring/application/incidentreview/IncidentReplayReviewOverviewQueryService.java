package com.guidinglight.nexusquant.monitoring.application.incidentreview;

import com.guidinglight.nexusquant.monitoring.application.incidentreview.IncidentReplayReviewOverviewReadModel.BoundaryMessage;
import com.guidinglight.nexusquant.monitoring.application.incidentreview.IncidentReplayReviewOverviewReadModel.EvidenceAnchor;
import com.guidinglight.nexusquant.monitoring.application.incidentreview.IncidentReplayReviewOverviewReadModel.IncidentReplayReviewItem;
import com.guidinglight.nexusquant.monitoring.application.incidentreview.IncidentReplayReviewOverviewReadModel.NextStep;
import com.guidinglight.nexusquant.monitoring.domain.port.IncidentReplayReviewOverviewFacts;
import com.guidinglight.nexusquant.monitoring.domain.port.IncidentReplayReviewOverviewFacts.ReviewEvidenceFact;
import com.guidinglight.nexusquant.monitoring.domain.port.IncidentReplayReviewOverviewQueryPort;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * IncidentReplayReviewOverviewQueryService 组装 GateT-3 Incident / Replay Review overview。
 *
 * <p>职责：只读读取本地 incident / replay diagnostics，并派生 deterministic review item、
 * reviewState、reviewDecision、severity、freshness、blocker、warning、nextStep 和 evidence anchor。
 * 该 service 不写库、不创建 incident/alert/replay/review、不启动 runner/scheduler、不调用 adapter，
 * 不读取 credential，也不修改 account/order/ledger/Paper/Shadow 状态。
 */
@Service
public class IncidentReplayReviewOverviewQueryService {

    private static final Duration STALE_AFTER = Duration.ofDays(7);
    private static final int MAX_REVIEW_ITEMS = 50;
    private static final int MAX_ANCHORS = 50;
    private static final Pattern SENSITIVE_TEXT_PATTERN = Pattern.compile(
            "api[_-]?key|secret|passphrase|token|private[_ -]?key|credentialMaterial|"
                    + "decrypted[_-]?payload|encrypted[_-]?payload|rawSignature|rawPrivate|"
                    + "private endpoint|realOrderId|realAccountBalance|authorizedForTrading|"
                    + "tradingReady|liveReady|tradeApproved|can\\s*trade|can[_ -]?trade|"
                    + "ready\\s+to\\s+trade|ready[_ -]?to[_ -]?trade|trade\\s+ready|"
                    + "trade[_ -]?ready|placeOrder|cancelOrder|withdraw|transfer",
            Pattern.CASE_INSENSITIVE
    );

    private final IncidentReplayReviewOverviewQueryPort queryPort;
    private final Clock clock;

    /**
     * 生产构造器。
     *
     * @param queryPort SELECT-only incident replay review query port
     */
    @Autowired
    public IncidentReplayReviewOverviewQueryService(IncidentReplayReviewOverviewQueryPort queryPort) {
        this(queryPort, Clock.systemUTC());
    }

    IncidentReplayReviewOverviewQueryService(
            IncidentReplayReviewOverviewQueryPort queryPort,
            Clock clock
    ) {
        this.queryPort = Objects.requireNonNull(queryPort, "queryPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 查询 Incident / Replay Review overview。
     *
     * <p>事务：read-only。副作用：无。空数据返回 safe overview，计数为 0，并用 warning / nextStep
     * 说明没有 review evidence；不会抛 500，不会自动创建 incident / alert / replay / review，也不会把
     * acknowledge / escalation / closeout recommendation 写成已执行动作。
     *
     * @param traceId 当前请求 trace id
     * @return GateT-3 incident replay review overview read model
     */
    @Transactional(readOnly = true)
    public IncidentReplayReviewOverviewReadModel overview(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        Instant generatedAt = clock.instant();
        IncidentReplayReviewOverviewFacts facts = queryPort.loadOverviewFacts();
        List<IncidentReplayReviewItem> items = facts.evidence().stream()
                .limit(MAX_REVIEW_ITEMS)
                .map(fact -> reviewItem(fact, generatedAt, traceId))
                .toList();

        return new IncidentReplayReviewOverviewReadModel(
                generatedAt,
                true,
                true,
                true,
                true,
                false,
                false,
                false,
                items.size(),
                count(items, IncidentReplayReviewState.INTAKE),
                count(items, IncidentReplayReviewState.EVIDENCE_REVIEW),
                count(items, IncidentReplayReviewState.NEEDS_OPERATOR_REVIEW),
                count(items, IncidentReplayReviewState.ACKNOWLEDGED_RECOMMENDATION),
                count(items, IncidentReplayReviewState.ESCALATED_RECOMMENDATION),
                count(items, IncidentReplayReviewState.CLOSED_RECOMMENDATION),
                count(items, IncidentReplayReviewState.BLOCKED),
                items.isEmpty() ? null : items.getFirst(),
                items,
                severityBuckets(items),
                freshnessSummary(items),
                overviewBlockers(),
                overviewWarnings(items),
                overviewNextSteps(items),
                overviewEvidenceAnchors(items, generatedAt, traceId),
                traceId
        );
    }

    private IncidentReplayReviewItem reviewItem(
            ReviewEvidenceFact fact,
            Instant generatedAt,
            String requestTraceId
    ) {
        IncidentReplayReviewFreshness freshness = freshness(fact, generatedAt);
        IncidentReplayReviewSeverity severity = severity(fact);
        IncidentReplayReviewState state = reviewState(fact, severity, freshness);
        IncidentReplayReviewDecision decision = reviewDecision(fact, state, severity, freshness);
        String itemTraceId = safeText(fact.traceId()) == null ? requestTraceId : safeText(fact.traceId());
        String reviewItemId = reviewItemId(fact);
        String operatorItemId = operatorItemId(fact);

        return new IncidentReplayReviewItem(
                reviewItemId,
                safeText(fact.sourceType()),
                safeText(fact.sourceId()),
                safeText(fact.incidentEvidenceId()),
                safeText(fact.replayRecordId()),
                safeText(fact.shadowRunId()),
                safeText(fact.paperRunId()),
                safeText(fact.consistencyReportId()),
                operatorItemId,
                state,
                decision,
                severity,
                freshness,
                safeText(fact.summary()),
                itemLimitations(fact, decision, freshness),
                itemBlockers(fact, state, decision),
                itemWarnings(fact, state, decision, severity),
                itemNextSteps(fact, state, decision, severity),
                itemEvidenceAnchors(fact, itemTraceId, operatorItemId),
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

    private IncidentReplayReviewFreshness freshness(ReviewEvidenceFact fact, Instant generatedAt) {
        if (fact.occurredAt() == null || fact.sourceId() == null) {
            return IncidentReplayReviewFreshness.MISSING;
        }
        if (fact.occurredAt().isBefore(generatedAt.minus(STALE_AFTER))) {
            return IncidentReplayReviewFreshness.STALE;
        }
        return IncidentReplayReviewFreshness.FRESH;
    }

    private IncidentReplayReviewSeverity severity(ReviewEvidenceFact fact) {
        String sourceType = value(fact.sourceType());
        String status = value(fact.sourceStatus());
        String sourceSeverity = value(fact.sourceSeverity());
        if ("CRITICAL".equals(sourceSeverity) || "FAILED".equals(status)) {
            return IncidentReplayReviewSeverity.CRITICAL;
        }
        if ("HIGH".equals(sourceSeverity)
                || "DIVERGED".equals(status)
                || "ILLEGAL_STATE_TRANSITION_ATTEMPT".equals(status)) {
            return IncidentReplayReviewSeverity.HIGH;
        }
        if ("MEDIUM".equals(sourceSeverity)
                || "PARTIAL".equals(status)
                || "NOT_COMPARABLE".equals(status)
                || "SKIPPED".equals(status)
                || "OPEN".equals(status)) {
            return IncidentReplayReviewSeverity.WARNING;
        }
        if ("LOW".equals(sourceSeverity)
                || "ACKED".equals(status)
                || "RESOLVED".equals(status)
                || "SUCCEEDED".equals(status)
                || "TRADE_REPLAY".equals(sourceType)
                || "SHADOW_EVENT".equals(sourceType)) {
            return IncidentReplayReviewSeverity.INFO;
        }
        return IncidentReplayReviewSeverity.UNKNOWN;
    }

    private IncidentReplayReviewState reviewState(
            ReviewEvidenceFact fact,
            IncidentReplayReviewSeverity severity,
            IncidentReplayReviewFreshness freshness
    ) {
        String sourceType = value(fact.sourceType());
        String status = value(fact.sourceStatus());
        if (freshness == IncidentReplayReviewFreshness.STALE
                || freshness == IncidentReplayReviewFreshness.MISSING
                || "FAILED".equals(status)) {
            return IncidentReplayReviewState.BLOCKED;
        }
        if ("PAPER_ALERT".equals(sourceType) && "ACKED".equals(status)) {
            return IncidentReplayReviewState.ACKNOWLEDGED_RECOMMENDATION;
        }
        if (("PAPER_ALERT".equals(sourceType) && "RESOLVED".equals(status))
                || ("RECOVERY_EVENT".equals(sourceType) && "SUCCEEDED".equals(status))) {
            return IncidentReplayReviewState.CLOSED_RECOMMENDATION;
        }
        if (severity == IncidentReplayReviewSeverity.CRITICAL || severity == IncidentReplayReviewSeverity.HIGH) {
            return IncidentReplayReviewState.NEEDS_OPERATOR_REVIEW;
        }
        if ("CONSISTENCY_DIVERGENCE".equals(sourceType) || "TRADE_REPLAY".equals(sourceType)) {
            return IncidentReplayReviewState.EVIDENCE_REVIEW;
        }
        return IncidentReplayReviewState.INTAKE;
    }

    private IncidentReplayReviewDecision reviewDecision(
            ReviewEvidenceFact fact,
            IncidentReplayReviewState state,
            IncidentReplayReviewSeverity severity,
            IncidentReplayReviewFreshness freshness
    ) {
        String sourceType = value(fact.sourceType());
        String status = value(fact.sourceStatus());
        if (freshness == IncidentReplayReviewFreshness.STALE) {
            return IncidentReplayReviewDecision.STALE_EVIDENCE;
        }
        if (state == IncidentReplayReviewState.BLOCKED) {
            return IncidentReplayReviewDecision.BLOCKED;
        }
        if ("PAPER_ALERT".equals(sourceType) && "ACKED".equals(status)) {
            return IncidentReplayReviewDecision.ACKNOWLEDGE_RECOMMENDED;
        }
        if (state == IncidentReplayReviewState.CLOSED_RECOMMENDATION) {
            return IncidentReplayReviewDecision.CLOSEOUT_RECOMMENDED;
        }
        if (severity == IncidentReplayReviewSeverity.CRITICAL || severity == IncidentReplayReviewSeverity.HIGH) {
            return IncidentReplayReviewDecision.ESCALATE_RECOMMENDED;
        }
        if (state == IncidentReplayReviewState.EVIDENCE_REVIEW || state == IncidentReplayReviewState.INTAKE) {
            return IncidentReplayReviewDecision.REVIEW_NEEDED;
        }
        return IncidentReplayReviewDecision.NO_DECISION;
    }

    private List<String> itemLimitations(
            ReviewEvidenceFact fact,
            IncidentReplayReviewDecision decision,
            IncidentReplayReviewFreshness freshness
    ) {
        List<String> limitations = new ArrayList<>();
        limitations.add("DERIVED_REVIEW_ITEM_NOT_PERSISTED");
        limitations.add("REVIEW_RECOMMENDATION_ONLY");
        if (freshness == IncidentReplayReviewFreshness.STALE) {
            limitations.add("STALE_EVIDENCE");
        }
        if (freshness == IncidentReplayReviewFreshness.MISSING) {
            limitations.add("MISSING_EVIDENCE_TIMESTAMP_OR_SOURCE");
        }
        if (fact.traceId() == null) {
            limitations.add("TRACE_ID_MISSING_FROM_SOURCE");
        }
        if (decision == IncidentReplayReviewDecision.ACKNOWLEDGE_RECOMMENDED) {
            limitations.add("SOURCE_ACKED_IS_NOT_GATET3_ACK");
        }
        if (decision == IncidentReplayReviewDecision.CLOSEOUT_RECOMMENDED) {
            limitations.add("CLOSEOUT_RECOMMENDATION_IS_NOT_INCIDENT_CLOSED");
        }
        return limitations.stream().distinct().toList();
    }

    private List<BoundaryMessage> itemBlockers(
            ReviewEvidenceFact fact,
            IncidentReplayReviewState state,
            IncidentReplayReviewDecision decision
    ) {
        if (state != IncidentReplayReviewState.BLOCKED) {
            return List.of();
        }
        String code = decision == IncidentReplayReviewDecision.STALE_EVIDENCE ? "STALE_EVIDENCE" : "REVIEW_ITEM_BLOCKED";
        return List.of(message(
                code,
                "WARNING",
                "Review item is fail-closed; no incident, alert, replay, review or trading state is created.",
                fact.sourceType(),
                fact.sourceId()
        ));
    }

    private List<BoundaryMessage> itemWarnings(
            ReviewEvidenceFact fact,
            IncidentReplayReviewState state,
            IncidentReplayReviewDecision decision,
            IncidentReplayReviewSeverity severity
    ) {
        List<BoundaryMessage> warnings = new ArrayList<>();
        warnings.add(message(
                "DIAGNOSTIC_REVIEW_ONLY",
                "WARNING",
                "Incident replay review item is diagnostic only and not trading authorization.",
                fact.sourceType(),
                fact.sourceId()
        ));
        if (decision == IncidentReplayReviewDecision.ACKNOWLEDGE_RECOMMENDED) {
            warnings.add(message(
                    "ACKNOWLEDGE_RECOMMENDED_ONLY",
                    "WARNING",
                    "ACKNOWLEDGE_RECOMMENDED only suggests human confirmation; no acknowledge write-side action is executed.",
                    fact.sourceType(),
                    fact.sourceId()
            ));
        }
        if (decision == IncidentReplayReviewDecision.ESCALATE_RECOMMENDED) {
            warnings.add(message(
                    "ESCALATE_RECOMMENDED_ONLY",
                    "WARNING",
                    "ESCALATE_RECOMMENDED only suggests human escalation; no escalation action is executed.",
                    fact.sourceType(),
                    fact.sourceId()
            ));
        }
        if (state == IncidentReplayReviewState.CLOSED_RECOMMENDATION) {
            warnings.add(message(
                    "CLOSED_RECOMMENDATION_ONLY",
                    "WARNING",
                    "CLOSED_RECOMMENDATION is diagnostic closeout advice, not a real incident closure.",
                    fact.sourceType(),
                    fact.sourceId()
            ));
        }
        if (severity == IncidentReplayReviewSeverity.HIGH || severity == IncidentReplayReviewSeverity.CRITICAL) {
            warnings.add(message(
                    "HIGH_CRITICAL_ARE_PRIORITY_ONLY",
                    "WARNING",
                    "HIGH and CRITICAL express diagnostic review priority only and do not mean risk is remediated.",
                    fact.sourceType(),
                    fact.sourceId()
            ));
        }
        return warnings;
    }

    private List<NextStep> itemNextSteps(
            ReviewEvidenceFact fact,
            IncidentReplayReviewState state,
            IncidentReplayReviewDecision decision,
            IncidentReplayReviewSeverity severity
    ) {
        List<NextStep> steps = new ArrayList<>();
        if (state == IncidentReplayReviewState.BLOCKED) {
            steps.add(step(
                    "KEEP_REVIEW_ITEM_BLOCKED",
                    "operator",
                    "Keep this review item blocked until local evidence is fresh and sufficient",
                    "No review, incident, alert, replay, account, order or ledger state is mutated",
                    false
            ));
        } else if (decision == IncidentReplayReviewDecision.ACKNOWLEDGE_RECOMMENDED) {
            steps.add(step(
                    "CONFIRM_DIAGNOSTIC_FACT_MANUALLY",
                    "operator",
                    "Manually confirm the known diagnostic fact in a separately authorized workflow",
                    "No acknowledge record is created by this read model",
                    false
            ));
        } else if (decision == IncidentReplayReviewDecision.CLOSEOUT_RECOMMENDED) {
            steps.add(step(
                    "REVIEW_CLOSEOUT_RECOMMENDATION",
                    "operator",
                    "Review whether the evidence forms a diagnostic closeout recommendation",
                    "No incident is marked closed by this endpoint",
                    false
            ));
        } else if (severity == IncidentReplayReviewSeverity.HIGH || severity == IncidentReplayReviewSeverity.CRITICAL) {
            steps.add(step(
                    "ESCALATE_MANUAL_REVIEW",
                    "operator",
                    "Escalate this diagnostic evidence for manual review only",
                    "Escalation is not executed automatically and no external system is notified",
                    false
            ));
        } else {
            steps.add(step(
                    "REVIEW_LOCAL_EVIDENCE",
                    "operator",
                    "Review local incident / replay evidence and anchors",
                    "Evidence remains read-only and diagnostic",
                    false
            ));
        }
        steps.add(step(
                "KEEP_NO_SIDE_EFFECT_BOUNDARY",
                "backend",
                "Keep review item handling read-only and derived",
                "No POST/PUT/PATCH/DELETE endpoint or write-side action is added",
                true
        ));
        return steps;
    }

    private List<EvidenceAnchor> itemEvidenceAnchors(
            ReviewEvidenceFact fact,
            String traceId,
            String operatorItemId
    ) {
        List<EvidenceAnchor> anchors = new ArrayList<>();
        addAnchor(anchors, fact.sourceType(), fact.sourceId(), fact.sourceStatus(), fact.occurredAt(), traceId,
                "Local incident replay source fact.");
        addAnchor(anchors, "INCIDENT_EVIDENCE", fact.incidentEvidenceId(), fact.sourceType(), fact.occurredAt(), traceId,
                "Derived incident evidence anchor.");
        addAnchor(anchors, "TRADE_REPLAY", fact.replayRecordId(), fact.sourceStatus(), fact.occurredAt(), traceId,
                "Local trade replay record anchor.");
        addAnchor(anchors, "SHADOW_RUN", fact.shadowRunId(), null, fact.occurredAt(), traceId,
                "Local Shadow Run anchor.");
        addAnchor(anchors, "PAPER_RUN", fact.paperRunId(), null, fact.occurredAt(), traceId,
                "Local Paper Run anchor.");
        addAnchor(anchors, "SHADOW_CONSISTENCY_REPORT", fact.consistencyReportId(), fact.sourceStatus(), fact.occurredAt(), traceId,
                "Local consistency report anchor.");
        addAnchor(anchors, "OPERATOR_ITEM", operatorItemId, "DERIVED_FROM_GATET1_RULE", fact.occurredAt(), traceId,
                "Derived GateT-1 operator item anchor; no persisted operator state is read or written.");
        if ("CONSISTENCY_DIVERGENCE".equals(value(fact.sourceType())) && fact.consistencyReportId() != null) {
            addAnchor(anchors, "CONSISTENCY_EVIDENCE", consistencyEvidenceItemId(fact), "DERIVED_FROM_GATET2_RULE",
                    fact.occurredAt(), traceId,
                    "Derived GateT-2 consistency evidence anchor; raw metricDelta is not copied.");
        }
        return anchors;
    }

    private void addAnchor(
            List<EvidenceAnchor> anchors,
            String sourceType,
            String sourceId,
            String sourceVersion,
            Instant sourceTimestamp,
            String traceId,
            String description
    ) {
        String safeSourceId = safeText(sourceId);
        if (safeSourceId != null) {
            anchors.add(new EvidenceAnchor(sourceType, safeSourceId, safeText(sourceVersion), sourceTimestamp, traceId, description));
        }
    }

    private List<BoundaryMessage> overviewBlockers() {
        return List.of(
                message("LIVE_DISABLED", "CRITICAL", "LIVE is disabled; incident replay review overview is diagnostic only.", "SYSTEM_BOUNDARY", null),
                message("REAL_PROVIDER_NOT_IMPLEMENTED", "CRITICAL", "Real provider is not implemented.", "SYSTEM_BOUNDARY", null),
                message("PRIVATE_TRADING_NOT_IMPLEMENTED", "CRITICAL", "Private trading adapter is not implemented.", "SYSTEM_BOUNDARY", null),
                message("NOT_TRADING_AUTHORIZATION", "CRITICAL", "Incident replay review overview is not trading authorization.", "SYSTEM_BOUNDARY", null)
        );
    }

    private List<BoundaryMessage> overviewWarnings(List<IncidentReplayReviewItem> items) {
        List<BoundaryMessage> warnings = new ArrayList<>();
        warnings.add(message("INCIDENT_REPLAY_REVIEW_DIAGNOSTIC_ONLY", "WARNING", "Review workflow is a read-only derived model and does not execute remediation.", "SYSTEM_BOUNDARY", null));
        warnings.add(message("AI_DH_RUNTIME_NOT_INTEGRATED", "CRITICAL", "AI is not started and DH runtime is not integrated.", "SYSTEM_BOUNDARY", null));
        warnings.add(message("REVIEW_RECOMMENDATIONS_NO_SIDE_EFFECT", "WARNING", "Acknowledge, escalate and closeout recommendations are not write-side actions.", "SYSTEM_BOUNDARY", null));
        if (items.isEmpty()) {
            warnings.add(message("NO_REVIEW_EVIDENCE", "WARNING", "No local incident / replay review evidence is currently available.", "LOCAL_FACTS", null));
        }
        if (items.stream().anyMatch(item -> item.evidenceFreshness() == IncidentReplayReviewFreshness.STALE)) {
            warnings.add(message("STALE_EVIDENCE", "WARNING", "At least one review item is based on stale evidence.", "LOCAL_FACTS", null));
        }
        if (items.stream().anyMatch(item -> item.severity() == IncidentReplayReviewSeverity.HIGH
                || item.severity() == IncidentReplayReviewSeverity.CRITICAL)) {
            warnings.add(message("HIGH_CRITICAL_ARE_PRIORITY_ONLY", "WARNING", "HIGH and CRITICAL only express diagnostic priority and do not authorize automatic action.", "LOCAL_FACTS", null));
        }
        if (items.stream().anyMatch(item -> item.reviewDecision() == IncidentReplayReviewDecision.ACKNOWLEDGE_RECOMMENDED)) {
            warnings.add(message("ACKNOWLEDGE_RECOMMENDED_ONLY", "WARNING", "ACKNOWLEDGE_RECOMMENDED is advice for human confirmation; no acknowledge record is created.", "LOCAL_FACTS", null));
        }
        if (items.stream().anyMatch(item -> item.reviewState() == IncidentReplayReviewState.CLOSED_RECOMMENDATION)) {
            warnings.add(message("CLOSED_RECOMMENDATION_ONLY", "WARNING", "CLOSED_RECOMMENDATION is diagnostic advice, not a real incident closure.", "LOCAL_FACTS", null));
        }
        return warnings;
    }

    private List<NextStep> overviewNextSteps(List<IncidentReplayReviewItem> items) {
        List<NextStep> steps = new ArrayList<>();
        steps.add(step(
                "KEEP_GET_ONLY_SELECT_ONLY",
                "backend",
                "Keep incident replay review overview as GET-only and repository as SELECT-only",
                "No incident, alert, replay, review, acknowledge, escalation or closeout record is created",
                true
        ));
        if (items.isEmpty()) {
            steps.add(step(
                    "INSPECT_LOCAL_REVIEW_EVIDENCE",
                    "operator",
                    "Inspect whether local incident / replay facts exist through separately authorized diagnostic flows",
                    "Evidence absence is explained without auto-generating incident or replay records",
                    false
            ));
        } else {
            steps.add(step(
                    "REVIEW_INCIDENT_REPLAY_EVIDENCE",
                    "operator",
                    "Review derived incident / replay review items, warnings, limitations and anchors",
                    "Review remains diagnostic and not trading authorization",
                    false
            ));
        }
        if (items.stream().anyMatch(item -> item.severity() == IncidentReplayReviewSeverity.HIGH
                || item.severity() == IncidentReplayReviewSeverity.CRITICAL)) {
            steps.add(step(
                    "MANUALLY_ESCALATE_PRIORITY_ITEMS",
                    "operator",
                    "Manually escalate HIGH / CRITICAL diagnostic items if needed",
                    "No escalation is executed by this endpoint",
                    false
            ));
        }
        return steps;
    }

    private List<EvidenceAnchor> overviewEvidenceAnchors(
            List<IncidentReplayReviewItem> items,
            Instant generatedAt,
            String traceId
    ) {
        if (items.isEmpty()) {
            return List.of(new EvidenceAnchor(
                    "INCIDENT_REPLAY_REVIEW_OVERVIEW",
                    null,
                    "NO_REVIEW_EVIDENCE",
                    generatedAt,
                    traceId,
                    "No local incident / replay review evidence was available."
            ));
        }
        return items.stream()
                .flatMap(item -> item.evidenceAnchors().stream())
                .limit(MAX_ANCHORS)
                .toList();
    }

    private Map<String, Long> severityBuckets(List<IncidentReplayReviewItem> items) {
        Map<IncidentReplayReviewSeverity, Long> counts = new EnumMap<>(IncidentReplayReviewSeverity.class);
        for (IncidentReplayReviewSeverity value : IncidentReplayReviewSeverity.values()) {
            counts.put(value, 0L);
        }
        for (IncidentReplayReviewItem item : items) {
            counts.compute(item.severity(), (key, value) -> value == null ? 1L : value + 1L);
        }
        return enumMap(counts);
    }

    private Map<String, Long> freshnessSummary(List<IncidentReplayReviewItem> items) {
        Map<IncidentReplayReviewFreshness, Long> counts = new EnumMap<>(IncidentReplayReviewFreshness.class);
        for (IncidentReplayReviewFreshness value : IncidentReplayReviewFreshness.values()) {
            counts.put(value, 0L);
        }
        for (IncidentReplayReviewItem item : items) {
            counts.compute(item.evidenceFreshness(), (key, value) -> value == null ? 1L : value + 1L);
        }
        return enumMap(counts);
    }

    private <E extends Enum<E>> Map<String, Long> enumMap(Map<E, Long> values) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map.Entry<E, Long> entry : values.entrySet()) {
            result.put(entry.getKey().name(), entry.getValue());
        }
        return result;
    }

    private long count(List<IncidentReplayReviewItem> items, IncidentReplayReviewState state) {
        return items.stream().filter(item -> item.reviewState() == state).count();
    }

    private String reviewItemId(ReviewEvidenceFact fact) {
        return "irr-" + sha256(seed("REVIEW", fact)).substring(0, 32);
    }

    private String operatorItemId(ReviewEvidenceFact fact) {
        return "op-" + sha256(seed("OPERATOR", fact)).substring(0, 32);
    }

    private String consistencyEvidenceItemId(ReviewEvidenceFact fact) {
        return "cse-" + sha256(String.join("|",
                nullToEmpty(fact.consistencyReportId()),
                nullToEmpty(fact.shadowRunId()),
                nullToEmpty(fact.paperRunId()),
                ""
        )).substring(0, 32);
    }

    private String seed(String prefix, ReviewEvidenceFact fact) {
        return String.join("|",
                prefix,
                nullToEmpty(fact.sourceType()),
                nullToEmpty(fact.sourceId()),
                nullToEmpty(fact.shadowRunId()),
                nullToEmpty(fact.paperRunId()),
                nullToEmpty(fact.consistencyReportId())
        );
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is required for deterministic reviewItemId", ex);
        }
    }

    private BoundaryMessage message(String code, String severity, String message, String sourceType, String sourceId) {
        return new BoundaryMessage(code, severity, message, sourceType, safeText(sourceId));
    }

    private NextStep step(
            String code,
            String owner,
            String action,
            String completionCondition,
            boolean boundaryCritical
    ) {
        return new NextStep(code, owner, action, completionCondition, boundaryCritical);
    }

    private String safeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return SENSITIVE_TEXT_PATTERN.matcher(normalized).find() ? "[filtered diagnostic text]" : normalized;
    }

    private String value(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
