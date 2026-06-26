package com.guidinglight.nexusquant.paper.api.dto;

import com.guidinglight.nexusquant.research.application.paper.PaperAutoReview;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * PaperAutoReviewResponse —— Paper 规则化自动复盘只读聚合响应体（GateK Batch K4）。
 *
 * 供前端自动复盘视图消费：总览、组合复盘、重点 run 复盘、策略/发布复盘与问题聚类。
 * primaryCause / severity / confidence / ratingLabel / cause 等以枚举名字符串序列化。
 * safety 固定声明 paperOnly / rulesBased / noInvestmentAdvice / noLiveTrading / noAiRuntime，
 * 复盘结论是 Paper 模拟事实的规则化摘要，不构成真实投资建议、不代表 LIVE 或真实交易表现。
 */
@Schema(name = "PaperAutoReviewResponse", description = "Paper 规则化自动复盘只读聚合事实源响应体")
public record PaperAutoReviewResponse(
        OverviewResponse overview,
        PortfolioReviewResponse portfolioReview,
        List<RunReviewResponse> runReviews,
        List<StrategyReviewResponse> strategyReviews,
        List<PublishReviewResponse> publishReviews,
        List<IssueClusterResponse> issueClusters,
        SafetyResponse safety
) {

    public record OverviewResponse(
            int totalRuns,
            int reviewedRunCount,
            int issueRunCount,
            int healthyRunCount,
            int criticalIssueCount,
            int warningIssueCount,
            int strategyReviewedCount,
            int publishReviewedCount,
            String topIssueCause,
            String topWeakness,
            Instant generatedAt
    ) {}

    public record PortfolioReviewResponse(
            String headline,
            String summary,
            List<String> keyFindings,
            List<String> riskHighlights,
            List<String> executionHighlights,
            List<String> strategyHighlights,
            List<String> backtestDeviationHighlights,
            List<String> suggestedNextActions,
            List<String> limitations
    ) {}

    public record RunReviewResponse(
            String paperRunId,
            String strategyVersionId,
            String publishId,
            String status,
            String primaryCause,
            String severity,
            String confidence,
            BigDecimal totalPnl,
            BigDecimal totalReturn,
            BigDecimal maxDrawdown,
            String reviewHeadline,
            String reviewSummary,
            List<String> keyFacts,
            List<String> likelyReasons,
            List<String> suggestedActions,
            List<String> tags
    ) {}

    public record StrategyReviewResponse(
            String strategyVersionId,
            String ratingLabel,
            int compositeScore,
            String evaluationConfidence,
            String primaryWeakness,
            String reviewHeadline,
            String reviewSummary,
            List<String> strengths,
            List<String> weaknesses,
            List<String> warnings,
            List<String> suggestedActions
    ) {}

    public record PublishReviewResponse(
            String publishId,
            String strategyVersionId,
            String ratingLabel,
            int compositeScore,
            String evaluationConfidence,
            String primaryWeakness,
            String reviewHeadline,
            String reviewSummary,
            List<String> strengths,
            List<String> weaknesses,
            List<String> warnings,
            List<String> suggestedActions
    ) {}

    public record IssueClusterResponse(
            String clusterKey,
            String cause,
            String severity,
            int count,
            List<String> affectedRunIds,
            List<String> affectedStrategyVersionIds,
            List<String> affectedPublishIds,
            String summary,
            String suggestedAction
    ) {}

    /**
     * 安全声明：固定为 Paper-only / 规则化 / 不构成投资建议 / LIVE 未开启 / 未接 AI runtime。
     * message 为人读说明；所有布尔不可变为 true 之外的值（本端点恒为上述安全态）。
     */
    public record SafetyResponse(
            boolean paperOnly,
            boolean rulesBased,
            boolean noInvestmentAdvice,
            boolean noLiveTrading,
            boolean noAiRuntime,
            String message
    ) {}

    public static PaperAutoReviewResponse from(PaperAutoReview review) {
        PaperAutoReview.Overview o = review.overview();
        var overview = new OverviewResponse(
                o.totalRuns(), o.reviewedRunCount(), o.issueRunCount(), o.healthyRunCount(),
                o.criticalIssueCount(), o.warningIssueCount(), o.strategyReviewedCount(), o.publishReviewedCount(),
                o.topIssueCause(), o.topWeakness(), o.generatedAt());

        PaperAutoReview.PortfolioReview p = review.portfolioReview();
        var portfolioReview = new PortfolioReviewResponse(
                p.headline(), p.summary(), p.keyFindings(), p.riskHighlights(), p.executionHighlights(),
                p.strategyHighlights(), p.backtestDeviationHighlights(), p.suggestedNextActions(), p.limitations());

        var safety = new SafetyResponse(
                true, true, true, true, true,
                "该自动复盘仅基于 Paper 模拟运行与本地执行事实做规则化摘要，未接 AI / DH runtime，"
                        + "不构成真实投资建议，不代表 LIVE 或真实交易表现");

        return new PaperAutoReviewResponse(
                overview, portfolioReview,
                review.runReviews().stream().map(PaperAutoReviewResponse::runReview).toList(),
                review.strategyReviews().stream().map(PaperAutoReviewResponse::strategyReview).toList(),
                review.publishReviews().stream().map(PaperAutoReviewResponse::publishReview).toList(),
                review.issueClusters().stream().map(PaperAutoReviewResponse::issueCluster).toList(),
                safety);
    }

    private static RunReviewResponse runReview(PaperAutoReview.RunReview r) {
        return new RunReviewResponse(
                r.paperRunId(), r.strategyVersionId(), r.publishId(), r.status(),
                r.primaryCause(), r.severity(), r.confidence(),
                r.totalPnl(), r.totalReturn(), r.maxDrawdown(),
                r.reviewHeadline(), r.reviewSummary(),
                r.keyFacts(), r.likelyReasons(), r.suggestedActions(), r.tags());
    }

    private static StrategyReviewResponse strategyReview(PaperAutoReview.StrategyReview s) {
        return new StrategyReviewResponse(
                s.strategyVersionId(), s.ratingLabel(), s.compositeScore(), s.evaluationConfidence(),
                s.primaryWeakness(), s.reviewHeadline(), s.reviewSummary(),
                s.strengths(), s.weaknesses(), s.warnings(), s.suggestedActions());
    }

    private static PublishReviewResponse publishReview(PaperAutoReview.PublishReview p) {
        return new PublishReviewResponse(
                p.publishId(), p.strategyVersionId(), p.ratingLabel(), p.compositeScore(), p.evaluationConfidence(),
                p.primaryWeakness(), p.reviewHeadline(), p.reviewSummary(),
                p.strengths(), p.weaknesses(), p.warnings(), p.suggestedActions());
    }

    private static IssueClusterResponse issueCluster(PaperAutoReview.IssueCluster c) {
        return new IssueClusterResponse(
                c.clusterKey(), c.cause(), c.severity(), c.count(),
                c.affectedRunIds(), c.affectedStrategyVersionIds(), c.affectedPublishIds(),
                c.summary(), c.suggestedAction());
    }
}
