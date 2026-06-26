package com.guidinglight.nexusquant.research.application.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.research.domain.BacktestPublishRecord;
import com.guidinglight.nexusquant.research.domain.PublishStatus;
import com.guidinglight.nexusquant.research.domain.paper.EquityCurveSnapshot;
import com.guidinglight.nexusquant.research.domain.paper.PaperOrderStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingOrder;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRun;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRunStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingTrade;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PaperAutoReviewService 集成口径单测：复用 {@link PaperPortfolioServiceTest} 的 counting 只读仓储装配 K1 诊断与
 * K3 评估服务，验证：
 * 1) 自动复盘复用诊断 / 评估的批量读取——不出现逐 run 读放大。
 * 2) 无 run 时返回稳定空结构且不触达 backtest 查询。
 * 3) 有 run 但 publish / backtest 缺失时不崩，仍产出复盘。
 */
class PaperAutoReviewServiceTest {

    private PaperTradingRunServiceTest.InMemoryRunRepo runRepo;
    private PaperPortfolioServiceTest.CountingEquityRepo equityRepo;
    private PaperPortfolioServiceTest.CountingDailyReportRepo reportRepo;
    private PaperPortfolioServiceTest.CountingRiskRepo riskRepo;
    private PaperPortfolioServiceTest.CountingAlertRepo alertRepo;
    private PaperPortfolioServiceTest.CountingOrderRepo orderRepo;
    private PaperPortfolioServiceTest.CountingTradeRepo tradeRepo;
    private PaperTradingRunServiceTest.InMemoryPublishRepo publishRepo;
    private PaperStrategyEvaluationServiceTest.CountingBacktestEvalPort backtestEvalPort;
    private PaperAutoReviewService service;

    @BeforeEach
    void setUp() {
        runRepo = new PaperTradingRunServiceTest.InMemoryRunRepo();
        equityRepo = new PaperPortfolioServiceTest.CountingEquityRepo();
        reportRepo = new PaperPortfolioServiceTest.CountingDailyReportRepo();
        riskRepo = new PaperPortfolioServiceTest.CountingRiskRepo();
        alertRepo = new PaperPortfolioServiceTest.CountingAlertRepo();
        orderRepo = new PaperPortfolioServiceTest.CountingOrderRepo();
        tradeRepo = new PaperPortfolioServiceTest.CountingTradeRepo();
        publishRepo = new PaperTradingRunServiceTest.InMemoryPublishRepo();
        backtestEvalPort = new PaperStrategyEvaluationServiceTest.CountingBacktestEvalPort();
        PaperPortfolioService portfolioService = new PaperPortfolioService(
                runRepo, equityRepo, reportRepo, riskRepo, alertRepo, orderRepo, tradeRepo, publishRepo);
        PaperExecutionDiagnosticsService diagnosticsService = new PaperExecutionDiagnosticsService(portfolioService);
        PaperStrategyEvaluationService evaluationService = new PaperStrategyEvaluationService(
                portfolioService, publishRepo, backtestEvalPort);
        service = new PaperAutoReviewService(diagnosticsService, evaluationService);
    }

    @Test
    void reviewShouldReturnStableEmptyStructureWhenNoRuns() {
        PaperAutoReview review = service.review();

        assertEquals(0, review.overview().totalRuns());
        assertTrue(review.runReviews().isEmpty());
        assertTrue(review.strategyReviews().isEmpty());
        assertTrue(review.issueClusters().isEmpty());
        assertEquals("暂无足够 Paper 事实生成复盘。", review.portfolioReview().headline());
        assertNotNull(review.overview().generatedAt());
        // 无 run → 不触达 backtest 查询。
        assertEquals(0, backtestEvalPort.calls);
    }

    @Test
    void reviewShouldReuseBatchReadWithoutPerRunAmplification() {
        runRepo.runs.add(run("run-a", "pub-1", "sv-1", PaperTradingRunStatus.STOPPED,
                Instant.parse("2026-06-03T00:00:00Z")));
        runRepo.runs.add(run("run-b", "pub-1", "sv-1", PaperTradingRunStatus.STOPPED,
                Instant.parse("2026-06-02T00:00:00Z")));
        publishRepo.records.add(publish("pub-1", "brn-1"));
        backtestEvalPort.viewByRunId.put("brn-1", btView("brn-1", "0.2", "0.05"));
        equityRepo.byRun.put("run-a", new ArrayList<>(List.of(
                equity("eq-a1", "run-a", "2026-06-03T00:00:00Z", "100000", "0"),
                equity("eq-a2", "run-a", "2026-06-03T01:00:00Z", "112000", "12000"))));
        equityRepo.byRun.put("run-b", new ArrayList<>(List.of(
                equity("eq-b1", "run-b", "2026-06-02T00:00:00Z", "100000", "0"),
                equity("eq-b2", "run-b", "2026-06-02T01:00:00Z", "110000", "10000"))));
        orderRepo.byRun.put("run-a", new ArrayList<>(List.of(order("or-a1", "run-a"))));
        orderRepo.byRun.put("run-b", new ArrayList<>(List.of(order("or-b1", "run-b"))));
        tradeRepo.byRun.put("run-a", new ArrayList<>(List.of(trade("tr-a1", "run-a"))));
        tradeRepo.byRun.put("run-b", new ArrayList<>(List.of(trade("tr-b1", "run-b"))));

        PaperAutoReview review = service.review();

        // 诊断与评估各复用一次组合看板批量读取（共 2 次批量），不出现逐 run 读放大。
        assertEquals(2, equityRepo.batchCalls);
        assertEquals(2, orderRepo.batchCalls);
        assertEquals(2, tradeRepo.batchCalls);
        assertEquals(0, equityRepo.perRunCalls);
        assertEquals(0, orderRepo.perRunCalls);
        // backtest 视图按去重 backtestRunId 仅评估侧 1 次。
        assertEquals(1, backtestEvalPort.calls);

        assertEquals(2, review.overview().totalRuns());
        assertEquals(1, review.overview().strategyReviewedCount());
        assertNotNull(review.portfolioReview().headline());
    }

    @Test
    void reviewShouldNotCrashWhenPublishOrBacktestMissing() {
        runRepo.runs.add(run("run-x", "pub-x", "sv-x", PaperTradingRunStatus.STOPPED,
                Instant.parse("2026-06-03T00:00:00Z")));
        equityRepo.byRun.put("run-x", new ArrayList<>(List.of(
                equity("eq-x1", "run-x", "2026-06-03T00:00:00Z", "100000", "0"),
                equity("eq-x2", "run-x", "2026-06-03T01:00:00Z", "90000", "-10000"))));

        PaperAutoReview review = service.review();

        assertEquals(1, review.overview().totalRuns());
        assertNotNull(review.portfolioReview().headline());
        // 无匹配 publish/backtest → backtest 查询不被触发。
        assertEquals(0, backtestEvalPort.calls);
    }

    // ---- fixtures ----

    private PaperTradingRun run(String id, String pub, String sv, PaperTradingRunStatus status, Instant updatedAt) {
        return new PaperTradingRun(
                id, pub, sv, status, "SIM", "BINANCE", "SPOT", "BTC-USDT", "1m",
                Instant.parse("2026-06-01T00:00:00Z"), updatedAt,
                "{}", "{}", "{}", "{}", "{}", "tester",
                Instant.parse("2026-06-01T00:00:00Z"), updatedAt);
    }

    private BacktestPublishRecord publish(String publishId, String backtestRunId) {
        return new BacktestPublishRecord(
                publishId, backtestRunId, "rc-1", "bc-1", "strat-1", "eval-1",
                null, "sv-1", PublishStatus.SUCCEEDED, "Publish",
                "{}", "{}", "{}", null, null,
                Instant.parse("2026-05-31T00:00:00Z"), Instant.parse("2026-05-31T00:00:00Z"), Instant.parse("2026-05-31T00:00:00Z"));
    }

    private EquityCurveSnapshot equity(String id, String runId, String time, String total, String realized) {
        return new EquityCurveSnapshot(
                id, runId, Instant.parse(time), new BigDecimal(total), new BigDecimal(total),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal(realized), BigDecimal.ZERO, "TEST", Instant.parse(time));
    }

    private PaperTradingTrade trade(String id, String runId) {
        return new PaperTradingTrade(
                id, "ord-" + id, runId, "BTC-USDT", "BUY",
                BigDecimal.ONE, new BigDecimal("100"), BigDecimal.ZERO,
                Instant.parse("2026-06-02T00:00:00Z"), Instant.parse("2026-06-02T00:00:00Z"));
    }

    private PaperTradingOrder order(String id, String runId) {
        return new PaperTradingOrder(
                id, runId, "BTC-USDT", "BUY", "LIMIT",
                BigDecimal.ONE, new BigDecimal("100"), PaperOrderStatus.CREATED, null, "{}",
                Instant.parse("2026-06-02T00:00:00Z"), Instant.parse("2026-06-02T00:00:00Z"));
    }

    private static com.guidinglight.nexusquant.research.domain.publish.BacktestEvaluationView btView(
            String backtestRunId, String returnRate, String maxDrawdownRate) {
        return new com.guidinglight.nexusquant.research.domain.publish.BacktestEvaluationView(
                "eval-" + backtestRunId, backtestRunId, "SUCCEEDED", Instant.parse("2026-05-31T00:00:00Z"),
                null, null, new BigDecimal(returnRate), new BigDecimal(maxDrawdownRate),
                null, null, null, null, null, null, null);
    }
}
