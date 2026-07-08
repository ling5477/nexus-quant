package com.guidinglight.nexusquant.strategy.domain.port;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * ConsistencyEvidenceOverviewFacts 是 GateT-2 repository 返回给 core 的 SELECT-only 投影。
 *
 * <p>该模型只允许携带本地 consistency / shadow fact 的脱敏字段和 evidence anchor。它不得包含
 * credential material、真实账户余额、真实订单状态、ledger mutation、private provider 配置或 snapshot
 * payload。JSONB 字段保留为输入 facts，必须由 application service 做摘要化和敏感字段过滤后才能进入
 * HTTP response。
 */
public record ConsistencyEvidenceOverviewFacts(List<ConsistencyReportFact> reports) {

    public ConsistencyEvidenceOverviewFacts {
        reports = reports == null ? List.of() : List.copyOf(reports);
    }

    public static ConsistencyEvidenceOverviewFacts empty() {
        return new ConsistencyEvidenceOverviewFacts(List.of());
    }

    /**
     * ConsistencyReportFact 是单条 consistency report 的最小只读证据输入。
     *
     * <p>字段仅来自允许的 local fact tables：`shadow_consistency_reports`、`shadow_runs`、
     * `shadow_run_snapshots` 和 `shadow_run_events`。`metricDelta`、`divergenceReasons`、`limitations`
     * 是 JSONB 原始输入，后续必须摘要化；`latestSnapshot*` 不包含 snapshot payload。
     */
    public record ConsistencyReportFact(
            UUID consistencyReportId,
            UUID shadowRunId,
            String paperRunId,
            String strategyVersionId,
            UUID datasetId,
            String comparisonStatus,
            JsonNode metricDelta,
            JsonNode divergenceReasons,
            JsonNode limitations,
            Instant generatedAt,
            String traceId,
            String latestSnapshotId,
            String latestSnapshotType,
            String latestSnapshotSchemaVersion,
            String latestSnapshotChecksum,
            Instant latestSnapshotAt,
            String latestEventId,
            String latestEventType,
            String latestEventReasonCode,
            Instant latestEventAt
    ) {
        public ConsistencyReportFact {
            comparisonStatus = normalizeStatus(comparisonStatus);
            paperRunId = normalize(paperRunId);
            strategyVersionId = normalize(strategyVersionId);
            traceId = normalize(traceId);
            latestSnapshotId = normalize(latestSnapshotId);
            latestSnapshotType = normalizeStatus(latestSnapshotType);
            latestSnapshotSchemaVersion = normalize(latestSnapshotSchemaVersion);
            latestSnapshotChecksum = normalize(latestSnapshotChecksum);
            latestEventId = normalize(latestEventId);
            latestEventType = normalizeStatus(latestEventType);
            latestEventReasonCode = normalize(latestEventReasonCode);
            metricDelta = Objects.requireNonNull(metricDelta, "metricDelta must not be null");
            divergenceReasons = Objects.requireNonNull(divergenceReasons, "divergenceReasons must not be null");
            limitations = Objects.requireNonNull(limitations, "limitations must not be null");
        }

        private static String normalize(String value) {
            return value == null || value.isBlank() ? null : value.trim();
        }

        private static String normalizeStatus(String value) {
            String normalized = normalize(value);
            return normalized == null ? null : normalized.toUpperCase(java.util.Locale.ROOT);
        }
    }
}
