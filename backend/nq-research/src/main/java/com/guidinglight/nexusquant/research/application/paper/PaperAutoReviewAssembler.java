package com.guidinglight.nexusquant.research.application.paper;

import com.guidinglight.nexusquant.research.application.paper.PaperAutoReview.IssueCluster;
import com.guidinglight.nexusquant.research.application.paper.PaperAutoReview.Overview;
import com.guidinglight.nexusquant.research.application.paper.PaperAutoReview.PortfolioReview;
import com.guidinglight.nexusquant.research.application.paper.PaperAutoReview.PublishReview;
import com.guidinglight.nexusquant.research.application.paper.PaperAutoReview.RunReview;
import com.guidinglight.nexusquant.research.application.paper.PaperAutoReview.StrategyReview;
import com.guidinglight.nexusquant.research.application.paper.PaperExecutionDiagnostics.Cause;
import com.guidinglight.nexusquant.research.application.paper.PaperExecutionDiagnostics.RunDiagnostics;
import com.guidinglight.nexusquant.research.application.paper.PaperExecutionDiagnostics.Severity;
import com.guidinglight.nexusquant.research.application.paper.PaperStrategyEvaluation.DeviationLevel;
import com.guidinglight.nexusquant.research.application.paper.PaperStrategyEvaluation.PublishEvaluation;
import com.guidinglight.nexusquant.research.application.paper.PaperStrategyEvaluation.StrategyEvaluation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PaperAutoReviewAssembler —— 纯函数式 Paper 自动复盘聚合器（GateK Batch K4）。
 *
 * 设计要点：
 * 1) 无任何 IO / 仓储 / 外呼 / AI 依赖；输入为 K1 {@link PaperExecutionDiagnostics} 与 K3
 *    {@link PaperStrategyEvaluation} 的只读派生结果，本聚合器只负责<b>规则化摘要与确定性聚类</b>，
 *    不重复计算 run 事实（避免与 K1/K3 口径分叉、也不引入查询放大）。
 * 2) 全部文案由 if/else + enum mapping + 字符串模板生成，<b>不含买入/卖出/加仓/减仓/做多/做空/实盘执行</b>
 *    等投资动作，suggestedActions 一律为工程排查动作。
 * 3) 事实缺失时降级并在 limitations 显式说明，不伪造；空输入返回稳定空结构（无 run / 无策略时
 *    headline 固定为「暂无足够 Paper 事实生成复盘」）。
 */
public final class PaperAutoReviewAssembler {

    /** 重点 run 复盘清单截断上限，避免响应体随 run 数膨胀。 */
    static final int MAX_RUN_REVIEWS = 50;

    /** 单个聚类 affected 清单截断上限。 */
    static final int MAX_AFFECTED = 20;

    /** 组合级 highlights / suggestedNextActions 截断上限。 */
    static final int MAX_HIGHLIGHTS = 8;

    /** 计为「问题」的诊断主因（FAILED / 数据不足 / 风控 / 执行 / 亏损 / 高回撤）。 */
    private static final Set<Cause> ISSUE_CAUSES = EnumSet.of(
            Cause.FAILED_RUN, Cause.DATA_INSUFFICIENT, Cause.RISK_BLOCKED,
            Cause.ORDER_NO_FILL, Cause.NO_ORDER, Cause.FILLED_LOSS, Cause.HIGH_DRAWDOWN);

    private PaperAutoReviewAssembler() {
    }

    /**
     * 由 K1 诊断与 K3 评估规则化归纳出 Paper 自动复盘。
     *
     * @param diagnostics K1 执行诊断只读结果（可为 null，按空处理）
     * @param evaluation  K3 策略评估只读结果（可为 null，按空处理）
     * @param generatedAt 复盘生成时间（由服务层注入，保证聚合器可测、确定性）
     */
    public static PaperAutoReview assemble(
            PaperExecutionDiagnostics diagnostics,
            PaperStrategyEvaluation evaluation,
            Instant generatedAt
    ) {
        List<RunDiagnostics> runs = diagnostics != null && diagnostics.runDiagnostics() != null
                ? diagnostics.runDiagnostics() : List.of();
        List<StrategyEvaluation> strategies = evaluation != null && evaluation.strategyEvaluations() != null
                ? evaluation.strategyEvaluations() : List.of();
        List<PublishEvaluation> publishes = evaluation != null && evaluation.publishEvaluations() != null
                ? evaluation.publishEvaluations() : List.of();

        List<RunReview> runReviews = buildRunReviews(runs);
        List<StrategyReview> strategyReviews = buildStrategyReviews(strategies);
        List<PublishReview> publishReviews = buildPublishReviews(publishes);
        List<IssueCluster> issueClusters = buildIssueClusters(runs, strategies);

        Overview overview = buildOverview(runs, strategies, publishes, runReviews, generatedAt);
        PortfolioReview portfolioReview = buildPortfolioReview(
                diagnostics, evaluation, runs, strategies, issueClusters, overview);

        return new PaperAutoReview(
                overview, portfolioReview,
                List.copyOf(runReviews), List.copyOf(strategyReviews),
                List.copyOf(publishReviews), List.copyOf(issueClusters));
    }

    // ---- Overview ----

    private static Overview buildOverview(
            List<RunDiagnostics> runs, List<StrategyEvaluation> strategies, List<PublishEvaluation> publishes,
            List<RunReview> runReviews, Instant generatedAt
    ) {
        int issue = 0;
        int healthy = 0;
        int critical = 0;
        int warning = 0;
        Map<Cause, Integer> issueCauseCounts = new LinkedHashMap<>();
        for (RunDiagnostics run : runs) {
            Cause cause = run.primaryCause();
            if (ISSUE_CAUSES.contains(cause)) {
                issue++;
                issueCauseCounts.merge(cause, 1, Integer::sum);
            } else if (cause == Cause.HEALTHY) {
                healthy++;
            }
            if (run.severity() == Severity.CRITICAL) {
                critical++;
            } else if (run.severity() == Severity.WARNING) {
                warning++;
            }
        }

        String topIssueCause = issueCauseCounts.entrySet().stream()
                // 频次降序，再按主因优先级（枚举序）升序：最频繁且最紧急者优先。
                .sorted(Comparator
                        .comparingInt((Map.Entry<Cause, Integer> e) -> e.getValue()).reversed()
                        .thenComparingInt(e -> e.getKey().ordinal()))
                .map(e -> e.getKey().name())
                .findFirst().orElse(null);
        String topWeakness = topWeakness(strategies);

        return new Overview(
                runs.size(), runReviews.size(), issue, healthy, critical, warning,
                strategies.size(), publishes.size(), topIssueCause, topWeakness, generatedAt);
    }

    private static String topWeakness(List<StrategyEvaluation> strategies) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (StrategyEvaluation s : strategies) {
            if (s.primaryWeakness() != null) {
                counts.merge(s.primaryWeakness(), 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey).orElse(null);
    }

    // ---- Run reviews ----

    private static List<RunReview> buildRunReviews(List<RunDiagnostics> runs) {
        List<RunReview> reviews = new ArrayList<>();
        for (RunDiagnostics run : runs) {
            // 只复盘「有结论」的 run：问题 run 与健康 run；运行中未出结果 / 未归因 run 不单列。
            Cause cause = run.primaryCause();
            if (!ISSUE_CAUSES.contains(cause) && cause != Cause.HEALTHY) {
                continue;
            }
            reviews.add(toRunReview(run));
        }
        // 排序：严重度优先（CRITICAL → WARNING → INFO），同级按主因优先级，再按收益率升序（最差在前）。
        reviews.sort(Comparator
                .comparingInt((RunReview r) -> severityRank(r.severity()))
                .thenComparing(r -> r.totalReturn() == null ? BigDecimal.ZERO : r.totalReturn()));
        if (reviews.size() > MAX_RUN_REVIEWS) {
            return new ArrayList<>(reviews.subList(0, MAX_RUN_REVIEWS));
        }
        return reviews;
    }

    private static RunReview toRunReview(RunDiagnostics run) {
        Cause cause = run.primaryCause();
        List<String> keyFacts = new ArrayList<>();
        keyFacts.add("状态=" + run.status());
        keyFacts.add("订单数=" + run.orderCount());
        keyFacts.add("成交数=" + run.tradeCount());
        keyFacts.add("收益率=" + ratioText(run.totalReturn()));
        keyFacts.add("最大回撤=" + ratioText(run.maxDrawdown()));
        keyFacts.add("风控拦截=" + (run.riskBlocked() ? "是" : "否"));

        List<String> reasons = new ArrayList<>(causeReasons(cause));
        if (run.secondaryCauses() != null && !run.secondaryCauses().isEmpty()) {
            List<String> names = run.secondaryCauses().stream().map(Cause::name).toList();
            reasons.add("次要原因: " + String.join(", ", names));
        }

        List<String> tags = new ArrayList<>();
        tags.add(causeTag(cause));
        tags.add(run.severity().name());
        if (run.riskBlocked() && !tags.contains("RISK_BLOCKED")) {
            tags.add("RISK_BLOCKED");
        }

        return new RunReview(
                run.paperRunId(), run.strategyVersionId(), run.publishId(), run.status(),
                cause.name(), run.severity().name(), run.causeConfidence().name(),
                run.totalPnl(), run.totalReturn(), run.maxDrawdown(),
                causeHeadline(cause), causeSummary(cause),
                List.copyOf(keyFacts), List.copyOf(reasons), List.copyOf(causeActions(cause)), List.copyOf(tags));
    }

    // ---- Strategy / Publish reviews ----

    private static List<StrategyReview> buildStrategyReviews(List<StrategyEvaluation> strategies) {
        List<StrategyReview> reviews = new ArrayList<>(strategies.size());
        for (StrategyEvaluation s : strategies) {
            String rating = s.ratingLabel().name();
            reviews.add(new StrategyReview(
                    s.strategyVersionId(), rating, s.compositeScore(), s.evaluationConfidence().name(),
                    s.primaryWeakness(), ratingHeadline(rating), ratingSummary(rating),
                    List.copyOf(strengths(s.returnScore(), s.riskScore(), s.executionScore(), s.sampleScore(), s.backtestDeviationScore())),
                    List.copyOf(weaknesses(s.primaryWeakness(), s.returnScore(), s.riskScore(), s.executionScore(), s.sampleScore(), s.backtestDeviationScore())),
                    s.warnings() != null ? List.copyOf(s.warnings()) : List.of(),
                    List.copyOf(ratingActions(rating))));
        }
        return reviews;
    }

    private static List<PublishReview> buildPublishReviews(List<PublishEvaluation> publishes) {
        List<PublishReview> reviews = new ArrayList<>(publishes.size());
        for (PublishEvaluation p : publishes) {
            String rating = p.ratingLabel().name();
            // K3 PublishEvaluation 未携带 primaryWeakness，按与 K3 一致的「最低分维度」规则在此派生。
            String primaryWeakness = lowestScoreDimension(
                    p.returnScore(), p.riskScore(), p.executionScore(), p.sampleScore(), p.backtestDeviationScore());
            reviews.add(new PublishReview(
                    p.publishId(), p.strategyVersionId(), rating, p.compositeScore(), p.evaluationConfidence().name(),
                    primaryWeakness, ratingHeadline(rating), ratingSummary(rating),
                    List.copyOf(strengths(p.returnScore(), p.riskScore(), p.executionScore(), p.sampleScore(), p.backtestDeviationScore())),
                    List.copyOf(weaknesses(primaryWeakness, p.returnScore(), p.riskScore(), p.executionScore(), p.sampleScore(), p.backtestDeviationScore())),
                    p.warnings() != null ? List.copyOf(p.warnings()) : List.of(),
                    List.copyOf(ratingActions(rating))));
        }
        return reviews;
    }

    private static List<String> strengths(int returnScore, int riskScore, int executionScore, int sampleScore, Integer btScore) {
        List<String> strengths = new ArrayList<>();
        if (returnScore >= 70) {
            strengths.add("Paper 收益评分较高（returnScore=" + returnScore + "）");
        }
        if (riskScore >= 70) {
            strengths.add("风险评分较高（riskScore=" + riskScore + "）");
        }
        if (executionScore >= 70) {
            strengths.add("执行评分较高（executionScore=" + executionScore + "）");
        }
        if (sampleScore >= 70) {
            strengths.add("样本评分较高（sampleScore=" + sampleScore + "）");
        }
        if (btScore != null && btScore >= 70) {
            strengths.add("Backtest 偏差评分较高（backtestDeviationScore=" + btScore + "）");
        }
        return strengths;
    }

    private static List<String> weaknesses(
            String primaryWeakness, int returnScore, int riskScore, int executionScore, int sampleScore, Integer btScore
    ) {
        List<String> weaknesses = new ArrayList<>();
        if (primaryWeakness != null) {
            weaknesses.add("主要短板: " + weaknessText(primaryWeakness));
        }
        addLowScore(weaknesses, "RETURN", returnScore, primaryWeakness);
        addLowScore(weaknesses, "RISK", riskScore, primaryWeakness);
        addLowScore(weaknesses, "EXECUTION", executionScore, primaryWeakness);
        addLowScore(weaknesses, "SAMPLE", sampleScore, primaryWeakness);
        if (btScore != null) {
            addLowScore(weaknesses, "BACKTEST_DEVIATION", btScore, primaryWeakness);
        }
        return weaknesses;
    }

    private static void addLowScore(List<String> weaknesses, String dim, int score, String primaryWeakness) {
        // 低分维度补充为短板，但主要短板维度已单列，避免重复。
        if (score < 50 && !dim.equals(primaryWeakness)) {
            weaknesses.add(weaknessText(dim) + "（得分 " + score + "）");
        }
    }

    // ---- Issue clusters ----

    private static List<IssueCluster> buildIssueClusters(
            List<RunDiagnostics> runs, List<StrategyEvaluation> strategies
    ) {
        // 各聚类的累加器（桶可重叠：同一 run 可同时落入风控/高回撤等多个类别）。
        Map<String, ClusterAcc> accs = new LinkedHashMap<>();
        for (String key : CLUSTER_ORDER) {
            accs.put(key, new ClusterAcc());
        }

        for (RunDiagnostics run : runs) {
            boolean noOrder = run.orderCount() == 0 && run.tradeCount() == 0;
            boolean orderNoFill = run.orderCount() > 0 && run.tradeCount() == 0;
            boolean filledLoss = run.tradeCount() > 0 && run.totalPnl() != null && run.totalPnl().signum() < 0;
            boolean dataInsufficient = run.currentEquity() == null || run.initialEquity() == null;
            boolean highDrawdown = run.maxDrawdown() != null
                    && run.maxDrawdown().compareTo(PaperExecutionDiagnosticsAssembler.HIGH_DRAWDOWN_THRESHOLD) <= 0;
            boolean failed = "FAILED".equals(run.status());

            if (noOrder) {
                accs.get("EXECUTION_NO_ORDER").add(run);
            }
            if (orderNoFill) {
                accs.get("EXECUTION_ORDER_NO_FILL").add(run);
            }
            if (filledLoss) {
                accs.get("EXECUTION_FILLED_LOSS").add(run);
            }
            if (run.riskBlocked()) {
                accs.get("RISK_BLOCKED").add(run);
            }
            if (dataInsufficient) {
                accs.get("DATA_INSUFFICIENT").add(run);
            }
            if (highDrawdown) {
                accs.get("HIGH_DRAWDOWN").add(run);
            }
            if (failed) {
                accs.get("FAILED_RUN").add(run);
            }
        }

        for (StrategyEvaluation s : strategies) {
            if (s.backtestDeviation() != null && s.backtestDeviation().deviationLevel() == DeviationLevel.HIGH) {
                accs.get("BACKTEST_DEVIATION_HIGH").addStrategy(s.strategyVersionId());
            }
            if (s.ratingLabel() == PaperStrategyEvaluation.RatingLabel.SAMPLE_INSUFFICIENT) {
                accs.get("SAMPLE_INSUFFICIENT").addStrategy(s.strategyVersionId());
            }
        }

        List<IssueCluster> clusters = new ArrayList<>();
        for (String key : CLUSTER_ORDER) {
            ClusterAcc acc = accs.get(key);
            if (acc.count == 0) {
                continue;
            }
            ClusterMeta meta = CLUSTER_META.get(key);
            clusters.add(new IssueCluster(
                    key, meta.cause, meta.severity, acc.count,
                    capped(acc.runIds), capped(acc.strategyIds), capped(acc.publishIds),
                    meta.summary, meta.suggestedAction));
        }
        // 输出顺序：严重度优先（CRITICAL → WARNING → INFO），同级按 count 降序。
        clusters.sort(Comparator
                .comparingInt((IssueCluster c) -> severityRank(c.severity()))
                .thenComparing(Comparator.comparingInt(IssueCluster::count).reversed()));
        return clusters;
    }

    /** 聚类累加器：去重收集受影响 run / strategy / publish。count 以「命中事实次数」计（run 维度即 run 数）。 */
    private static final class ClusterAcc {
        int count;
        final Set<String> runIds = new LinkedHashSet<>();
        final Set<String> strategyIds = new LinkedHashSet<>();
        final Set<String> publishIds = new LinkedHashSet<>();

        void add(RunDiagnostics run) {
            count++;
            if (run.paperRunId() != null) {
                runIds.add(run.paperRunId());
            }
            if (run.strategyVersionId() != null && !run.strategyVersionId().isBlank()) {
                strategyIds.add(run.strategyVersionId());
            }
            if (run.publishId() != null && !run.publishId().isBlank()) {
                publishIds.add(run.publishId());
            }
        }

        void addStrategy(String strategyVersionId) {
            count++;
            if (strategyVersionId != null && !strategyVersionId.isBlank()) {
                strategyIds.add(strategyVersionId);
            }
        }
    }

    private static List<String> capped(Set<String> values) {
        if (values.size() <= MAX_AFFECTED) {
            return List.copyOf(values);
        }
        return values.stream().limit(MAX_AFFECTED).toList();
    }

    // ---- Portfolio review ----

    private static PortfolioReview buildPortfolioReview(
            PaperExecutionDiagnostics diagnostics, PaperStrategyEvaluation evaluation,
            List<RunDiagnostics> runs, List<StrategyEvaluation> strategies,
            List<IssueCluster> issueClusters, Overview overview
    ) {
        List<String> limitations = baseLimitations(overview);

        // 无 run 且无策略：稳定空复盘。
        if (runs.isEmpty() && strategies.isEmpty()) {
            return new PortfolioReview(
                    "暂无足够 Paper 事实生成复盘。",
                    "当前没有可用的 bounded Paper run 与策略评估事实，无法生成自动复盘。",
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.copyOf(limitations));
        }

        int highDeviation = evaluation != null && evaluation.overview() != null
                ? evaluation.overview().backtestDeviationStrategyCount() : 0;

        String headline;
        if (overview.criticalIssueCount() > 0) {
            headline = "Paper 组合存在关键执行问题，需优先处理高风险 run。";
        } else if (highDeviation > 0) {
            headline = "Paper 与 Backtest 偏差显著，需复核策略稳定性。";
        } else {
            headline = "Paper 组合整体运行稳定，但仍需观察样本充足性。";
        }

        String summary = String.format(
                "共 %d 个 bounded Paper run，其中 %d 个存在问题、%d 个健康；关键问题 run %d 个、警告 run %d 个；"
                        + "已评估策略 %d 个、发布 %d 个。结论为规则化 Paper 复盘，不构成投资建议。",
                overview.totalRuns(), overview.issueRunCount(), overview.healthyRunCount(),
                overview.criticalIssueCount(), overview.warningIssueCount(),
                overview.strategyReviewedCount(), overview.publishReviewedCount());

        List<String> keyFindings = new ArrayList<>();
        keyFindings.add(String.format("共 %d 个 Paper run：问题 %d 个、健康 %d 个。",
                overview.totalRuns(), overview.issueRunCount(), overview.healthyRunCount()));
        if (overview.criticalIssueCount() > 0) {
            keyFindings.add("关键问题 run " + overview.criticalIssueCount() + " 个，建议优先排查。");
        }
        if (overview.topIssueCause() != null) {
            keyFindings.add("最集中问题类型: " + overview.topIssueCause() + "。");
        }
        if (overview.topWeakness() != null) {
            keyFindings.add("策略最常见短板: " + weaknessText(overview.topWeakness()) + "。");
        }

        List<String> riskHighlights = clusterHighlights(issueClusters,
                List.of("RISK_BLOCKED", "HIGH_DRAWDOWN", "FAILED_RUN"));
        List<String> executionHighlights = clusterHighlights(issueClusters,
                List.of("EXECUTION_NO_ORDER", "EXECUTION_ORDER_NO_FILL", "EXECUTION_FILLED_LOSS", "DATA_INSUFFICIENT"));
        List<String> strategyHighlights = strategyHighlights(evaluation);
        List<String> backtestDeviationHighlights = backtestHighlights(evaluation, highDeviation);
        List<String> suggestedNextActions = suggestedNextActions(issueClusters, overview);

        return new PortfolioReview(
                headline, summary, cap(keyFindings), cap(riskHighlights), cap(executionHighlights),
                cap(strategyHighlights), cap(backtestDeviationHighlights), cap(suggestedNextActions),
                List.copyOf(limitations));
    }

    private static List<String> baseLimitations(Overview overview) {
        List<String> limitations = new ArrayList<>();
        limitations.add("结论基于 SIM/Paper 模拟执行事实，不代表 LIVE 或真实交易表现。");
        limitations.add("复盘为规则化启发式结果，非真实投资评级、不构成投资建议。");
        // 存在数据不足 run 时显式提示置信度下降。
        if (overview.totalRuns() > 0 && overview.reviewedRunCount() < overview.issueRunCount() + overview.healthyRunCount()) {
            limitations.add("重点 run 清单已截断，完整明细见 runReviews 与 issueClusters。");
        }
        return limitations;
    }

    private static List<String> clusterHighlights(List<IssueCluster> clusters, List<String> keys) {
        List<String> highlights = new ArrayList<>();
        for (IssueCluster c : clusters) {
            if (keys.contains(c.clusterKey())) {
                highlights.add(c.clusterKey() + ": " + c.count() + " 项 —— " + c.summary());
            }
        }
        return highlights;
    }

    private static List<String> strategyHighlights(PaperStrategyEvaluation evaluation) {
        List<String> highlights = new ArrayList<>();
        if (evaluation == null || evaluation.overview() == null) {
            return highlights;
        }
        PaperStrategyEvaluation.Overview o = evaluation.overview();
        int strong = (int) evaluation.strategyEvaluations().stream()
                .filter(s -> s.ratingLabel() == PaperStrategyEvaluation.RatingLabel.STRONG_PAPER_PERFORMER).count();
        if (strong > 0) {
            highlights.add("Paper 内部表现较强策略 " + strong + " 个（非投资推荐）。");
        }
        if (o.highRiskStrategyCount() > 0) {
            highlights.add("高风险策略 " + o.highRiskStrategyCount() + " 个，需复核风险来源。");
        }
        if (o.sampleInsufficientStrategyCount() > 0) {
            highlights.add("样本不足策略 " + o.sampleInsufficientStrategyCount() + " 个，需增加 Paper 样本。");
        }
        return highlights;
    }

    private static List<String> backtestHighlights(PaperStrategyEvaluation evaluation, int highDeviation) {
        List<String> highlights = new ArrayList<>();
        if (evaluation == null) {
            return highlights;
        }
        if (highDeviation > 0) {
            highlights.add(highDeviation + " 个策略版本 Paper 与 Backtest 偏差显著（HIGH），需重点复核策略稳定性。");
        } else {
            highlights.add("未发现显著 Paper/Backtest 偏差（或缺少可比 Backtest 数据）。");
        }
        return highlights;
    }

    private static List<String> suggestedNextActions(List<IssueCluster> clusters, Overview overview) {
        List<String> actions = new ArrayList<>();
        // 按聚类输出顺序（严重度优先）取各类工程排查动作，去重。
        for (IssueCluster c : clusters) {
            if (!actions.contains(c.suggestedAction())) {
                actions.add(c.suggestedAction());
            }
        }
        if (actions.isEmpty()) {
            actions.add("继续积累 Paper 样本以提升评估置信度。");
            actions.add("持续观察执行与风险指标，复核 Paper 与 Backtest 偏差。");
        }
        return actions;
    }

    private static List<String> cap(List<String> values) {
        if (values.size() <= MAX_HIGHLIGHTS) {
            return List.copyOf(values);
        }
        return List.copyOf(values.subList(0, MAX_HIGHLIGHTS));
    }

    // ---- 规则化文案映射（Paper-only，工程排查动作，不含投资动作）----

    private static String causeHeadline(Cause cause) {
        return switch (cause) {
            case NO_ORDER -> "无订单：策略未产生任何订单";
            case ORDER_NO_FILL -> "有订单无成交：订单未撮合成交";
            case FILLED_LOSS -> "成交亏损：已成交但当前为亏损";
            case RISK_BLOCKED -> "风控拦截：执行被风控阻断或判为高风险";
            case DATA_INSUFFICIENT -> "数据不足：复盘置信度下降";
            case HIGH_DRAWDOWN -> "高回撤：回撤超过首版高回撤阈值";
            case FAILED_RUN -> "异常终态：执行链路失败";
            case HEALTHY -> "健康：未发现关键异常";
            case RUNNING_NO_RESULT -> "运行中：结果尚未形成";
            case UNKNOWN -> "未归因：事实不足以判断";
        };
    }

    private static String causeSummary(Cause cause) {
        return switch (cause) {
            case NO_ORDER -> "策略未产生订单，可能由信号未触发、数据不足或 run 尚未进入有效执行阶段导致。";
            case ORDER_NO_FILL -> "已有订单但未成交，可能由撮合条件、价格条件或流动性模拟导致。";
            case FILLED_LOSS -> "已有成交但当前亏损，需要结合回撤、持仓和出入场条件复核。";
            case RISK_BLOCKED -> "风控阻断或高风险结果出现。";
            case DATA_INSUFFICIENT -> "数据不足导致复盘置信度下降。";
            case HIGH_DRAWDOWN -> "回撤超过首版高回撤阈值。";
            case FAILED_RUN -> "Run 处于 FAILED 终态，执行链路异常终止。";
            case HEALTHY -> "未发现关键异常，继续积累样本。";
            case RUNNING_NO_RESULT -> "Run 仍在运行但暂无足够订单/成交/权益事实，结果尚未形成。";
            case UNKNOWN -> "现有事实不足以归因，需要进一步检查。";
        };
    }

    private static List<String> causeReasons(Cause cause) {
        return switch (cause) {
            case NO_ORDER -> List.of("策略触发条件可能未满足", "行情/输入数据覆盖可能不足", "run 可能尚未进入有效执行阶段");
            case ORDER_NO_FILL -> List.of("订单价格可能偏离可成交区间", "撮合参数可能限制成交", "流动性模拟可能不足");
            case FILLED_LOSS -> List.of("出入场条件可能不利", "止损规则可能缺失或过宽", "信号延迟可能影响成交价格");
            case RISK_BLOCKED -> List.of("风控规则可能触发", "仓位可能超限", "风险阈值可能被突破");
            case DATA_INSUFFICIENT -> List.of("缺少 equity / 初始资金事实", "订单 / 成交 / 日报事实可能缺失");
            case HIGH_DRAWDOWN -> List.of("风险暴露可能过高", "策略参数可能过于激进", "止损 / 资金管理可能不足");
            case FAILED_RUN -> List.of("执行步骤可能异常中断", "依赖事实 / 数据可能缺失");
            case HEALTHY, RUNNING_NO_RESULT, UNKNOWN -> List.of();
        };
    }

    /** 工程排查动作（仅指向「检查/补齐/复核哪些 Paper 事实」，不含买卖方向，不构成投资建议）。 */
    private static List<String> causeActions(Cause cause) {
        return switch (cause) {
            case NO_ORDER -> List.of("检查策略触发条件", "检查行情数据覆盖", "检查调度窗口");
            case ORDER_NO_FILL -> List.of("检查订单价格", "检查撮合参数", "检查市场数据与成交模拟规则");
            case FILLED_LOSS -> List.of("检查亏损交易", "检查止损规则", "检查持仓时长与信号延迟");
            case RISK_BLOCKED -> List.of("检查风控规则", "检查仓位限制", "检查风险阈值");
            case DATA_INSUFFICIENT -> List.of("补齐 equity、order、trade、daily report 或 backtest 事实");
            case HIGH_DRAWDOWN -> List.of("检查风险暴露", "检查策略参数", "检查止损与资金管理");
            case FAILED_RUN -> List.of("检查异常停机/告警/恢复事件", "定位失败步骤后再决定是否重试");
            case HEALTHY -> List.of("继续积累 Paper 样本");
            case RUNNING_NO_RESULT -> List.of("稍后再观察执行器心跳与数据");
            case UNKNOWN -> List.of("检查该 run 的订单/成交/风控/权益事实完整性");
        };
    }

    private static String causeTag(Cause cause) {
        return switch (cause) {
            case NO_ORDER -> "NO_ORDER";
            case ORDER_NO_FILL -> "ORDER_NO_FILL";
            case FILLED_LOSS -> "FILLED_LOSS";
            case RISK_BLOCKED -> "RISK_BLOCKED";
            case DATA_INSUFFICIENT -> "DATA_INSUFFICIENT";
            case HIGH_DRAWDOWN -> "HIGH_DRAWDOWN";
            case FAILED_RUN -> "FAILED_RUN";
            case HEALTHY -> "HEALTHY";
            case RUNNING_NO_RESULT -> "RUNNING";
            case UNKNOWN -> "UNKNOWN";
        };
    }

    private static String ratingHeadline(String rating) {
        return switch (rating) {
            case "STRONG_PAPER_PERFORMER" -> "Paper 内部表现较强（非投资推荐）";
            case "WATCHLIST" -> "观察中：需继续积累 Paper 样本";
            case "HIGH_RISK" -> "高风险：存在回撤/风控/失败问题";
            case "SAMPLE_INSUFFICIENT" -> "样本不足：评估置信度低";
            case "DATA_INSUFFICIENT" -> "数据不足：缺少可比事实";
            case "EXECUTION_PROBLEM" -> "执行问题：无订单/有单无成交/成交质量问题";
            default -> "未归因";
        };
    }

    private static String ratingSummary(String rating) {
        return switch (rating) {
            case "STRONG_PAPER_PERFORMER" ->
                    "该策略版本在 Paper 模拟内部表现较强，但这是 Paper 内部启发式评估，不代表真实交易表现，也不构成投资建议。";
            case "WATCHLIST" -> "该策略版本表现中性，建议继续观察并积累更多 Paper 样本后再评估。";
            case "HIGH_RISK" -> "该策略版本存在较高回撤、风控拦截或失败终态等风险信号，需重点复核风险来源。";
            case "SAMPLE_INSUFFICIENT" -> "该策略版本可比 Paper 样本不足，评估置信度低，需增加 Paper 样本后再评估。";
            case "DATA_INSUFFICIENT" -> "该策略版本缺少可比 equity / 收益事实，暂无法形成可信评估。";
            case "EXECUTION_PROBLEM" -> "该策略版本存在无订单、有订单无成交或成交质量问题，需优先排查执行链路。";
            default -> "现有事实不足以评估该策略版本。";
        };
    }

    private static List<String> ratingActions(String rating) {
        return switch (rating) {
            case "STRONG_PAPER_PERFORMER" -> List.of("继续积累 Paper 样本以提升评估置信度", "持续复核 Paper 与 Backtest 偏差");
            case "WATCHLIST" -> List.of("继续观察并增加 Paper 样本", "复核执行与风险指标");
            case "HIGH_RISK" -> List.of("检查风控阈值与仓位限制", "复核回撤来源与策略参数");
            case "SAMPLE_INSUFFICIENT" -> List.of("增加 Paper 样本", "补齐 equity / 成交事实");
            case "DATA_INSUFFICIENT" -> List.of("补齐 equity、order、trade、daily report 事实");
            case "EXECUTION_PROBLEM" -> List.of("检查策略触发条件与撮合参数", "复核无订单 / 有单无成交 run");
            default -> List.of("检查该策略版本的 run / 事实完整性");
        };
    }

    private static String weaknessText(String dim) {
        return switch (dim) {
            case "RETURN" -> "收益评分偏低";
            case "RISK" -> "风险评分偏低";
            case "EXECUTION" -> "执行评分偏低";
            case "SAMPLE" -> "样本不足";
            case "BACKTEST_DEVIATION" -> "Paper 与 Backtest 偏差偏大";
            default -> dim;
        };
    }

    /** 与 K3 一致的「最低分维度」短板规则（缺失的 backtest 维度不参与）。 */
    private static String lowestScoreDimension(int returnScore, int riskScore, int executionScore, int sampleScore, Integer btScore) {
        String weakest = "RETURN";
        int lowest = returnScore;
        if (riskScore < lowest) {
            lowest = riskScore;
            weakest = "RISK";
        }
        if (executionScore < lowest) {
            lowest = executionScore;
            weakest = "EXECUTION";
        }
        if (sampleScore < lowest) {
            lowest = sampleScore;
            weakest = "SAMPLE";
        }
        if (btScore != null && btScore < lowest) {
            weakest = "BACKTEST_DEVIATION";
        }
        return weakest;
    }

    private static int severityRank(String severity) {
        return switch (severity) {
            case "CRITICAL" -> 0;
            case "WARNING" -> 1;
            default -> 2;
        };
    }

    private static String ratioText(BigDecimal ratio) {
        return ratio != null ? ratio.toPlainString() : "数据不足";
    }

    // ---- 聚类元数据 ----

    /** 聚类输出/构建顺序（与 K4 规则要求的至少 9 类一致）。 */
    private static final List<String> CLUSTER_ORDER = List.of(
            "FAILED_RUN", "RISK_BLOCKED", "HIGH_DRAWDOWN",
            "EXECUTION_ORDER_NO_FILL", "EXECUTION_NO_ORDER", "EXECUTION_FILLED_LOSS",
            "DATA_INSUFFICIENT", "BACKTEST_DEVIATION_HIGH", "SAMPLE_INSUFFICIENT");

    private record ClusterMeta(String cause, String severity, String summary, String suggestedAction) {}

    private static final Map<String, ClusterMeta> CLUSTER_META = buildClusterMeta();

    private static Map<String, ClusterMeta> buildClusterMeta() {
        Map<String, ClusterMeta> meta = new LinkedHashMap<>();
        meta.put("FAILED_RUN", new ClusterMeta("FAILED_RUN", "CRITICAL",
                "多个 Paper run 处于 FAILED 终态。", "检查异常停机/告警/恢复事件并定位失败步骤。"));
        meta.put("RISK_BLOCKED", new ClusterMeta("RISK_BLOCKED", "CRITICAL",
                "多个 Paper run 被风控阻断或判为高风险。", "检查风控规则、仓位限制与风险阈值。"));
        meta.put("HIGH_DRAWDOWN", new ClusterMeta("HIGH_DRAWDOWN", "CRITICAL",
                "多个 Paper run 回撤达到/超过首版高回撤阈值。", "检查风险暴露、策略参数与止损/资金管理。"));
        meta.put("EXECUTION_ORDER_NO_FILL", new ClusterMeta("ORDER_NO_FILL", "WARNING",
                "多个 Paper run 有订单但未成交。", "检查订单价格、撮合参数与成交模拟规则。"));
        meta.put("EXECUTION_NO_ORDER", new ClusterMeta("NO_ORDER", "WARNING",
                "多个 Paper run 未产生任何订单，可能是信号未触发或数据不足。", "检查策略触发条件、行情数据覆盖与调度窗口。"));
        meta.put("EXECUTION_FILLED_LOSS", new ClusterMeta("FILLED_LOSS", "WARNING",
                "多个 Paper run 已成交但当前亏损。", "检查亏损交易、止损规则与持仓时长。"));
        meta.put("DATA_INSUFFICIENT", new ClusterMeta("DATA_INSUFFICIENT", "WARNING",
                "多个 Paper run 缺少可比事实，复盘置信度下降。", "补齐 equity、order、trade、daily report 或 backtest 事实。"));
        meta.put("BACKTEST_DEVIATION_HIGH", new ClusterMeta("BACKTEST_DEVIATION_HIGH", "WARNING",
                "多个策略版本 Paper 与 Backtest 偏差显著。", "复核策略稳定性与 Paper/Backtest 偏差来源。"));
        meta.put("SAMPLE_INSUFFICIENT", new ClusterMeta("SAMPLE_INSUFFICIENT", "INFO",
                "多个策略版本可比 Paper 样本不足。", "增加 Paper 样本后再评估。"));
        return meta;
    }
}
