package com.guidinglight.nexusquant.research.application.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.research.application.paper.PaperStrategyEvaluation.DeviationLevel;
import com.guidinglight.nexusquant.research.application.paper.PaperStrategyEvaluation.EvaluationConfidence;
import com.guidinglight.nexusquant.research.application.paper.PaperStrategyEvaluation.RatingLabel;
import com.guidinglight.nexusquant.research.application.paper.PaperStrategyEvaluation.StrategyEvaluation;
import com.guidinglight.nexusquant.research.domain.publish.BacktestEvaluationView;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/**
 * PaperStrategyEvaluationAssembler 单测：验证 0~100 启发式评分、评级、可信度、Backtest 偏差与排行聚合，
 * 以及缺 backtest / 样本不足 / 数据不足等降级路径与空输入稳定空结构。
 */
class PaperStrategyEvaluationAssemblerTest {

    @Test
    void profitableStrategyShouldRateStrongWithReasonableComposite() {
        var eval = strategyOf(List.of(
                ref("r1", "STOPPED", "sv-good", "pub-good", "110000", "100000", "10000", "0.1", "-0.03", false, 4, 3),
                ref("r2", "STOPPED", "sv-good", "pub-good", "112000", "100000", "12000", "0.12", "-0.02", false, 4, 3),
                ref("r3", "STOPPED", "sv-good", "pub-good", "108000", "100000", "8000", "0.08", "-0.04", false, 4, 3)
        ), Map.of());
        assertEquals(RatingLabel.STRONG_PAPER_PERFORMER, eval.ratingLabel());
        assertTrue(eval.returnScore() >= 60, "returnScore=" + eval.returnScore());
        assertTrue(eval.riskScore() >= 50, "riskScore=" + eval.riskScore());
        assertTrue(eval.compositeScore() >= 70, "composite=" + eval.compositeScore());
        assertEquals(3, eval.winRunCount());
    }

    @Test
    void lossStrategyShouldLowerReturnScore() {
        var loss = strategyOf(List.of(
                ref("l1", "STOPPED", "sv-loss", "pub-loss", "90000", "100000", "-10000", "-0.1", "-0.12", false, 4, 3),
                ref("l2", "STOPPED", "sv-loss", "pub-loss", "88000", "100000", "-12000", "-0.12", "-0.14", false, 4, 3)
        ), Map.of());
        assertTrue(loss.returnScore() < 30, "returnScore=" + loss.returnScore());
        assertEquals(2, loss.lossRunCount());
    }

    @Test
    void highDrawdownStrategyShouldLowerRiskScoreAndRateHighRisk() {
        var eval = strategyOf(List.of(
                ref("d1", "STOPPED", "sv-dd", "pub-dd", "70000", "100000", "-30000", "0.05", "-0.30", false, 4, 3),
                ref("d2", "STOPPED", "sv-dd", "pub-dd", "75000", "100000", "-25000", "0.05", "-0.28", false, 4, 3)
        ), Map.of());
        assertTrue(eval.riskScore() < 60, "riskScore=" + eval.riskScore());
        assertEquals(RatingLabel.HIGH_RISK, eval.ratingLabel());
        assertTrue(eval.warnings().contains("HIGH_DRAWDOWN"));
    }

    @Test
    void noOrderOrderNoFillHeavyStrategyShouldLowerExecutionScoreAndRateExecutionProblem() {
        var eval = strategyOf(List.of(
                // 有 equity（可比，避免 DATA_INSUFFICIENT）但多数无订单 / 有单无成交 → 执行问题。
                ref("e1", "RUNNING", "sv-exec", "pub-exec", "100000", "100000", "0", "0", "0", false, 0, 0),
                ref("e2", "RUNNING", "sv-exec", "pub-exec", "100000", "100000", "0", "0", "0", false, 3, 0),
                ref("e3", "STOPPED", "sv-exec", "pub-exec", "101000", "100000", "1000", "0.01", "-0.01", false, 4, 2)
        ), Map.of());
        assertTrue(eval.executionScore() < 60, "executionScore=" + eval.executionScore());
        assertEquals(RatingLabel.EXECUTION_PROBLEM, eval.ratingLabel());
    }

    @Test
    void sampleTooSmallShouldLowerSampleScoreAndRateSampleInsufficient() {
        var eval = strategyOf(List.of(
                ref("s1", "STOPPED", "sv-small", "pub-small", "110000", "100000", "10000", "0.1", "-0.03", false, 4, 3)
        ), Map.of());
        assertEquals(10, eval.sampleScore());
        assertEquals(RatingLabel.SAMPLE_INSUFFICIENT, eval.ratingLabel());
        assertEquals(EvaluationConfidence.LOW, eval.evaluationConfidence());
    }

    @Test
    void dataInsufficientStrategyShouldRateDataInsufficientWithLowConfidence() {
        var eval = strategyOf(List.of(
                ref("x1", "RUNNING", "sv-di", "pub-di", null, null, null, null, null, false, 0, 0),
                ref("x2", "RUNNING", "sv-di", "pub-di", null, null, null, null, null, false, 0, 0)
        ), Map.of());
        assertEquals(0, eval.comparableRunCount());
        assertEquals(RatingLabel.DATA_INSUFFICIENT, eval.ratingLabel());
        assertEquals(EvaluationConfidence.LOW, eval.evaluationConfidence());
        assertEquals(0, eval.returnScore());
    }

    @Test
    void highBacktestDeviationShouldAddWarningAndLowerDeviationScore() {
        // paper 收益 ~0.1，backtest 收益 0.5 → 偏差 -0.4 → HIGH。
        Map<String, BacktestEvaluationView> bt = Map.of(
                "pub-dev", btView("brn-dev", "0.5", "0.05"));
        var eval = strategyOf(List.of(
                ref("v1", "STOPPED", "sv-dev", "pub-dev", "110000", "100000", "10000", "0.1", "-0.03", false, 4, 3),
                ref("v2", "STOPPED", "sv-dev", "pub-dev", "110000", "100000", "10000", "0.1", "-0.03", false, 4, 3)
        ), bt);
        assertNotNull(eval.backtestDeviation());
        assertEquals(DeviationLevel.HIGH, eval.backtestDeviation().deviationLevel());
        assertTrue(eval.warnings().contains("BACKTEST_PAPER_DEVIATION_HIGH"));
        assertNotNull(eval.backtestDeviationScore());
        assertTrue(eval.backtestDeviationScore() < 50, "btScore=" + eval.backtestDeviationScore());
    }

    @Test
    void missingBacktestShouldNotCrashAndDowngradeConfidence() {
        var eval = strategyOf(List.of(
                ref("m1", "STOPPED", "sv-nb", "pub-nb", "110000", "100000", "10000", "0.1", "-0.03", false, 4, 3),
                ref("m2", "STOPPED", "sv-nb", "pub-nb", "112000", "100000", "12000", "0.12", "-0.02", false, 4, 3),
                ref("m3", "STOPPED", "sv-nb", "pub-nb", "108000", "100000", "8000", "0.08", "-0.04", false, 4, 3),
                ref("m4", "STOPPED", "sv-nb", "pub-nb", "109000", "100000", "9000", "0.09", "-0.03", false, 4, 3),
                ref("m5", "STOPPED", "sv-nb", "pub-nb", "111000", "100000", "11000", "0.11", "-0.03", false, 4, 3)
        ), Map.of());
        assertNull(eval.backtestDeviationScore());
        assertEquals(DeviationLevel.UNAVAILABLE, eval.backtestDeviation().deviationLevel());
        assertTrue(eval.warnings().contains("BACKTEST_DATA_UNAVAILABLE"));
        // 5 个可比 run 但 backtest 缺失 → 不能升到 HIGH（最多 MEDIUM）。
        assertTrue(eval.evaluationConfidence() != EvaluationConfidence.HIGH);
    }

    @Test
    void publishEvaluationsShouldAggregateByPublishId() {
        var evaluation = PaperStrategyEvaluationAssembler.assemble(List.of(
                ref("p1", "STOPPED", "sv-a", "pub-1", "110000", "100000", "10000", "0.1", "-0.03", false, 4, 3),
                ref("p2", "STOPPED", "sv-a", "pub-1", "112000", "100000", "12000", "0.12", "-0.02", false, 4, 3),
                ref("p3", "STOPPED", "sv-a", "pub-2", "108000", "100000", "8000", "0.08", "-0.04", false, 4, 3)
        ), Map.of());
        Map<String, PaperStrategyEvaluation.PublishEvaluation> byPublish = evaluation.publishEvaluations().stream()
                .collect(Collectors.toMap(PaperStrategyEvaluation.PublishEvaluation::publishId, Function.identity()));
        assertEquals(2, byPublish.get("pub-1").runCount());
        assertEquals(1, byPublish.get("pub-2").runCount());
        assertEquals("sv-a", byPublish.get("pub-1").strategyVersionId());
        // strategy 维度聚合：sv-a 共 3 run。
        assertEquals(1, evaluation.strategyEvaluations().size());
        assertEquals(3, evaluation.strategyEvaluations().get(0).runCount());
    }

    @Test
    void rankingsShouldOrderByCompositeAndCollectCategories() {
        var evaluation = PaperStrategyEvaluationAssembler.assemble(List.of(
                ref("g1", "STOPPED", "sv-strong", "pub-s", "112000", "100000", "12000", "0.12", "-0.02", false, 4, 3),
                ref("g2", "STOPPED", "sv-strong", "pub-s", "111000", "100000", "11000", "0.11", "-0.02", false, 4, 3),
                ref("h1", "STOPPED", "sv-risk", "pub-r", "70000", "100000", "-30000", "-0.3", "-0.35", true, 4, 3),
                ref("h2", "STOPPED", "sv-risk", "pub-r", "72000", "100000", "-28000", "-0.28", "-0.33", true, 4, 3),
                ref("z1", "RUNNING", "sv-small", "pub-z", "110000", "100000", "10000", "0.1", "-0.03", false, 4, 3)
        ), Map.of());
        // 复合分降序：strong 在 risk 之前。
        assertTrue(evaluation.rankings().topCompositeStrategies().indexOf("sv-strong")
                < evaluation.rankings().topCompositeStrategies().indexOf("sv-risk"));
        // 高风险 / 样本不足分类命中。
        assertTrue(evaluation.rankings().highRiskStrategies().contains("sv-risk"));
        assertTrue(evaluation.rankings().sampleInsufficientStrategies().contains("sv-small"));
        assertEquals(2, evaluation.overview().profitableStrategyCount()); // sv-strong + sv-small (totalReturn>0)
        assertEquals(1, evaluation.overview().highRiskStrategyCount());
    }

    @Test
    void emptyInputShouldReturnStableEmptyStructure() {
        var evaluation = PaperStrategyEvaluationAssembler.assemble(List.of(), Map.of());
        assertEquals(0, evaluation.overview().strategyCount());
        assertEquals(0, evaluation.overview().evaluatedRunCount());
        assertNull(evaluation.overview().topCompositeScore());
        assertTrue(evaluation.strategyEvaluations().isEmpty());
        assertTrue(evaluation.publishEvaluations().isEmpty());
        assertTrue(evaluation.rankings().topCompositeStrategies().isEmpty());

        var fromNull = PaperStrategyEvaluationAssembler.assemble(null, null);
        assertEquals(0, fromNull.overview().strategyCount());
    }

    @Test
    void safetyScoresMustNotImplyInvestmentAdvice() {
        // ratingLabel 与 warnings 均为内部代码，不含「买入/卖出/投资建议」语义。
        var eval = strategyOf(List.of(
                ref("a1", "STOPPED", "sv-a", "pub-a", "110000", "100000", "10000", "0.1", "-0.03", false, 4, 3),
                ref("a2", "STOPPED", "sv-a", "pub-a", "112000", "100000", "12000", "0.12", "-0.02", false, 4, 3)
        ), Map.of());
        String text = eval.ratingLabel().name() + String.join(",", eval.warnings()) + eval.primaryWeakness();
        for (String banned : List.of("买入", "卖出", "投资建议", "BUY", "SELL")) {
            assertTrue(!text.contains(banned), "评估代码不得含投资动作语义: " + banned);
        }
    }

    // ---- fixtures ----

    private static StrategyEvaluation strategyOf(
            List<PaperPortfolioSummary.RunRef> refs, Map<String, BacktestEvaluationView> bt
    ) {
        return PaperStrategyEvaluationAssembler.assemble(refs, bt).strategyEvaluations().get(0);
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
