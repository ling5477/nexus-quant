package com.guidinglight.nexusquant.research.application.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.research.application.paper.PaperAutoReview.IssueCluster;
import com.guidinglight.nexusquant.research.application.paper.PaperAutoReview.RunReview;
import com.guidinglight.nexusquant.research.application.paper.PaperAutoReview.StrategyReview;
import com.guidinglight.nexusquant.research.domain.publish.BacktestEvaluationView;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * PaperAutoReviewAssembler 单测：以真实 K1 诊断 + K3 评估为输入（同口径 RunRef 派生），验证规则化复盘文案、
 * 严重度 / 置信度、issueCluster 聚合、portfolioReview headline 优先级、空输入稳定空结构，
 * 以及 suggestedActions 不含投资动作语义。
 */
class PaperAutoReviewAssemblerTest {

    private static final Instant FIXED = Instant.parse("2026-06-26T00:00:00Z");

    private static final List<String> BANNED_ACTIONS =
            List.of("买入", "卖出", "加仓", "减仓", "做多", "做空", "实盘", "BUY", "SELL");

    // ---- empty ----

    @Test
    void emptyInputShouldReturnStableEmptyReview() {
        PaperAutoReview review = PaperAutoReviewAssembler.assemble(null, null, FIXED);
        assertEquals(0, review.overview().totalRuns());
        assertEquals(0, review.overview().reviewedRunCount());
        assertNull(review.overview().topIssueCause());
        assertNull(review.overview().topWeakness());
        assertEquals(FIXED, review.overview().generatedAt());
        assertEquals("暂无足够 Paper 事实生成复盘。", review.portfolioReview().headline());
        assertTrue(review.runReviews().isEmpty());
        assertTrue(review.strategyReviews().isEmpty());
        assertTrue(review.issueClusters().isEmpty());
        // limitations 即便空数据也声明 Paper-only 边界。
        assertFalse(review.portfolioReview().limitations().isEmpty());

        PaperAutoReview emptyRefs = reviewOf(List.of(), Map.of());
        assertEquals("暂无足够 Paper 事实生成复盘。", emptyRefs.portfolioReview().headline());
    }

    // ---- run reviews by cause ----

    @Test
    void noOrderRunShouldProduceNoOrderReview() {
        RunReview r = firstRun(reviewOf(List.of(
                ref("r1", "RUNNING", "sv-1", "pub-1", "100000", "100000", "0", "0", "-0.01", false, 0, 0)), Map.of()));
        assertEquals("NO_ORDER", r.primaryCause());
        assertEquals("策略未产生订单，可能由信号未触发、数据不足或 run 尚未进入有效执行阶段导致。", r.reviewSummary());
        assertTrue(r.suggestedActions().contains("检查策略触发条件"));
        assertTrue(r.suggestedActions().contains("检查调度窗口"));
        assertTrue(r.tags().contains("NO_ORDER"));
    }

    @Test
    void orderNoFillRunShouldProduceOrderNoFillReview() {
        RunReview r = firstRun(reviewOf(List.of(
                ref("r1", "RUNNING", "sv-1", "pub-1", "100000", "100000", "0", "0", "-0.01", false, 3, 0)), Map.of()));
        assertEquals("ORDER_NO_FILL", r.primaryCause());
        assertEquals("已有订单但未成交，可能由撮合条件、价格条件或流动性模拟导致。", r.reviewSummary());
        assertTrue(r.suggestedActions().contains("检查撮合参数"));
    }

    @Test
    void filledLossRunShouldProduceFilledLossReview() {
        RunReview r = firstRun(reviewOf(List.of(
                ref("r1", "STOPPED", "sv-1", "pub-1", "98000", "100000", "-2000", "-0.02", "-0.05", false, 4, 2)), Map.of()));
        assertEquals("FILLED_LOSS", r.primaryCause());
        assertEquals("已有成交但当前亏损，需要结合回撤、持仓和出入场条件复核。", r.reviewSummary());
        assertTrue(r.suggestedActions().contains("检查止损规则"));
    }

    @Test
    void riskBlockedRunShouldProduceRiskBlockedClusterAndCriticalReview() {
        PaperAutoReview review = reviewOf(List.of(
                ref("r1", "STOPPED", "sv-1", "pub-1", "100000", "100000", "0", "0", "-0.01", true, 4, 2)), Map.of());
        RunReview r = firstRun(review);
        assertEquals("RISK_BLOCKED", r.primaryCause());
        assertEquals("CRITICAL", r.severity());
        IssueCluster cluster = cluster(review, "RISK_BLOCKED");
        assertNotNull(cluster);
        assertEquals(1, cluster.count());
        assertTrue(cluster.affectedRunIds().contains("r1"));
    }

    @Test
    void dataInsufficientRunShouldHaveConfidenceAndLimitation() {
        PaperAutoReview review = reviewOf(List.of(
                ref("r1", "RUNNING", "sv-1", "pub-1", null, null, null, null, null, false, 0, 0)), Map.of());
        RunReview r = firstRun(review);
        assertEquals("DATA_INSUFFICIENT", r.primaryCause());
        assertEquals("HIGH", r.confidence());
        assertNotNull(cluster(review, "DATA_INSUFFICIENT"));
        assertTrue(review.portfolioReview().limitations().stream()
                .anyMatch(l -> l.contains("不代表 LIVE 或真实交易表现")));
    }

    @Test
    void highDrawdownRunShouldProduceRiskHighlight() {
        PaperAutoReview review = reviewOf(List.of(
                ref("r1", "STOPPED", "sv-1", "pub-1", "110000", "100000", "10000", "0.1", "-0.15", false, 4, 3)), Map.of());
        RunReview r = firstRun(review);
        assertEquals("HIGH_DRAWDOWN", r.primaryCause());
        assertEquals("CRITICAL", r.severity());
        assertTrue(review.portfolioReview().riskHighlights().stream().anyMatch(h -> h.contains("HIGH_DRAWDOWN")));
        assertNotNull(cluster(review, "HIGH_DRAWDOWN"));
    }

    @Test
    void healthyRunShouldProduceHealthyReview() {
        // 3 个健康 run，避免样本不足干扰策略评级，但 run review 仍逐条产出 HEALTHY。
        RunReview r = firstRun(reviewOf(List.of(
                ref("r1", "STOPPED", "sv-1", "pub-1", "110000", "100000", "10000", "0.1", "-0.03", false, 4, 3),
                ref("r2", "STOPPED", "sv-1", "pub-1", "112000", "100000", "12000", "0.12", "-0.02", false, 4, 3),
                ref("r3", "STOPPED", "sv-1", "pub-1", "108000", "100000", "8000", "0.08", "-0.03", false, 4, 3)), Map.of()));
        assertEquals("HEALTHY", r.primaryCause());
        assertEquals("未发现关键异常，继续积累样本。", r.reviewSummary());
    }

    // ---- strategy reviews ----

    @Test
    void strongStrategyShouldProduceStrengths() {
        StrategyReview s = firstStrategy(reviewOf(List.of(
                ref("r1", "STOPPED", "sv-good", "pub-good", "110000", "100000", "10000", "0.1", "-0.03", false, 4, 3),
                ref("r2", "STOPPED", "sv-good", "pub-good", "112000", "100000", "12000", "0.12", "-0.02", false, 4, 3),
                ref("r3", "STOPPED", "sv-good", "pub-good", "108000", "100000", "8000", "0.08", "-0.04", false, 4, 3)), Map.of()));
        assertEquals("STRONG_PAPER_PERFORMER", s.ratingLabel());
        assertFalse(s.strengths().isEmpty());
        assertTrue(s.reviewSummary().contains("不构成投资建议"));
    }

    @Test
    void highRiskStrategyShouldProduceWarningsAndWeaknesses() {
        StrategyReview s = firstStrategy(reviewOf(List.of(
                ref("r1", "STOPPED", "sv-risk", "pub-risk", "70000", "100000", "-30000", "-0.3", "-0.35", false, 4, 3),
                ref("r2", "STOPPED", "sv-risk", "pub-risk", "72000", "100000", "-28000", "-0.28", "-0.33", false, 4, 3)), Map.of()));
        assertEquals("HIGH_RISK", s.ratingLabel());
        assertTrue(s.warnings().contains("HIGH_DRAWDOWN"));
        assertFalse(s.weaknesses().isEmpty());
        assertTrue(s.suggestedActions().contains("检查风控阈值与仓位限制"));
    }

    @Test
    void sampleInsufficientStrategyShouldProduceSuggestedActions() {
        StrategyReview s = firstStrategy(reviewOf(List.of(
                ref("r1", "STOPPED", "sv-small", "pub-small", "110000", "100000", "10000", "0.1", "-0.03", false, 4, 3)), Map.of()));
        assertEquals("SAMPLE_INSUFFICIENT", s.ratingLabel());
        assertTrue(s.suggestedActions().contains("增加 Paper 样本"));
    }

    @Test
    void highBacktestDeviationShouldProduceClusterAndWarning() {
        Map<String, BacktestEvaluationView> bt = Map.of("pub-dev", btView("brn-dev", "0.5", "0.05"));
        PaperAutoReview review = reviewOf(List.of(
                ref("r1", "STOPPED", "sv-dev", "pub-dev", "110000", "100000", "10000", "0.1", "-0.03", false, 4, 3),
                ref("r2", "STOPPED", "sv-dev", "pub-dev", "110000", "100000", "10000", "0.1", "-0.03", false, 4, 3)), bt);
        StrategyReview s = firstStrategy(review);
        assertTrue(s.warnings().contains("BACKTEST_PAPER_DEVIATION_HIGH"));
        IssueCluster cluster = cluster(review, "BACKTEST_DEVIATION_HIGH");
        assertNotNull(cluster);
        assertTrue(cluster.affectedStrategyVersionIds().contains("sv-dev"));
    }

    // ---- clustering / overview ----

    @Test
    void issueClustersShouldAggregateAcrossCauses() {
        PaperAutoReview review = reviewOf(List.of(
                ref("n1", "RUNNING", "sv-a", "pub-a", "100000", "100000", "0", "0", "-0.01", false, 0, 0), // NO_ORDER
                ref("n2", "RUNNING", "sv-a", "pub-a", "100000", "100000", "0", "0", "-0.01", false, 0, 0), // NO_ORDER
                ref("o1", "RUNNING", "sv-b", "pub-b", "100000", "100000", "0", "0", "-0.01", false, 3, 0), // ORDER_NO_FILL
                ref("k1", "STOPPED", "sv-c", "pub-c", "100000", "100000", "0", "0", "-0.01", true, 4, 2)), // RISK_BLOCKED
                Map.of());
        assertEquals(2, cluster(review, "EXECUTION_NO_ORDER").count());
        assertEquals(2, cluster(review, "EXECUTION_NO_ORDER").affectedRunIds().size());
        assertEquals(1, cluster(review, "EXECUTION_ORDER_NO_FILL").count());
        assertEquals(1, cluster(review, "RISK_BLOCKED").count());
        // 严重度优先：RISK_BLOCKED（CRITICAL）排在 NO_ORDER（WARNING）之前。
        List<String> keys = review.issueClusters().stream().map(IssueCluster::clusterKey).toList();
        assertTrue(keys.indexOf("RISK_BLOCKED") < keys.indexOf("EXECUTION_NO_ORDER"));
        assertEquals(4, review.overview().totalRuns());
        assertEquals(4, review.overview().issueRunCount());
        assertNotNull(review.overview().topIssueCause());
    }

    @Test
    void portfolioHeadlineShouldFollowPriority() {
        // criticalIssueCount > 0 → headline 1。
        PaperAutoReview critical = reviewOf(List.of(
                ref("r1", "STOPPED", "sv-1", "pub-1", "100000", "100000", "0", "0", "-0.01", true, 4, 2)), Map.of());
        assertEquals("Paper 组合存在关键执行问题，需优先处理高风险 run。", critical.portfolioReview().headline());

        // 无 critical 但偏差显著 → headline 2。
        Map<String, BacktestEvaluationView> bt = Map.of("pub-dev", btView("brn-dev", "0.5", "0.05"));
        PaperAutoReview deviation = reviewOf(List.of(
                ref("r1", "STOPPED", "sv-dev", "pub-dev", "110000", "100000", "10000", "0.1", "-0.03", false, 4, 3),
                ref("r2", "STOPPED", "sv-dev", "pub-dev", "110000", "100000", "10000", "0.1", "-0.03", false, 4, 3)), bt);
        assertEquals("Paper 与 Backtest 偏差显著，需复核策略稳定性。", deviation.portfolioReview().headline());

        // 无 critical、无偏差 → headline 3（稳定）。
        PaperAutoReview stable = reviewOf(List.of(
                ref("r1", "STOPPED", "sv-1", "pub-1", "110000", "100000", "10000", "0.1", "-0.03", false, 4, 3),
                ref("r2", "STOPPED", "sv-1", "pub-1", "112000", "100000", "12000", "0.12", "-0.02", false, 4, 3),
                ref("r3", "STOPPED", "sv-1", "pub-1", "108000", "100000", "8000", "0.08", "-0.03", false, 4, 3)), Map.of());
        assertEquals("Paper 组合整体运行稳定，但仍需观察样本充足性。", stable.portfolioReview().headline());
    }

    @Test
    void suggestedActionsMustNotContainInvestmentVerbs() {
        Map<String, BacktestEvaluationView> bt = Map.of("pub-dev", btView("brn-dev", "0.5", "0.05"));
        PaperAutoReview review = reviewOf(List.of(
                ref("n1", "RUNNING", "sv-a", "pub-a", "100000", "100000", "0", "0", "-0.01", false, 0, 0),
                ref("k1", "STOPPED", "sv-c", "pub-c", "100000", "100000", "0", "0", "-0.01", true, 4, 2),
                ref("d1", "STOPPED", "sv-dev", "pub-dev", "70000", "100000", "-30000", "-0.3", "-0.35", false, 4, 3),
                ref("d2", "STOPPED", "sv-dev", "pub-dev", "72000", "100000", "-28000", "-0.28", "-0.33", false, 4, 3)), bt);

        List<String> allText = new ArrayList<>();
        review.runReviews().forEach(r -> {
            allText.addAll(r.suggestedActions());
            allText.addAll(r.reviewSummary() != null ? List.of(r.reviewSummary()) : List.of());
        });
        review.strategyReviews().forEach(s -> allText.addAll(s.suggestedActions()));
        review.publishReviews().forEach(p -> allText.addAll(p.suggestedActions()));
        allText.addAll(review.portfolioReview().suggestedNextActions());
        review.issueClusters().forEach(c -> allText.add(c.suggestedAction()));

        String joined = String.join(" | ", allText);
        for (String banned : BANNED_ACTIONS) {
            assertFalse(joined.contains(banned), "suggestedActions 不得含投资动作: " + banned + " in " + joined);
        }
    }

    // ---- fixtures ----

    private static PaperAutoReview reviewOf(
            List<PaperPortfolioSummary.RunRef> refs, Map<String, BacktestEvaluationView> bt
    ) {
        PaperExecutionDiagnostics diag = PaperExecutionDiagnosticsAssembler.assemble(refs);
        PaperStrategyEvaluation eval = PaperStrategyEvaluationAssembler.assemble(refs, bt);
        return PaperAutoReviewAssembler.assemble(diag, eval, FIXED);
    }

    private static RunReview firstRun(PaperAutoReview review) {
        assertFalse(review.runReviews().isEmpty(), "expected at least one run review");
        return review.runReviews().get(0);
    }

    private static StrategyReview firstStrategy(PaperAutoReview review) {
        assertFalse(review.strategyReviews().isEmpty(), "expected at least one strategy review");
        return review.strategyReviews().get(0);
    }

    private static IssueCluster cluster(PaperAutoReview review, String clusterKey) {
        return review.issueClusters().stream()
                .filter(c -> c.clusterKey().equals(clusterKey))
                .findFirst().orElse(null);
    }

    private static PaperPortfolioSummary.RunRef ref(
            String runId, String status, String strategyVersionId, String publishId,
            String currentEquity, String initialEquity, String totalPnl, String totalReturn, String maxDrawdown,
            boolean riskBlocked, int orderCount, int tradeCount
    ) {
        boolean hasFill = tradeCount > 0;
        boolean orderNoFill = !hasFill && orderCount > 0;
        boolean noOrder = !hasFill && orderCount == 0;
        return new PaperPortfolioSummary.RunRef(
                runId, status, "BTC-USDT", strategyVersionId, publishId,
                dec(currentEquity), dec(initialEquity), dec(totalPnl), dec(totalReturn), dec(maxDrawdown),
                riskBlocked, 0, orderCount, tradeCount, noOrder, orderNoFill, hasFill,
                Instant.parse("2026-06-02T00:00:00Z"));
    }

    private static BacktestEvaluationView btView(String backtestRunId, String returnRate, String maxDrawdownRate) {
        return new BacktestEvaluationView(
                "eval-" + backtestRunId, backtestRunId, "SUCCEEDED", Instant.parse("2026-05-31T00:00:00Z"),
                null, null, dec(returnRate), dec(maxDrawdownRate), null, null, null, null, null, null, null);
    }

    private static BigDecimal dec(String value) {
        return value != null ? new BigDecimal(value) : null;
    }
}
