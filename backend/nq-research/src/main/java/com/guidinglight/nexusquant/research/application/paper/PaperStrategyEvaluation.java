package com.guidinglight.nexusquant.research.application.paper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * PaperStrategyEvaluation —— Paper 策略评估只读聚合事实源（GateK Batch K3）。
 *
 * 职责：在 K1/K2 已能解释「为什么执行异常」的基础上，从 strategyVersionId / publishId 维度评估
 * Paper 模拟表现、Paper vs Backtest 偏差、样本充足性与风险调整评分，回答「哪些策略更稳定 / 偏差最大 /
 * 样本太少 / 收益高但风险高 / 需继续观察」。
 *
 * 关键约束：
 * 1) 纯派生：不写库、不触发任何状态机 / 下单 / 调度 / 回测 / 发布动作、不外呼、不读凭证。
 * 2) 评分为 Paper 内部启发式评估（0~100），<b>不是真实投资评级、不代表 LIVE 或真实交易表现、不构成投资建议</b>。
 * 3) Backtest 对照数据缺失时 backtestDeviation 为 null、backtestDeviationScore 为 null，并在 warnings /
 *    evaluationConfidence 中显式降级，不伪造偏差。
 * 4) 收益率 / 回撤为比例值（如 -0.1 表示回撤 10%）；数据不足时相关字段为 null，不外推。
 */
public record PaperStrategyEvaluation(
        Overview overview,
        List<StrategyEvaluation> strategyEvaluations,
        List<PublishEvaluation> publishEvaluations,
        Rankings rankings
) {

    /** Paper 内部评级（非真实投资评级）。 */
    public enum RatingLabel {
        STRONG_PAPER_PERFORMER,
        WATCHLIST,
        HIGH_RISK,
        SAMPLE_INSUFFICIENT,
        DATA_INSUFFICIENT,
        EXECUTION_PROBLEM,
        UNKNOWN
    }

    /** 评估可信度：样本与数据完整度越高越可信。 */
    public enum EvaluationConfidence {
        HIGH,
        MEDIUM,
        LOW
    }

    /** Paper vs Backtest 偏差等级；无 backtest 数据时为 UNAVAILABLE。 */
    public enum DeviationLevel {
        LOW,
        MEDIUM,
        HIGH,
        UNAVAILABLE
    }

    public record Overview(
            int strategyCount,
            int publishCount,
            int evaluatedRunCount,
            int comparableRunCount,
            int sampleInsufficientStrategyCount,
            int profitableStrategyCount,
            int lossStrategyCount,
            int highRiskStrategyCount,
            int backtestDeviationStrategyCount,
            Integer topCompositeScore,
            Integer worstCompositeScore
    ) {}

    /**
     * Paper vs Backtest 偏差（strategy / publish 维度）。
     * return / drawdown 为比例值；deviation = paper - backtest；无 backtest 数据时整体为 null 由调用方处理。
     */
    public record BacktestDeviation(
            BigDecimal backtestReturn,
            BigDecimal paperReturn,
            BigDecimal returnDeviation,
            BigDecimal backtestMaxDrawdown,
            BigDecimal paperMaxDrawdown,
            BigDecimal drawdownDeviation,
            DeviationLevel deviationLevel,
            String deviationExplanation
    ) {}

    public record StrategyEvaluation(
            String strategyVersionId,
            int runCount,
            int comparableRunCount,
            int publishCount,
            Instant latestRunTime,
            BigDecimal currentEquity,
            BigDecimal initialEquity,
            BigDecimal totalPnl,
            BigDecimal totalReturn,
            BigDecimal maxDrawdown,
            int winRunCount,
            int lossRunCount,
            BigDecimal winRate,
            BigDecimal averageReturn,
            BigDecimal averageDrawdown,
            int riskBlockedCount,
            int dataInsufficientCount,
            int noOrderCount,
            int orderNoFillCount,
            int filledRunCount,
            int filledLossCount,
            int failedRunCount,
            int sampleScore,
            int riskScore,
            int returnScore,
            int executionScore,
            Integer backtestDeviationScore,
            int compositeScore,
            RatingLabel ratingLabel,
            EvaluationConfidence evaluationConfidence,
            String primaryWeakness,
            List<String> warnings,
            BacktestDeviation backtestDeviation
    ) {}

    public record PublishEvaluation(
            String publishId,
            String strategyVersionId,
            int runCount,
            int comparableRunCount,
            Instant latestRunTime,
            BigDecimal totalPnl,
            BigDecimal totalReturn,
            BigDecimal maxDrawdown,
            BigDecimal winRate,
            int sampleScore,
            int riskScore,
            int returnScore,
            int executionScore,
            Integer backtestDeviationScore,
            int compositeScore,
            RatingLabel ratingLabel,
            EvaluationConfidence evaluationConfidence,
            List<String> warnings,
            BacktestDeviation backtestDeviation
    ) {}

    /** 各维度排行（值为 strategyVersionId / publishId 键，已截断到上限）。 */
    public record Rankings(
            List<String> topCompositeStrategies,
            List<String> worstCompositeStrategies,
            List<String> topReturnStrategies,
            List<String> worstDrawdownStrategies,
            List<String> sampleInsufficientStrategies,
            List<String> highDeviationStrategies,
            List<String> highRiskStrategies
    ) {}
}
