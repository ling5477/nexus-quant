package com.guidinglight.nexusquant.paper.api.dto;

import com.guidinglight.nexusquant.research.application.paper.PaperStrategyEvaluation;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * PaperStrategyEvaluationResponse —— Paper 策略评估只读聚合响应体（GateK Batch K3）。
 *
 * 供前端策略评估视图消费：总览、strategy/publish 维度评分、Paper vs Backtest 偏差与各维度排行。
 * ratingLabel / evaluationConfidence / deviationLevel 以枚举名字符串序列化；评分为 0~100 Paper 内部启发式分。
 * safety 固定声明 SIM/Paper、LIVE 未开启、未触达真实交易所；评估结论不是真实投资评级、不构成投资建议。
 */
@Schema(name = "PaperStrategyEvaluationResponse", description = "Paper 策略评估只读聚合事实源响应体")
public record PaperStrategyEvaluationResponse(
        OverviewResponse overview,
        List<StrategyEvaluationResponse> strategyEvaluations,
        List<PublishEvaluationResponse> publishEvaluations,
        RankingsResponse rankings,
        SafetyResponse safety
) {

    public record OverviewResponse(
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

    public record BacktestDeviationResponse(
            BigDecimal backtestReturn,
            BigDecimal paperReturn,
            BigDecimal returnDeviation,
            BigDecimal backtestMaxDrawdown,
            BigDecimal paperMaxDrawdown,
            BigDecimal drawdownDeviation,
            String deviationLevel,
            String deviationExplanation
    ) {}

    public record StrategyEvaluationResponse(
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
            String ratingLabel,
            String evaluationConfidence,
            String primaryWeakness,
            List<String> warnings,
            BacktestDeviationResponse backtestDeviation
    ) {}

    public record PublishEvaluationResponse(
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
            String ratingLabel,
            String evaluationConfidence,
            List<String> warnings,
            BacktestDeviationResponse backtestDeviation
    ) {}

    public record RankingsResponse(
            List<String> topCompositeStrategies,
            List<String> worstCompositeStrategies,
            List<String> topReturnStrategies,
            List<String> worstDrawdownStrategies,
            List<String> sampleInsufficientStrategies,
            List<String> highDeviationStrategies,
            List<String> highRiskStrategies
    ) {}

    public record SafetyResponse(
            String environment,
            boolean liveEnabled,
            boolean realExchangeTouched,
            String message
    ) {}

    public static PaperStrategyEvaluationResponse from(PaperStrategyEvaluation evaluation) {
        PaperStrategyEvaluation.Overview o = evaluation.overview();
        var overview = new OverviewResponse(
                o.strategyCount(), o.publishCount(), o.evaluatedRunCount(), o.comparableRunCount(),
                o.sampleInsufficientStrategyCount(), o.profitableStrategyCount(), o.lossStrategyCount(),
                o.highRiskStrategyCount(), o.backtestDeviationStrategyCount(), o.topCompositeScore(), o.worstCompositeScore());

        PaperStrategyEvaluation.Rankings r = evaluation.rankings();
        var rankings = new RankingsResponse(
                r.topCompositeStrategies(), r.worstCompositeStrategies(), r.topReturnStrategies(),
                r.worstDrawdownStrategies(), r.sampleInsufficientStrategies(), r.highDeviationStrategies(),
                r.highRiskStrategies());

        var safety = new SafetyResponse(
                "SIM/PAPER", false, false,
                "该策略评估仅基于 Paper 模拟运行与本地执行事实做 Paper 内部启发式评分，不是真实投资评级，不构成投资建议，不代表 LIVE 或真实交易表现");

        return new PaperStrategyEvaluationResponse(
                overview,
                evaluation.strategyEvaluations().stream().map(PaperStrategyEvaluationResponse::strategy).toList(),
                evaluation.publishEvaluations().stream().map(PaperStrategyEvaluationResponse::publish).toList(),
                rankings, safety);
    }

    private static BacktestDeviationResponse deviation(PaperStrategyEvaluation.BacktestDeviation d) {
        if (d == null) {
            return null;
        }
        return new BacktestDeviationResponse(
                d.backtestReturn(), d.paperReturn(), d.returnDeviation(),
                d.backtestMaxDrawdown(), d.paperMaxDrawdown(), d.drawdownDeviation(),
                d.deviationLevel().name(), d.deviationExplanation());
    }

    private static StrategyEvaluationResponse strategy(PaperStrategyEvaluation.StrategyEvaluation s) {
        return new StrategyEvaluationResponse(
                s.strategyVersionId(), s.runCount(), s.comparableRunCount(), s.publishCount(), s.latestRunTime(),
                s.currentEquity(), s.initialEquity(), s.totalPnl(), s.totalReturn(), s.maxDrawdown(),
                s.winRunCount(), s.lossRunCount(), s.winRate(), s.averageReturn(), s.averageDrawdown(),
                s.riskBlockedCount(), s.dataInsufficientCount(), s.noOrderCount(), s.orderNoFillCount(),
                s.filledRunCount(), s.filledLossCount(), s.failedRunCount(),
                s.sampleScore(), s.riskScore(), s.returnScore(), s.executionScore(), s.backtestDeviationScore(),
                s.compositeScore(), s.ratingLabel().name(), s.evaluationConfidence().name(), s.primaryWeakness(),
                s.warnings(), deviation(s.backtestDeviation()));
    }

    private static PublishEvaluationResponse publish(PaperStrategyEvaluation.PublishEvaluation p) {
        return new PublishEvaluationResponse(
                p.publishId(), p.strategyVersionId(), p.runCount(), p.comparableRunCount(), p.latestRunTime(),
                p.totalPnl(), p.totalReturn(), p.maxDrawdown(), p.winRate(),
                p.sampleScore(), p.riskScore(), p.returnScore(), p.executionScore(), p.backtestDeviationScore(),
                p.compositeScore(), p.ratingLabel().name(), p.evaluationConfidence().name(),
                p.warnings(), deviation(p.backtestDeviation()));
    }
}
