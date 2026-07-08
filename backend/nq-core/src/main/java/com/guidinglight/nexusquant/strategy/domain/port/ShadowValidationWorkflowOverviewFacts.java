package com.guidinglight.nexusquant.strategy.domain.port;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * ShadowValidationWorkflowOverviewFacts 是 GateT-1 repository 返回给 core 的 SELECT-only 投影。
 *
 * <p>该模型只承载 allowed local fact tables 的脱敏字段：strategy/evaluation/publish/Paper/Shadow/consistency
 * 和 incident/replay 证据锚点。它不得包含 credential material、真实账户余额、真实订单状态、ledger mutation
 * 或 private provider 配置。
 */
public record ShadowValidationWorkflowOverviewFacts(List<OperatorEvidenceFact> operatorEvidence) {

    public ShadowValidationWorkflowOverviewFacts {
        operatorEvidence = operatorEvidence == null ? List.of() : List.copyOf(operatorEvidence);
    }

    public static ShadowValidationWorkflowOverviewFacts empty() {
        return new ShadowValidationWorkflowOverviewFacts(List.of());
    }

    /**
     * OperatorEvidenceFact 是派生 operator item 的最小事实输入。
     *
     * <p>字段命名保持 source anchor 语义，不表达交易授权；status 字段只来自允许的本地事实表，用于 service
     * 层 fail-closed 分类。
     */
    public record OperatorEvidenceFact(
            String sourceType,
            String sourceId,
            String strategyVersionId,
            UUID datasetId,
            String evaluationReportId,
            String paperRunId,
            UUID shadowRunId,
            UUID consistencyReportId,
            String incidentEvidenceId,
            String strategyVersionStatus,
            String evaluationStatus,
            String publishStatus,
            String paperRunStatus,
            String paperTradeEnv,
            String shadowRunStatus,
            String consistencyStatus,
            String incidentStatus,
            String incidentSeverity,
            Instant evidenceUpdatedAt,
            String traceId
    ) {
        public OperatorEvidenceFact {
            sourceType = normalizeRequired(sourceType, "sourceType");
            sourceId = normalizeRequired(sourceId, "sourceId");
            strategyVersionId = normalize(strategyVersionId);
            evaluationReportId = normalize(evaluationReportId);
            paperRunId = normalize(paperRunId);
            incidentEvidenceId = normalize(incidentEvidenceId);
            strategyVersionStatus = normalizeStatus(strategyVersionStatus);
            evaluationStatus = normalizeStatus(evaluationStatus);
            publishStatus = normalizeStatus(publishStatus);
            paperRunStatus = normalizeStatus(paperRunStatus);
            paperTradeEnv = normalizeStatus(paperTradeEnv);
            shadowRunStatus = normalizeStatus(shadowRunStatus);
            consistencyStatus = normalizeStatus(consistencyStatus);
            incidentStatus = normalizeStatus(incidentStatus);
            incidentSeverity = normalizeStatus(incidentSeverity);
            traceId = normalize(traceId);
            evidenceUpdatedAt = Objects.requireNonNull(evidenceUpdatedAt, "evidenceUpdatedAt must not be null");
        }

        public boolean hasValidationEvidence() {
            return strategyVersionId != null || evaluationReportId != null;
        }

        public boolean hasShadowEvidence() {
            return shadowRunId != null;
        }

        public boolean hasConsistencyEvidence() {
            return consistencyReportId != null || consistencyStatus != null;
        }

        private static String normalizeRequired(String value, String fieldName) {
            String normalized = normalize(value);
            if (normalized == null) {
                throw new IllegalArgumentException(fieldName + " must not be blank");
            }
            return normalized;
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
