package com.guidinglight.nexusquant.strategy.application.evaluationgate;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * StrategyEvaluationGateFacts 是 infra 层读取到的本地只读事实集合。
 *
 * <p>Why: GateQ-1 需要跨多个历史表聚合事实，但 core 不能依赖 JDBC，也不能调用 nq-research
 * 的写侧服务。本 record 只承载 SELECT 得到的最小事实，用于 service 层 fail-closed 判定。
 */
public record StrategyEvaluationGateFacts(
        StrategyVersionFact strategyVersion,
        DatasetFact dataset,
        EvaluationFact evaluation,
        PublishTraceFact publishTrace,
        PaperEvidenceFact paperEvidence
) {
    public StrategyEvaluationGateFacts {
        strategyVersion = Objects.requireNonNull(strategyVersion, "strategyVersion must not be null");
        dataset = Objects.requireNonNull(dataset, "dataset must not be null");
        evaluation = Objects.requireNonNull(evaluation, "evaluation must not be null");
        publishTrace = Objects.requireNonNull(publishTrace, "publishTrace must not be null");
        paperEvidence = Objects.requireNonNull(paperEvidence, "paperEvidence must not be null");
    }

    /**
     * strategy version 事实；matchesRequestedStrategy=false 表示 query 中 strategyId 与版本归属不一致。
     */
    public record StrategyVersionFact(
            boolean present,
            boolean matchesRequestedStrategy,
            String strategyId,
            String strategyCode,
            String strategyVersionId,
            String status
    ) {
        public static StrategyVersionFact missing() {
            return new StrategyVersionFact(false, false, null, null, null, null);
        }

        public boolean activeForEvaluation() {
            return present && matchesRequestedStrategy && "ACTIVE".equalsIgnoreCase(status);
        }
    }

    /**
     * dataset / coverage 事实；qualitySufficient 只认可 READY + OK + 无缺口/异常的本地事实。
     */
    public record DatasetFact(
            boolean present,
            UUID datasetId,
            String datasetStatus,
            String datasetQualityStatus,
            String coverageQualityStatus,
            Long barCount,
            Long gapCount,
            Long missingBars,
            Long invalidBars,
            Long duplicateBars,
            Instant latestCoverageAt
    ) {
        public static DatasetFact missing(UUID datasetId) {
            return new DatasetFact(false, datasetId, null, null, null, null, null, null, null, null, null);
        }

        public boolean qualitySufficient() {
            return present
                    && "READY".equalsIgnoreCase(datasetStatus)
                    && "OK".equalsIgnoreCase(datasetQualityStatus)
                    && (coverageQualityStatus == null || "OK".equalsIgnoreCase(coverageQualityStatus))
                    && positiveOrUnknown(barCount)
                    && zeroOrNull(gapCount)
                    && zeroOrNull(missingBars)
                    && zeroOrNull(invalidBars)
                    && zeroOrNull(duplicateBars);
        }

        public String effectiveQualityStatus() {
            if (!present) {
                return "NOT_AVAILABLE";
            }
            if (coverageQualityStatus != null && !"OK".equalsIgnoreCase(coverageQualityStatus)) {
                return coverageQualityStatus;
            }
            if (!zeroOrNull(gapCount) || !zeroOrNull(missingBars)) {
                return "GAP_DETECTED";
            }
            if (!zeroOrNull(invalidBars)) {
                return "INVALID";
            }
            if (!positiveOrUnknown(barCount)) {
                return "INCOMPLETE";
            }
            return datasetQualityStatus == null ? "UNKNOWN" : datasetQualityStatus;
        }

        private boolean zeroOrNull(Long value) {
            return value == null || value == 0L;
        }

        private boolean positiveOrUnknown(Long value) {
            return value == null || value > 0L;
        }
    }

    /**
     * evaluation report 事实；metricsComplete 不阻断 gate，但会生成 warning 供后续 review 使用。
     */
    public record EvaluationFact(
            boolean present,
            String evaluationId,
            String backtestRunId,
            String status,
            boolean metricsComplete,
            Instant evaluatedAt
    ) {
        public static EvaluationFact missing(String evaluationId) {
            return new EvaluationFact(false, evaluationId, null, null, false, null);
        }

        public boolean succeeded() {
            return present && "SUCCEEDED".equalsIgnoreCase(status);
        }
    }

    /**
     * publish trace 事实；只认可 SUCCEEDED 发布记录，失败或缺失都不得进入 Shadow review。
     */
    public record PublishTraceFact(
            boolean present,
            String publishId,
            String backtestRunId,
            String evaluationId,
            String strategyVersionId,
            String status,
            Instant publishedAt
    ) {
        public static PublishTraceFact missing(String publishId) {
            return new PublishTraceFact(false, publishId, null, null, null, null, null);
        }

        public boolean succeeded() {
            return present && "SUCCEEDED".equalsIgnoreCase(status);
        }
    }

    /**
     * Paper evidence 事实；只接受 SIM/Paper 的已有运行事实，CREATED/FAILED/LIVE 都按不足处理。
     */
    public record PaperEvidenceFact(
            boolean present,
            String paperRunId,
            String publishId,
            String strategyVersionId,
            String status,
            String tradeEnv,
            Instant updatedAt
    ) {
        public static PaperEvidenceFact missing(String paperRunId) {
            return new PaperEvidenceFact(false, paperRunId, null, null, null, null, null);
        }

        public boolean sufficient() {
            return present
                    && "SIM".equalsIgnoreCase(tradeEnv)
                    && ("STOPPED".equalsIgnoreCase(status) || "RUNNING".equalsIgnoreCase(status));
        }
    }
}
