package com.guidinglight.nexusquant.strategy.application.consistencyevidence;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * ConsistencyEvidenceOverviewReadModel 是 GateT-2 consistency evidence overview 的只读响应合同。
 *
 * <p>职责：聚合 Paper vs Shadow consistency evidence、派生 evidence item、severity / freshness bucket、
 * metric delta 摘要、blocker/warning/nextStep 和 evidence anchor。该模型不持久化 evidence item，
 * 不创建 consistency report，不启动 runner/scheduler，不读取 credential，不调用 adapter，不表达交易授权。
 */
public record ConsistencyEvidenceOverviewReadModel(
        Instant generatedAt,
        boolean diagnosticOnly,
        boolean noSideEffect,
        boolean notTradingAuthorization,
        boolean liveDisabled,
        boolean realProviderImplemented,
        boolean privateTradingImplemented,
        boolean aiDhRuntimeIntegrated,
        long totalEvidenceItems,
        long consistentCount,
        long divergedCount,
        long partialCount,
        long notComparableCount,
        long failedCount,
        long staleEvidenceCount,
        long highSeverityCount,
        long criticalSeverityCount,
        ConsistencyEvidenceItem latestEvidenceItem,
        List<ConsistencyEvidenceItem> evidenceItems,
        Map<String, Long> severityBuckets,
        Map<String, Long> freshnessSummary,
        MetricDeltaSummary metricDeltaSummary,
        List<BoundaryMessage> blockers,
        List<BoundaryMessage> warnings,
        List<NextStep> nextSteps,
        List<EvidenceAnchor> evidenceAnchors,
        String traceId
) {
    public ConsistencyEvidenceOverviewReadModel {
        generatedAt = Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        evidenceItems = evidenceItems == null ? List.of() : List.copyOf(evidenceItems);
        severityBuckets = unmodifiableLinkedMap(severityBuckets);
        freshnessSummary = unmodifiableLinkedMap(freshnessSummary);
        metricDeltaSummary = Objects.requireNonNull(metricDeltaSummary, "metricDeltaSummary must not be null");
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        nextSteps = nextSteps == null ? List.of() : List.copyOf(nextSteps);
        evidenceAnchors = evidenceAnchors == null ? List.of() : List.copyOf(evidenceAnchors);
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
    }

    /**
     * ConsistencyEvidenceItem 是从本地 consistency report 派生的 deterministic item。
     *
     * <p>字段用于定位 evidence、解释比较状态和提示人工诊断动作。即使 comparisonStatus=CONSISTENT，
     * 也必须固定 notTradingAuthorization=true；即使 severity=HIGH/CRITICAL，也只表示诊断优先级。
     */
    public record ConsistencyEvidenceItem(
            String evidenceItemId,
            UUID shadowRunId,
            String paperRunId,
            UUID consistencyReportId,
            String strategyVersionId,
            UUID datasetId,
            ConsistencyEvidenceComparisonStatus comparisonStatus,
            ConsistencyEvidenceDivergenceSeverity divergenceSeverity,
            ConsistencyEvidenceFreshness evidenceFreshness,
            MetricDeltaSummary metricDelta,
            List<String> divergenceReasons,
            List<String> limitations,
            List<EvidenceAnchor> evidenceAnchors,
            String traceId,
            Instant generatedAt,
            boolean diagnosticOnly,
            boolean noSideEffect,
            boolean notTradingAuthorization,
            boolean liveDisabled,
            boolean realProviderImplemented,
            boolean privateTradingImplemented,
            boolean aiDhRuntimeIntegrated
    ) {
        public ConsistencyEvidenceItem {
            evidenceItemId = required(evidenceItemId, "evidenceItemId");
            comparisonStatus = Objects.requireNonNull(comparisonStatus, "comparisonStatus must not be null");
            divergenceSeverity = Objects.requireNonNull(divergenceSeverity, "divergenceSeverity must not be null");
            evidenceFreshness = Objects.requireNonNull(evidenceFreshness, "evidenceFreshness must not be null");
            metricDelta = Objects.requireNonNull(metricDelta, "metricDelta must not be null");
            divergenceReasons = divergenceReasons == null ? List.of() : List.copyOf(divergenceReasons);
            limitations = limitations == null ? List.of() : List.copyOf(limitations);
            evidenceAnchors = evidenceAnchors == null ? List.of() : List.copyOf(evidenceAnchors);
            traceId = required(traceId, "traceId");
            generatedAt = Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        }
    }

    /**
     * MetricDeltaSummary 是 JSONB `metric_delta` 的安全摘要。
     *
     * <p>它只暴露 metric 名称、delta 与比较可用性；不返回 raw JSONB，不推断收益结论，不生成交易建议。
     */
    public record MetricDeltaSummary(
            long metricCount,
            long comparableMetricCount,
            long nonComparableMetricCount,
            List<MetricDeltaItem> topDeltaMetrics,
            List<String> limitationCodes,
            long sensitiveFieldFilteredCount,
            boolean rawMetricDeltaExposed,
            boolean profitConclusionInferred,
            boolean tradingSignalInferred
    ) {
        public MetricDeltaSummary {
            if (metricCount < 0
                    || comparableMetricCount < 0
                    || nonComparableMetricCount < 0
                    || sensitiveFieldFilteredCount < 0) {
                throw new IllegalArgumentException("metric delta counts must not be negative");
            }
            topDeltaMetrics = topDeltaMetrics == null ? List.of() : List.copyOf(topDeltaMetrics);
            limitationCodes = limitationCodes == null ? List.of() : List.copyOf(limitationCodes);
        }

        public static MetricDeltaSummary empty() {
            return new MetricDeltaSummary(0, 0, 0, List.of(), List.of(), 0, false, false, false);
        }
    }

    /**
     * MetricDeltaItem 是单个 metric 的摘要项。
     */
    public record MetricDeltaItem(
            String name,
            Double delta,
            String unit,
            boolean comparable,
            List<String> limitationCodes
    ) {
        public MetricDeltaItem {
            name = required(name, "name");
            unit = optional(unit);
            limitationCodes = limitationCodes == null ? List.of() : List.copyOf(limitationCodes);
        }
    }

    /**
     * BoundaryMessage 描述 blocker / warning，不携带 raw payload、credential 或交易命令。
     */
    public record BoundaryMessage(
            String code,
            String severity,
            String message,
            String sourceType,
            String sourceId
    ) {
        public BoundaryMessage {
            code = required(code, "code");
            severity = required(severity, "severity");
            message = required(message, "message");
            sourceType = required(sourceType, "sourceType");
            sourceId = optional(sourceId);
        }
    }

    /**
     * NextStep 只描述 inspect/review/refresh evidence 等诊断动作，不是交易执行指令。
     */
    public record NextStep(
            String code,
            String owner,
            String action,
            String completionCondition,
            boolean boundaryCritical
    ) {
        public NextStep {
            code = required(code, "code");
            owner = required(owner, "owner");
            action = required(action, "action");
            completionCondition = required(completionCondition, "completionCondition");
        }
    }

    /**
     * EvidenceAnchor 只定位本地 read-only fact source，不复制 snapshot payload 或 JSONB 原文。
     */
    public record EvidenceAnchor(
            String sourceType,
            String sourceId,
            String sourceVersion,
            Instant sourceTimestamp,
            String traceId,
            String description
    ) {
        public EvidenceAnchor {
            sourceType = required(sourceType, "sourceType");
            sourceId = optional(sourceId);
            sourceVersion = optional(sourceVersion);
            traceId = optional(traceId);
            description = optional(description);
        }
    }

    private static Map<String, Long> unmodifiableLinkedMap(Map<String, Long> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
