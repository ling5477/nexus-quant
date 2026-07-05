package com.guidinglight.nexusquant.strategy.application.papershadowcomparison;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * PaperShadowComparisonFacts 是 infra 层读取到的本地只读事实集合。
 *
 * <p>Why: GateQ-2 需要跨 strategy version、dataset、evaluation、publish 和 Paper 表聚合事实，
 * 但 core 不能依赖 JDBC。Shadow run 当前没有表和 runner，生产 repository 必须返回 notImplemented，
 * 由 service fail-closed；测试 fixture 可构造 future read-only shadow fact 来固化 DTO 语义。
 */
public record PaperShadowComparisonFacts(
        StrategyVersionFact strategyVersion,
        DatasetFact dataset,
        EvaluationFact evaluation,
        PublishTraceFact publishTrace,
        PaperRunFact paperRun,
        ShadowRunFact shadowRun
) {
    public PaperShadowComparisonFacts {
        strategyVersion = Objects.requireNonNull(strategyVersion, "strategyVersion must not be null");
        dataset = Objects.requireNonNull(dataset, "dataset must not be null");
        evaluation = Objects.requireNonNull(evaluation, "evaluation must not be null");
        publishTrace = Objects.requireNonNull(publishTrace, "publishTrace must not be null");
        paperRun = Objects.requireNonNull(paperRun, "paperRun must not be null");
        shadowRun = Objects.requireNonNull(shadowRun, "shadowRun must not be null");
    }

    /** strategy version 事实；matchesRequestedStrategy=false 表示 query scope 与版本归属不一致。 */
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

        public boolean activeForComparison() {
            return present && matchesRequestedStrategy && "ACTIVE".equalsIgnoreCase(status);
        }
    }

    /** dataset / coverage 事实；只认可 READY + OK + 无缺口/异常的本地质量证据。 */
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

    /** evaluation gate 事实；成功才可进入 Paper vs Shadow 对照链。 */
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

        public boolean passedGate() {
            return present && "SUCCEEDED".equalsIgnoreCase(status);
        }
    }

    /** publish trace 事实；只认可已成功发布且与 strategy/evaluation 链一致的本地记录。 */
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

    /** Paper run 事实；只允许 SIM 且 RUNNING/STOPPED 的既有事实进入只读比较候选。 */
    public record PaperRunFact(
            boolean present,
            String paperRunId,
            String publishId,
            String strategyVersionId,
            String status,
            String tradeEnv,
            Instant updatedAt
    ) {
        public static PaperRunFact missing(String paperRunId) {
            return new PaperRunFact(false, paperRunId, null, null, null, null, null);
        }

        public boolean comparableEvidence() {
            return present
                    && "SIM".equalsIgnoreCase(tradeEnv)
                    && ("STOPPED".equalsIgnoreCase(status) || "RUNNING".equalsIgnoreCase(status));
        }
    }

    /**
     * Shadow run 事实；当前生产路径固定为 notImplemented。
     *
     * <p>Why: GateQ-2 需要 DTO baseline 先表达 Shadow 未实现与缺失语义，但本轮禁止新增 shadow
     * table、创建 shadow run 或写 shadow 状态。future fixture 仅用于测试 read model 语义。
     */
    public record ShadowRunFact(
            boolean runnerImplemented,
            boolean present,
            String shadowRunId,
            String publishId,
            String strategyVersionId,
            UUID datasetId,
            String evaluationId,
            String status,
            Instant updatedAt
    ) {
        public static ShadowRunFact notImplemented(String shadowRunId) {
            return new ShadowRunFact(false, false, shadowRunId, null, null, null, null, "NOT_IMPLEMENTED", null);
        }

        public static ShadowRunFact missing(String shadowRunId) {
            return new ShadowRunFact(true, false, shadowRunId, null, null, null, null, "NOT_AVAILABLE", null);
        }

        public boolean comparableEvidence() {
            return runnerImplemented
                    && present
                    && ("COMPLETED".equalsIgnoreCase(status)
                    || "SUCCEEDED".equalsIgnoreCase(status)
                    || "STOPPED".equalsIgnoreCase(status));
        }

        public String effectiveStatus() {
            if (!runnerImplemented) {
                return "NOT_IMPLEMENTED";
            }
            if (!present) {
                return "NOT_AVAILABLE";
            }
            return status == null || status.isBlank() ? "UNKNOWN" : status;
        }
    }
}
