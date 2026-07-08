package com.guidinglight.nexusquant.monitoring.domain.port;

import java.time.Instant;
import java.util.List;

/**
 * IncidentReplayOverviewFacts 是 GateS-6 repository 层返回的 SELECT-only 聚合投影。
 *
 * <p>该 record 只承载本地 Shadow / Paper / Replay 事实计数和轻量 evidence 摘要，不包含
 * credential、真实账户、真实订单、ledger 或 private provider 配置。
 */
public record IncidentReplayOverviewFacts(
        long shadowEventCount,
        long consistencyDivergenceCount,
        long paperAlertCount,
        long criticalPaperAlertCount,
        long highPaperAlertCount,
        long recoveryEventCount,
        long replayEventCount,
        List<LatestEvidenceFact> latestEvidence
) {
    public IncidentReplayOverviewFacts {
        latestEvidence = latestEvidence == null ? List.of() : List.copyOf(latestEvidence);
    }

    public long totalEvidenceItems() {
        return shadowEventCount
                + consistencyDivergenceCount
                + paperAlertCount
                + recoveryEventCount
                + replayEventCount;
    }

    public static IncidentReplayOverviewFacts empty() {
        return new IncidentReplayOverviewFacts(0, 0, 0, 0, 0, 0, 0, List.of());
    }

    /**
     * LatestEvidenceFact 是 repository 返回的脱敏 evidence 摘要。
     *
     * <p>sourceId 只能是本地事实 ID，不得使用真实订单 ID、真实账户 ID 或 provider payload。
     */
    public record LatestEvidenceFact(
            String evidenceType,
            String sourceId,
            String sourceStatus,
            String summary,
            Instant occurredAt,
            String traceId
    ) {
        public LatestEvidenceFact {
            evidenceType = normalizeRequired(evidenceType, "evidenceType");
            sourceId = normalizeNullable(sourceId);
            sourceStatus = normalizeNullable(sourceStatus);
            summary = normalizeNullable(summary);
            traceId = normalizeNullable(traceId);
        }

        private static String normalizeRequired(String value, String fieldName) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(fieldName + " must not be blank");
            }
            return value.trim();
        }

        private static String normalizeNullable(String value) {
            return value == null || value.isBlank() ? null : value.trim();
        }
    }
}
