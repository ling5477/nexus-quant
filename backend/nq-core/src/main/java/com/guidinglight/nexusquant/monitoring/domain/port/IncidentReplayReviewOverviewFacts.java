package com.guidinglight.nexusquant.monitoring.domain.port;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * IncidentReplayReviewOverviewFacts 是 GateT-3 repository 返回给 core 的 SELECT-only 投影。
 *
 * <p>该模型只允许携带本地 Shadow / consistency / Paper alert / recovery / replay 的脱敏摘要和 id anchor。
 * 它不得包含 credential material、真实账户余额、真实订单状态、ledger mutation、private provider 配置或 raw JSONB payload。
 */
public record IncidentReplayReviewOverviewFacts(List<ReviewEvidenceFact> evidence) {

    public IncidentReplayReviewOverviewFacts {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    public static IncidentReplayReviewOverviewFacts empty() {
        return new IncidentReplayReviewOverviewFacts(List.of());
    }

    /**
     * ReviewEvidenceFact 是单条 Incident / Replay review 的最小事实输入。
     *
     * <p>字段只能来自允许的 local fact tables，summary 必须是脱敏摘要或 count，不允许放入 raw JSONB。
     */
    public record ReviewEvidenceFact(
            String sourceType,
            String sourceId,
            String sourceStatus,
            String sourceSeverity,
            String incidentEvidenceId,
            String replayRecordId,
            String shadowRunId,
            String paperRunId,
            String consistencyReportId,
            String summary,
            Instant occurredAt,
            String traceId
    ) {
        public ReviewEvidenceFact {
            sourceType = normalizeStatusRequired(sourceType, "sourceType");
            sourceId = normalize(sourceId);
            sourceStatus = normalizeStatus(sourceStatus);
            sourceSeverity = normalizeStatus(sourceSeverity);
            incidentEvidenceId = normalize(incidentEvidenceId);
            replayRecordId = normalize(replayRecordId);
            shadowRunId = normalize(shadowRunId);
            paperRunId = normalize(paperRunId);
            consistencyReportId = normalize(consistencyReportId);
            summary = normalize(summary);
            traceId = normalize(traceId);
        }

        private static String normalizeStatusRequired(String value, String fieldName) {
            String normalized = normalize(value);
            if (normalized == null) {
                throw new IllegalArgumentException(fieldName + " must not be blank");
            }
            return normalized.toUpperCase(Locale.ROOT);
        }

        private static String normalizeStatus(String value) {
            String normalized = normalize(value);
            return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
        }

        private static String normalize(String value) {
            return value == null || value.isBlank() ? null : value.trim();
        }
    }
}
