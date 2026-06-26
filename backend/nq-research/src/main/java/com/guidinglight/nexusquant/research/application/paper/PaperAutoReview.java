package com.guidinglight.nexusquant.research.application.paper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * PaperAutoReview —— Paper 规则化自动复盘只读聚合事实源（GateK Batch K4）。
 *
 * 职责：在 K1 执行诊断（{@link PaperExecutionDiagnostics}，回答「为什么异常」）与 K3 策略评估
 * （{@link PaperStrategyEvaluation}，回答「哪些策略好/差/样本不足/偏差大」）已产出结构化事实的基础上，
 * 以确定性规则把组合、策略、发布与重点 run 的事实归纳为结构化复盘摘要，并按问题类型做确定性聚类，
 * 回答「整体怎么样、重点 run 该查什么、哪些策略需要关注、问题集中在哪些类别」。
 *
 * 关键约束：
 * 1) 纯派生 / 规则化：不接 AI/LLM/DH runtime、不写库、不触发任何状态机 / 下单 / 调度 / 回测 / 发布动作、
 *    不外呼、不读凭证；复盘文案由 if/else + enum mapping + 字符串模板生成。
 * 2) 仅覆盖 SIM/Paper：复盘结论是 Paper 模拟执行事实的规则化摘要，<b>不构成真实投资建议、不代表 LIVE 或真实交易表现</b>。
 * 3) suggestedActions 一律为工程排查动作（检查数据 / 触发条件 / 撮合参数 / 风控阈值 / 增加样本 / 复核偏差），
 *    <b>不含买入/卖出/加仓/减仓/做多/做空/实盘执行等投资动作</b>。
 * 4) 事实缺失时降级置信度并在 limitations / warnings 显式说明，不外推、不伪造；无 run 时返回稳定空结构。
 */
public record PaperAutoReview(
        Overview overview,
        PortfolioReview portfolioReview,
        List<RunReview> runReviews,
        List<StrategyReview> strategyReviews,
        List<PublishReview> publishReviews,
        List<IssueCluster> issueClusters
) {

    /**
     * 复盘总览计数。
     * issueRunCount / healthyRunCount 基于全部 bounded run 的诊断主因独立统计；
     * criticalIssueCount / warningIssueCount 基于诊断严重度统计；reviewedRunCount 为实际生成 runReview 的数量。
     * topIssueCause / topWeakness 在无问题 / 无策略时为 null。generatedAt 由服务层注入（不在聚合器内取系统时间，保证可测）。
     */
    public record Overview(
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

    /**
     * 组合级复盘：headline 按「关键问题 &gt; 显著偏差 &gt; 整体稳定 &gt; 无数据」优先级生成；
     * 各 highlights / suggestedNextActions / limitations 为规则化字符串清单（可为空，不为 null）。
     */
    public record PortfolioReview(
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

    /**
     * 单个重点 run 的复盘记录。
     * primaryCause / severity / confidence 为 K1 诊断枚举名字符串；reviewSummary / suggestedActions 为 Paper-only
     * 排查说明（不含投资动作）；keyFacts 为关键事实摘要；likelyReasons 为可能原因；tags 为分类标签。
     */
    public record RunReview(
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

    /**
     * strategyVersionId 维度复盘（基于 K3 策略评估）。
     * ratingLabel / evaluationConfidence 为 K3 枚举名字符串；compositeScore 为 0~100 Paper 内部启发式分；
     * strengths / weaknesses / warnings / suggestedActions 为规则化清单。
     */
    public record StrategyReview(
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

    /** publishId 维度复盘（字段同 {@link StrategyReview}，附 strategyVersionId 归属）。 */
    public record PublishReview(
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

    /**
     * 问题聚类：按问题类型（clusterKey）确定性聚合受影响的 run / strategy / publish。
     * 桶之间可重叠（同一 run 可同时落入风控拦截与高回撤），与 K1 「按事实独立计数」口径一致；
     * affected* 清单已截断到上限，summary / suggestedAction 为 Paper-only 工程排查说明。
     */
    public record IssueCluster(
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
}
