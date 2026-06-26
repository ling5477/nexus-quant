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
import com.guidinglight.nexusquant.research.domain.publish.BacktestEvaluationView;
import com.guidinglight.nexusquant.research.domain.publish.port.BacktestEvaluationQueryPort;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PaperStrategyEvaluationService 集成口径单测：复用 {@link PaperPortfolioServiceTest} 的 counting 只读仓储装配
 * {@link PaperPortfolioService}，验证：
 * 1) 评估复用组合看板批量读取——不出现逐 run 读放大；backtest 视图按去重 backtestRunId 有限次读取。
 * 2) 无 run 时返回稳定空结构且不触达 backtest 查询；publish / backtest 缺失时不崩。
 * 3) backtest 视图存在时偏差对照被填充。
 */
class PaperStrategyEvaluationServiceTest {

    private PaperTradingRunServiceTest.InMemoryRunRepo runRepo;
    private PaperPortfolioServiceTest.CountingEquityRepo equityRepo;
    private PaperPortfolioServiceTest.CountingDailyReportRepo reportRepo;
    private PaperPortfolioServiceTest.CountingRiskRepo riskRepo;
    private PaperPortfolioServiceTest.CountingAlertRepo alertRepo;
    private PaperPortfolioServiceTest.CountingOrderRepo orderRepo;
    private PaperPortfolioServiceTest.CountingTradeRepo tradeRepo;
    private PaperTradingRunServiceTest.InMemoryPublishRepo publishRepo;
    private CountingBacktestEvalPort backtestEvalPort;
    private PaperStrategyEvaluationService service;

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
        backtestEvalPort = new CountingBacktestEvalPort();
        PaperPortfolioService portfolioService = new PaperPortfolioService(
                runRepo, equityRepo, reportRepo, riskRepo, alertRepo, orderRepo, tradeRepo, publishRepo);
        service = new PaperStrategyEvaluationService(portfolioService, publishRepo, backtestEvalPort);
    }

    @Test
    void evaluateShouldReturnStableEmptyStructureAndSkipBacktestWhenNoRuns() {
        PaperStrategyEvaluation evaluation = service.evaluate();

        assertEquals(0, evaluation.overview().strategyCount());
        assertTrue(evaluation.strategyEvaluations().isEmpty());
        assertTrue(evaluation.publishEvaluations().isEmpty());
        // 无 run → 不触达 backtest 查询。
        assertEquals(0, backtestEvalPort.calls);
    }

    @Test
    void evaluateShouldReuseBatchReadAndJoinBacktestDeviation() {
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
        orderRepo.byRun.put("run-a", new ArrayList<>(List.of(order("or-a1", "run-a"), order("or-a2", "run-a"))));
        orderRepo.byRun.put("run-b", new ArrayList<>(List.of(order("or-b1", "run-b"), order("or-b2", "run-b"))));
        tradeRepo.byRun.put("run-a", new ArrayList<>(List.of(trade("tr-a1", "run-a"))));
        tradeRepo.byRun.put("run-b", new ArrayList<>(List.of(trade("tr-b1", "run-b"))));

        PaperStrategyEvaluation evaluation = service.evaluate();

        // ---- 批量读取语义：portfolio 各事实批量端口各 1 次 ----
        assertEquals(1, equityRepo.batchCalls);
        assertEquals(1, orderRepo.batchCalls);
        assertEquals(1, tradeRepo.batchCalls);
        assertEquals(0, equityRepo.perRunCalls);
        assertEquals(0, orderRepo.perRunCalls);
        // ---- backtest 视图按去重 backtestRunId 有限次读取（仅 brn-1 → 1 次）----
        assertEquals(1, backtestEvalPort.calls);

        assertEquals(1, evaluation.strategyEvaluations().size());
        var sv1 = evaluation.strategyEvaluations().get(0);
        assertEquals("sv-1", sv1.strategyVersionId());
        assertEquals(2, sv1.runCount());
        // backtest 视图存在 → 偏差对照被填充（非 UNAVAILABLE）。
        assertNotNull(sv1.backtestDeviation());
        assertNotNull(sv1.backtestDeviation().backtestReturn());
        assertNotNull(sv1.backtestDeviationScore());
    }

    @Test
    void evaluateShouldNotCrashWhenPublishOrBacktestMissing() {
        // 有 run 与 equity，但无 publish 记录 / 无 backtest 视图。
        runRepo.runs.add(run("run-x", "pub-x", "sv-x", PaperTradingRunStatus.STOPPED,
                Instant.parse("2026-06-03T00:00:00Z")));
        equityRepo.byRun.put("run-x", new ArrayList<>(List.of(
                equity("eq-x1", "run-x", "2026-06-03T00:00:00Z", "100000", "0"),
                equity("eq-x2", "run-x", "2026-06-03T01:00:00Z", "110000", "10000"))));

        PaperStrategyEvaluation evaluation = service.evaluate();

        assertEquals(1, evaluation.strategyEvaluations().size());
        var sv = evaluation.strategyEvaluations().get(0);
        assertTrue(sv.warnings().contains("BACKTEST_DATA_UNAVAILABLE"));
        // 无匹配 publish/backtest → backtest 查询不被触发（usedPublishIds 无对应记录）。
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

    private static BacktestEvaluationView btView(String backtestRunId, String returnRate, String maxDrawdownRate) {
        return new BacktestEvaluationView(
                "eval-" + backtestRunId, backtestRunId, "SUCCEEDED", Instant.parse("2026-05-31T00:00:00Z"),
                null, null, new BigDecimal(returnRate), new BigDecimal(maxDrawdownRate),
                null, null, null, null, null, null, null);
    }

    /** 计数 backtest 评估查询次数的 in-memory 端口（验证不放大查询）。 */
    static final class CountingBacktestEvalPort implements BacktestEvaluationQueryPort {
        final Map<String, BacktestEvaluationView> viewByRunId = new HashMap<>();
        int calls;

        @Override
        public Optional<BacktestEvaluationView> findByBacktestRunId(String backtestRunId) {
            calls++;
            return Optional.ofNullable(viewByRunId.get(backtestRunId));
        }
    }
}
