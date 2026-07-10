package com.guidinglight.nexusquant.strategy.application.consistencyevidence;

import com.fasterxml.jackson.databind.JsonNode;
import com.guidinglight.nexusquant.strategy.application.consistencyevidence.ConsistencyEvidenceOverviewReadModel.BoundaryMessage;
import com.guidinglight.nexusquant.strategy.application.consistencyevidence.ConsistencyEvidenceOverviewReadModel.ConsistencyEvidenceItem;
import com.guidinglight.nexusquant.strategy.application.consistencyevidence.ConsistencyEvidenceOverviewReadModel.EvidenceAnchor;
import com.guidinglight.nexusquant.strategy.application.consistencyevidence.ConsistencyEvidenceOverviewReadModel.MetricDeltaItem;
import com.guidinglight.nexusquant.strategy.application.consistencyevidence.ConsistencyEvidenceOverviewReadModel.MetricDeltaSummary;
import com.guidinglight.nexusquant.strategy.application.consistencyevidence.ConsistencyEvidenceOverviewReadModel.NextStep;
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata;
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadata.Availability;
import com.guidinglight.nexusquant.strategy.application.readmodel.ReadModelEvidenceMetadataCalculator;
import com.guidinglight.nexusquant.strategy.domain.port.ConsistencyEvidenceOverviewFacts;
import com.guidinglight.nexusquant.strategy.domain.port.ConsistencyEvidenceOverviewFacts.ConsistencyReportFact;
import com.guidinglight.nexusquant.strategy.domain.port.ConsistencyEvidenceOverviewQueryPort;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ConsistencyEvidenceOverviewQueryService 组装 GateT-2 consistency evidence overview。
 *
 * <p>职责：只读读取本地 `shadow_consistency_reports` 等 consistency facts，派生 deterministic evidence item、
 * freshness、severity、metricDelta 摘要、blocker、warning、nextStep 和 evidence anchor。该 service 不写库、
 * 不创建 report、不启动 runner/scheduler、不调用 adapter，不读取 credential，也不修改 account/order/ledger 状态。
 */
@Service
public class ConsistencyEvidenceOverviewQueryService {

    private static final Duration STALE_AFTER = Duration.ofDays(7);
    private static final int MAX_EVIDENCE_ITEMS = 50;
    private static final int MAX_ANCHORS = 50;
    private static final int MAX_TEXT_ITEMS = 10;
    private static final int MAX_METRIC_ITEMS = 5;
    private static final Pattern SENSITIVE_TEXT_PATTERN = Pattern.compile(
            "api[_-]?key|secret|passphrase|token|private[_ -]?key|credentialMaterial|"
                    + "decrypted[_-]?payload|encrypted[_-]?payload|rawSignature|rawPrivate|"
                    + "private endpoint|realOrderId|realAccountBalance|authorizedForTrading|"
                    + "tradingReady|liveReady|tradeApproved|can\\s*trade|can[_ -]?trade|"
                    + "ready\\s+to\\s+trade|ready[_ -]?to[_ -]?trade|trade\\s+ready|"
                    + "trade[_ -]?ready|placeOrder|cancelOrder|withdraw|transfer",
            Pattern.CASE_INSENSITIVE
    );

    private final ConsistencyEvidenceOverviewQueryPort queryPort;
    private final Clock clock;

    /**
     * 生产构造器。
     *
     * @param queryPort SELECT-only consistency evidence query port
     */
    @Autowired
    public ConsistencyEvidenceOverviewQueryService(ConsistencyEvidenceOverviewQueryPort queryPort) {
        this(queryPort, Clock.systemUTC());
    }

    ConsistencyEvidenceOverviewQueryService(
            ConsistencyEvidenceOverviewQueryPort queryPort,
            Clock clock
    ) {
        this.queryPort = Objects.requireNonNull(queryPort, "queryPort must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 查询 consistency evidence overview。
     *
     * <p>事务：read-only。副作用：无。空数据返回 safe overview，计数为 0，并用 warning / nextStep
     * 说明没有 consistency evidence；不会抛 500，不会自动创建 report，也不会把 metric delta 推断为收益、
     * 交易建议、授权或自动处置。
     *
     * @param traceId 当前请求 trace id
     * @return GateT-2 consistency evidence overview read model
     */
    @Transactional(readOnly = true)
    public ConsistencyEvidenceOverviewReadModel overview(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        Instant generatedAt = clock.instant();
        ConsistencyEvidenceOverviewFacts facts = queryPort.loadOverviewFacts();
        List<ConsistencyEvidenceItem> items = facts.reports().stream()
                .limit(MAX_EVIDENCE_ITEMS)
                .map(fact -> evidenceItem(fact, generatedAt, traceId))
                .toList();
        MetricDeltaSummary metricDeltaSummary = aggregateMetricDelta(items);
        ReadModelEvidenceMetadata evidenceMetadata = evidenceMetadata(facts, items);

        return new ConsistencyEvidenceOverviewReadModel(
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
                count(items, ConsistencyEvidenceComparisonStatus.CONSISTENT),
                count(items, ConsistencyEvidenceComparisonStatus.DIVERGED),
                count(items, ConsistencyEvidenceComparisonStatus.PARTIAL),
                count(items, ConsistencyEvidenceComparisonStatus.NOT_COMPARABLE),
                count(items, ConsistencyEvidenceComparisonStatus.FAILED),
                items.stream().filter(item -> item.evidenceFreshness() == ConsistencyEvidenceFreshness.STALE).count(),
                items.stream().filter(item -> item.divergenceSeverity() == ConsistencyEvidenceDivergenceSeverity.HIGH).count(),
                items.stream().filter(item -> item.divergenceSeverity() == ConsistencyEvidenceDivergenceSeverity.CRITICAL).count(),
                items.isEmpty() ? null : items.getFirst(),
                items,
                severityBuckets(items),
                freshnessSummary(items),
                metricDeltaSummary,
                overviewBlockers(),
                overviewWarnings(items, metricDeltaSummary),
                overviewNextSteps(items),
                overviewEvidenceAnchors(items, generatedAt, traceId),
                traceId
        );
    }

    /**
     * 从本地 consistency report facts 派生 overview 的统一证据元数据。
     *
     * <p>Why: `generatedAt` 是 report 落地时的权威事实时间；HTTP 响应生成时间不能替代它。空事实
     * 必须显示为 UNAVAILABLE，缺少 report、shadow run 或权威时间的单项为 PARTIAL。新鲜度阈值复用
     * 当前 overview 已使用的 7 天规则，且只用于诊断，不改变原有 item freshness summary。
     *
     * @param facts SELECT-only 本地 consistency report facts
     * @param items 已派生的只读 evidence items
     * @return fail-closed 的 read-model evidence metadata
     */
    private ReadModelEvidenceMetadata evidenceMetadata(
            ConsistencyEvidenceOverviewFacts facts,
            List<ConsistencyEvidenceItem> items
    ) {
        Instant lastCalculatedAt = facts.reports().stream()
                .map(ConsistencyReportFact::generatedAt)
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);
        Availability availability = items.isEmpty()
                ? Availability.UNAVAILABLE
                : items.stream().anyMatch(item -> item.evidenceFreshness() == ConsistencyEvidenceFreshness.MISSING)
                        ? Availability.PARTIAL
                        : Availability.AVAILABLE;
        return new ReadModelEvidenceMetadataCalculator(clock).calculate(
                "LOCAL_DB_SHADOW_CONSISTENCY_REPORTS",
                availability,
                lastCalculatedAt,
                STALE_AFTER
        );
    }

    private ConsistencyEvidenceItem evidenceItem(
            ConsistencyReportFact fact,
            Instant generatedAt,
            String requestTraceId
    ) {
        ConsistencyEvidenceComparisonStatus status = comparisonStatus(fact.comparisonStatus());
        ConsistencyEvidenceDivergenceSeverity severity = severity(status, fact.divergenceReasons());
        ConsistencyEvidenceFreshness freshness = freshness(fact, generatedAt);
        String itemTraceId = safeText(fact.traceId()) == null ? requestTraceId : safeText(fact.traceId());
        MetricDeltaSummary metricDelta = metricDeltaSummary(fact.metricDelta());
        List<String> divergenceReasons = safeTextList(fact.divergenceReasons());
        List<String> limitations = limitations(fact.limitations(), metricDelta);
        return new ConsistencyEvidenceItem(
                evidenceItemId(fact),
                fact.shadowRunId(),
                safeText(fact.paperRunId()),
                fact.consistencyReportId(),
                safeText(fact.strategyVersionId()),
                fact.datasetId(),
                status,
                severity,
                freshness,
                metricDelta,
                divergenceReasons,
                limitations,
                evidenceAnchors(fact, itemTraceId),
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

    private ConsistencyEvidenceComparisonStatus comparisonStatus(String status) {
        if (status == null || status.isBlank()) {
            return ConsistencyEvidenceComparisonStatus.UNKNOWN;
        }
        try {
            return ConsistencyEvidenceComparisonStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ConsistencyEvidenceComparisonStatus.UNKNOWN;
        }
    }

    private ConsistencyEvidenceDivergenceSeverity severity(
            ConsistencyEvidenceComparisonStatus status,
            JsonNode divergenceReasons
    ) {
        return switch (status) {
            case CONSISTENT -> ConsistencyEvidenceDivergenceSeverity.NONE;
            case PARTIAL -> safeTextList(divergenceReasons).isEmpty()
                    ? ConsistencyEvidenceDivergenceSeverity.LOW
                    : ConsistencyEvidenceDivergenceSeverity.MEDIUM;
            case NOT_COMPARABLE -> ConsistencyEvidenceDivergenceSeverity.MEDIUM;
            case DIVERGED -> ConsistencyEvidenceDivergenceSeverity.HIGH;
            case FAILED -> ConsistencyEvidenceDivergenceSeverity.CRITICAL;
            case NO_REPORT, UNKNOWN -> ConsistencyEvidenceDivergenceSeverity.UNKNOWN;
        };
    }

    private ConsistencyEvidenceFreshness freshness(ConsistencyReportFact fact, Instant generatedAt) {
        if (fact.consistencyReportId() == null || fact.shadowRunId() == null || fact.generatedAt() == null) {
            return ConsistencyEvidenceFreshness.MISSING;
        }
        if (fact.generatedAt().isBefore(generatedAt.minus(STALE_AFTER))) {
            return ConsistencyEvidenceFreshness.STALE;
        }
        if (fact.comparisonStatus() == null) {
            return ConsistencyEvidenceFreshness.UNKNOWN;
        }
        return ConsistencyEvidenceFreshness.FRESH;
    }

    private MetricDeltaSummary metricDeltaSummary(JsonNode metricDelta) {
        if (metricDelta == null || metricDelta.isMissingNode() || metricDelta.isNull() || metricDelta.isEmpty()) {
            return MetricDeltaSummary.empty();
        }
        List<MetricDeltaItem> items = new ArrayList<>();
        Set<String> limitationCodes = new LinkedHashSet<>();
        Counter sensitiveFiltered = new Counter();
        collectMetricDelta(metricDelta, "", items, limitationCodes, sensitiveFiltered);
        List<MetricDeltaItem> topDeltaMetrics = items.stream()
                .filter(MetricDeltaItem::comparable)
                .sorted((left, right) -> Double.compare(abs(right.delta()), abs(left.delta())))
                .limit(MAX_METRIC_ITEMS)
                .toList();
        long comparable = items.stream().filter(MetricDeltaItem::comparable).count();
        long nonComparable = items.size() - comparable;
        if (sensitiveFiltered.value > 0) {
            limitationCodes.add("SENSITIVE_FIELD_FILTERED");
        }
        return new MetricDeltaSummary(
                items.size(),
                comparable,
                nonComparable,
                topDeltaMetrics,
                List.copyOf(limitationCodes),
                sensitiveFiltered.value,
                false,
                false,
                false
        );
    }

    private void collectMetricDelta(
            JsonNode node,
            String path,
            List<MetricDeltaItem> items,
            Set<String> limitationCodes,
            Counter sensitiveFiltered
    ) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        if (path != null && !path.isBlank() && SENSITIVE_TEXT_PATTERN.matcher(path).find()) {
            sensitiveFiltered.value++;
            return;
        }
        if (node.isNumber()) {
            addMetricItem(path, node.doubleValue(), null, true, List.of(), items);
            return;
        }
        if (node.isObject() && node.has("delta")) {
            String name = node.hasNonNull("name") ? node.get("name").asText() : path;
            if (SENSITIVE_TEXT_PATTERN.matcher(nullToEmpty(name)).find()) {
                sensitiveFiltered.value++;
                return;
            }
            boolean comparable = !node.has("comparable") || node.get("comparable").asBoolean(true);
            Double delta = node.get("delta").isNumber() ? node.get("delta").doubleValue() : null;
            String unit = node.hasNonNull("unit") ? safeText(node.get("unit").asText()) : null;
            List<String> metricLimitations = safeTextList(node.get("limitationCodes"));
            if (!comparable || delta == null) {
                limitationCodes.add("NON_COMPARABLE_METRIC");
            }
            addMetricItem(name, delta, unit, comparable && delta != null, metricLimitations, items);
            return;
        }
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                String childPath = path == null || path.isBlank() ? entry.getKey() : path + "." + entry.getKey();
                collectMetricDelta(entry.getValue(), childPath, items, limitationCodes, sensitiveFiltered);
            }
            return;
        }
        if (node.isArray()) {
            int index = 0;
            for (JsonNode child : node) {
                collectMetricDelta(child, path + "[" + index + "]", items, limitationCodes, sensitiveFiltered);
                index++;
            }
        }
    }

    private void addMetricItem(
            String name,
            Double delta,
            String unit,
            boolean comparable,
            List<String> limitationCodes,
            List<MetricDeltaItem> items
    ) {
        String safeName = safeText(name);
        if (safeName == null) {
            return;
        }
        items.add(new MetricDeltaItem(safeName, delta, unit, comparable, limitationCodes));
    }

    private MetricDeltaSummary aggregateMetricDelta(List<ConsistencyEvidenceItem> items) {
        long metricCount = 0;
        long comparable = 0;
        long nonComparable = 0;
        long sensitiveFiltered = 0;
        Set<String> limitationCodes = new LinkedHashSet<>();
        List<MetricDeltaItem> metricItems = new ArrayList<>();
        for (ConsistencyEvidenceItem item : items) {
            MetricDeltaSummary metricDelta = item.metricDelta();
            metricCount += metricDelta.metricCount();
            comparable += metricDelta.comparableMetricCount();
            nonComparable += metricDelta.nonComparableMetricCount();
            sensitiveFiltered += metricDelta.sensitiveFieldFilteredCount();
            limitationCodes.addAll(metricDelta.limitationCodes());
            metricItems.addAll(metricDelta.topDeltaMetrics());
        }
        List<MetricDeltaItem> topDeltaMetrics = metricItems.stream()
                .sorted((left, right) -> Double.compare(abs(right.delta()), abs(left.delta())))
                .limit(MAX_METRIC_ITEMS)
                .toList();
        return new MetricDeltaSummary(
                metricCount,
                comparable,
                nonComparable,
                topDeltaMetrics,
                List.copyOf(limitationCodes),
                sensitiveFiltered,
                false,
                false,
                false
        );
    }

    private List<String> limitations(JsonNode limitations, MetricDeltaSummary metricDelta) {
        List<String> safeLimitations = new ArrayList<>(safeTextList(limitations));
        safeLimitations.add("METRIC_DELTA_SUMMARY_ONLY");
        if (metricDelta.sensitiveFieldFilteredCount() > 0) {
            safeLimitations.add("SENSITIVE_FIELD_FILTERED");
        }
        return safeLimitations.stream().distinct().limit(MAX_TEXT_ITEMS).toList();
    }

    private List<String> safeTextList(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode() || node.isEmpty()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        collectSafeText(node, values);
        return values.stream().distinct().limit(MAX_TEXT_ITEMS).toList();
    }

    private void collectSafeText(JsonNode node, List<String> values) {
        if (values.size() >= MAX_TEXT_ITEMS || node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        if (node.isTextual() || node.isNumber() || node.isBoolean()) {
            String safe = safeText(node.asText());
            if (safe != null) {
                values.add(safe);
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectSafeText(child, values);
                if (values.size() >= MAX_TEXT_ITEMS) {
                    return;
                }
            }
            return;
        }
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                if (SENSITIVE_TEXT_PATTERN.matcher(entry.getKey()).find()) {
                    continue;
                }
                JsonNode value = entry.getValue();
                if (value.isTextual() || value.isNumber() || value.isBoolean()) {
                    String safe = safeText(entry.getKey() + "=" + value.asText());
                    if (safe != null) {
                        values.add(safe);
                    }
                } else {
                    String safe = safeText(entry.getKey());
                    if (safe != null) {
                        values.add(safe);
                    }
                }
                if (values.size() >= MAX_TEXT_ITEMS) {
                    return;
                }
            }
        }
    }

    private List<EvidenceAnchor> evidenceAnchors(ConsistencyReportFact fact, String traceId) {
        List<EvidenceAnchor> anchors = new ArrayList<>();
        addAnchor(anchors, "SHADOW_CONSISTENCY_REPORT", uuid(fact.consistencyReportId()), fact.comparisonStatus(), fact.generatedAt(), traceId, "Latest local consistency report anchor.");
        addAnchor(anchors, "SHADOW_RUN", uuid(fact.shadowRunId()), null, fact.generatedAt(), traceId, "Local Shadow Run anchor.");
        addAnchor(anchors, "PAPER_RUN", fact.paperRunId(), null, fact.generatedAt(), traceId, "Local Paper Run id anchor.");
        addAnchor(anchors, "STRATEGY_VERSION", fact.strategyVersionId(), null, fact.generatedAt(), traceId, "Local strategy version id anchor.");
        addAnchor(anchors, "DATASET", uuid(fact.datasetId()), null, fact.generatedAt(), traceId, "Local dataset id anchor.");
        addAnchor(anchors, "SHADOW_SNAPSHOT", fact.latestSnapshotId(), fact.latestSnapshotSchemaVersion(), fact.latestSnapshotAt(), traceId, "Latest local snapshot metadata anchor; payload is not exposed.");
        addAnchor(anchors, "SHADOW_EVENT", fact.latestEventId(), fact.latestEventType(), fact.latestEventAt(), traceId, "Latest local event anchor.");
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
                message("LIVE_DISABLED", "CRITICAL", "LIVE is disabled; consistency evidence overview is diagnostic only.", "SYSTEM_BOUNDARY", null),
                message("REAL_PROVIDER_NOT_IMPLEMENTED", "CRITICAL", "Real provider is not implemented.", "SYSTEM_BOUNDARY", null),
                message("PRIVATE_TRADING_NOT_IMPLEMENTED", "CRITICAL", "Private trading adapter is not implemented.", "SYSTEM_BOUNDARY", null),
                message("NOT_TRADING_AUTHORIZATION", "CRITICAL", "Consistency evidence overview is not trading authorization.", "SYSTEM_BOUNDARY", null)
        );
    }

    private List<BoundaryMessage> overviewWarnings(
            List<ConsistencyEvidenceItem> items,
            MetricDeltaSummary metricDeltaSummary
    ) {
        List<BoundaryMessage> warnings = new ArrayList<>();
        warnings.add(message("AI_DH_RUNTIME_NOT_INTEGRATED", "CRITICAL", "AI is not started and DH runtime is not integrated.", "SYSTEM_BOUNDARY", null));
        warnings.add(message("METRIC_DELTA_SUMMARY_ONLY", "INFO", "metricDelta is summarized for diagnostics only; raw JSONB and profit conclusions are not exposed.", "SYSTEM_BOUNDARY", null));
        if (items.isEmpty()) {
            warnings.add(message("NO_CONSISTENCY_EVIDENCE", "WARNING", "No local consistency evidence is currently available.", "SHADOW_CONSISTENCY_REPORT", null));
        }
        if (items.stream().anyMatch(item -> item.evidenceFreshness() == ConsistencyEvidenceFreshness.STALE)) {
            warnings.add(message("STALE_EVIDENCE", "WARNING", "At least one consistency evidence item is stale.", "SHADOW_CONSISTENCY_REPORT", null));
        }
        if (items.stream().anyMatch(item -> item.comparisonStatus() == ConsistencyEvidenceComparisonStatus.DIVERGED)) {
            warnings.add(message("DIVERGED_IS_DIAGNOSTIC_ONLY", "WARNING", "DIVERGED only means Paper vs Shadow evidence differs; it is not automatic remediation or trading authorization.", "SHADOW_CONSISTENCY_REPORT", null));
        }
        if (items.stream().anyMatch(item -> item.divergenceSeverity() == ConsistencyEvidenceDivergenceSeverity.HIGH
                || item.divergenceSeverity() == ConsistencyEvidenceDivergenceSeverity.CRITICAL)) {
            warnings.add(message("HIGH_CRITICAL_ARE_PRIORITY_ONLY", "WARNING", "HIGH and CRITICAL only express diagnostic priority and do not authorize automatic action.", "SHADOW_CONSISTENCY_REPORT", null));
        }
        if (metricDeltaSummary.sensitiveFieldFilteredCount() > 0) {
            warnings.add(message("SENSITIVE_FIELD_FILTERED", "WARNING", "One or more metricDelta fields were filtered by the sensitive-field guard.", "SHADOW_CONSISTENCY_REPORT", null));
        }
        return warnings;
    }

    private List<NextStep> overviewNextSteps(List<ConsistencyEvidenceItem> items) {
        List<NextStep> steps = new ArrayList<>();
        steps.add(new NextStep(
                "KEEP_GET_ONLY_SELECT_ONLY",
                "backend",
                "Keep consistency evidence overview as GET-only and repository as SELECT-only",
                "No report creation, runner, scheduler, adapter or trading command is added",
                true
        ));
        if (items.isEmpty()) {
            steps.add(new NextStep(
                    "INSPECT_LOCAL_CONSISTENCY_FACTS",
                    "operator",
                    "Inspect whether local consistency reports exist through separately authorized diagnostic flows",
                    "Consistency evidence absence is explained without auto-generating a report",
                    false
            ));
        } else if (items.stream().anyMatch(item -> item.comparisonStatus() == ConsistencyEvidenceComparisonStatus.FAILED)) {
            steps.add(new NextStep(
                    "REVIEW_FAILED_COMPARISON",
                    "operator",
                    "Review failed consistency evidence and keep automated remediation out of scope",
                    "Failed comparison is diagnosed without account, order or ledger mutation",
                    false
            ));
        } else {
            steps.add(new NextStep(
                    "REVIEW_CONSISTENCY_EVIDENCE",
                    "operator",
                    "Review summarized metricDelta, divergence reasons, limitations and anchors",
                    "Evidence is reviewed as diagnostics only and not used as trading authorization",
                    false
            ));
        }
        return steps;
    }

    private List<EvidenceAnchor> overviewEvidenceAnchors(
            List<ConsistencyEvidenceItem> items,
            Instant generatedAt,
            String traceId
    ) {
        if (items.isEmpty()) {
            return List.of(new EvidenceAnchor(
                    "CONSISTENCY_EVIDENCE_OVERVIEW",
                    null,
                    "NO_CONSISTENCY_EVIDENCE",
                    generatedAt,
                    traceId,
                    "No local consistency evidence was available."
            ));
        }
        return items.stream()
                .flatMap(item -> item.evidenceAnchors().stream())
                .limit(MAX_ANCHORS)
                .toList();
    }

    private Map<String, Long> severityBuckets(List<ConsistencyEvidenceItem> items) {
        Map<ConsistencyEvidenceDivergenceSeverity, Long> counts = new EnumMap<>(ConsistencyEvidenceDivergenceSeverity.class);
        for (ConsistencyEvidenceDivergenceSeverity value : ConsistencyEvidenceDivergenceSeverity.values()) {
            counts.put(value, 0L);
        }
        for (ConsistencyEvidenceItem item : items) {
            counts.compute(item.divergenceSeverity(), (key, value) -> value == null ? 1L : value + 1L);
        }
        return enumMap(counts);
    }

    private Map<String, Long> freshnessSummary(List<ConsistencyEvidenceItem> items) {
        Map<ConsistencyEvidenceFreshness, Long> counts = new EnumMap<>(ConsistencyEvidenceFreshness.class);
        for (ConsistencyEvidenceFreshness value : ConsistencyEvidenceFreshness.values()) {
            counts.put(value, 0L);
        }
        for (ConsistencyEvidenceItem item : items) {
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

    private long count(List<ConsistencyEvidenceItem> items, ConsistencyEvidenceComparisonStatus status) {
        return items.stream().filter(item -> item.comparisonStatus() == status).count();
    }

    private String evidenceItemId(ConsistencyReportFact fact) {
        String seed = String.join("|",
                uuid(fact.consistencyReportId()),
                uuid(fact.shadowRunId()),
                nullToEmpty(fact.paperRunId()),
                nullToEmpty(fact.strategyVersionId())
        );
        return "cse-" + sha256(seed).substring(0, 32);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is required for deterministic evidenceItemId", ex);
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

    private double abs(Double value) {
        return value == null ? 0.0d : Math.abs(value);
    }

    private String uuid(UUID value) {
        return value == null ? null : value.toString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static final class Counter {
        private long value;
    }
}
