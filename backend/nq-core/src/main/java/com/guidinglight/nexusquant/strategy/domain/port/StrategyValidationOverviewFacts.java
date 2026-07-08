package com.guidinglight.nexusquant.strategy.domain.port;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * StrategyValidationOverviewFacts 是 GateS-3 read model 的 SELECT-only repository 投影。
 *
 * <p>它只承载 repository 从允许事实表读取到的最小聚合结果；core service 负责把这些事实转换成
 * validation decision、blocker、warning、nextStep 和安全边界。
 */
public record StrategyValidationOverviewFacts(
        long totalStrategyVersions,
        long evaluatedStrategyVersions,
        long approvedForValidation,
        long rejectedForValidation,
        long needsReview,
        long blocked,
        Optional<LatestDecisionFact> latestDecision
) {
    public StrategyValidationOverviewFacts {
        latestDecision = latestDecision == null ? Optional.empty() : latestDecision;
    }

    /**
     * 最新策略验证事实；字段只用于证据定位，不包含收益、胜率、交易放行或敏感材料。
     */
    public record LatestDecisionFact(
            String strategyVersionId,
            UUID datasetId,
            String evaluationReportId,
            String publishId,
            String paperRunId,
            UUID shadowRunId,
            String strategyVersionStatus,
            String evaluationStatus,
            String publishStatus,
            String paperRunStatus,
            String paperTradeEnv,
            String shadowRunStatus,
            String consistencyStatus,
            Instant generatedAt,
            Instant evidenceUpdatedAt
    ) {
        public LatestDecisionFact {
            strategyVersionId = normalize(strategyVersionId);
            evaluationReportId = normalize(evaluationReportId);
            publishId = normalize(publishId);
            paperRunId = normalize(paperRunId);
            strategyVersionStatus = normalizeStatus(strategyVersionStatus);
            evaluationStatus = normalizeStatus(evaluationStatus);
            publishStatus = normalizeStatus(publishStatus);
            paperRunStatus = normalizeStatus(paperRunStatus);
            paperTradeEnv = normalizeStatus(paperTradeEnv);
            shadowRunStatus = normalizeStatus(shadowRunStatus);
            consistencyStatus = normalizeStatus(consistencyStatus);
            generatedAt = Objects.requireNonNull(generatedAt, "generatedAt must not be null");
            evidenceUpdatedAt = evidenceUpdatedAt == null ? generatedAt : evidenceUpdatedAt;
        }

        public boolean hasEvaluationReport() {
            return evaluationReportId != null;
        }

        public boolean evaluationSucceeded() {
            return "SUCCEEDED".equals(evaluationStatus);
        }

        public boolean evaluationFailed() {
            return "FAILED".equals(evaluationStatus)
                    || "FAILURE".equals(evaluationStatus)
                    || "ERROR".equals(evaluationStatus);
        }

        public boolean publishSucceeded() {
            return publishId != null && "SUCCEEDED".equals(publishStatus);
        }

        public boolean paperEvidenceSufficient() {
            return paperRunId != null
                    && "SIM".equals(paperTradeEnv)
                    && ("RUNNING".equals(paperRunStatus) || "STOPPED".equals(paperRunStatus));
        }

        private static String normalize(String value) {
            return value == null || value.isBlank() ? null : value.trim();
        }

        private static String normalizeStatus(String value) {
            String normalized = normalize(value);
            return normalized == null ? null : normalized.toUpperCase(java.util.Locale.ROOT);
        }
    }

    /**
     * OverviewCounts 是 repository 层聚合后的计数投影；用于避免把全量 strategy version 明细拉入内存。
     */
    public record OverviewCounts(
            long totalStrategyVersions,
            long evaluatedStrategyVersions,
            long approvedForValidation,
            long rejectedForValidation,
            long needsReview,
            long blocked
    ) {
        public static OverviewCounts empty() {
            return new OverviewCounts(0, 0, 0, 0, 0, 0);
        }
    }
}
