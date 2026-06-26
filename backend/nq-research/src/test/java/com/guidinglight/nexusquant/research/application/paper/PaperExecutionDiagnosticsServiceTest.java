package com.guidinglight.nexusquant.research.application.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * PaperExecutionDiagnosticsService 集成口径单测：复用 {@link PaperPortfolioServiceTest} 的 counting 只读仓储装配
 * {@link PaperPortfolioService}，验证：
 * 1) 诊断复用组合看板的批量读取——每类事实批量端口各调用一次，不出现逐 run 读放大。
 * 2) 无 run 时返回稳定空结构；事实缺失（无 equity）时降级为 DATA_INSUFFICIENT 而非异常。
 * 3) 归因总览计数与 run 事实一致。
 */
class PaperExecutionDiagnosticsServiceTest {

    private PaperTradingRunServiceTest.InMemoryRunRepo runRepo;
    private PaperPortfolioServiceTest.CountingEquityRepo equityRepo;
    private PaperPortfolioServiceTest.CountingDailyReportRepo reportRepo;
    private PaperPortfolioServiceTest.CountingRiskRepo riskRepo;
    private PaperPortfolioServiceTest.CountingAlertRepo alertRepo;
    private PaperPortfolioServiceTest.CountingOrderRepo orderRepo;
    private PaperPortfolioServiceTest.CountingTradeRepo tradeRepo;
    private PaperTradingRunServiceTest.InMemoryPublishRepo publishRepo;
    private PaperExecutionDiagnosticsService service;

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
        PaperPortfolioService portfolioService = new PaperPortfolioService(
                runRepo, equityRepo, reportRepo, riskRepo, alertRepo, orderRepo, tradeRepo, publishRepo);
        service = new PaperExecutionDiagnosticsService(portfolioService);
    }

    @Test
    void diagnoseShouldReturnStableEmptyStructureWhenNoRuns() {
        PaperExecutionDiagnostics diagnostics = service.diagnose();

        assertEquals(0, diagnostics.overview().totalRuns());
        assertTrue(diagnostics.causeDistribution().isEmpty());
        assertTrue(diagnostics.runDiagnostics().isEmpty());
        assertTrue(diagnostics.strategyDiagnostics().isEmpty());
        assertTrue(diagnostics.publishDiagnostics().isEmpty());
    }

    @Test
    void diagnoseShouldReuseBatchReadWithoutPerRunAmplification() {
        runRepo.runs.add(run("run-a", "pub-1", "sv-1", PaperTradingRunStatus.RUNNING,
                Instant.parse("2026-06-03T00:00:00Z")));
        runRepo.runs.add(run("run-b", "pub-1", "sv-1", PaperTradingRunStatus.STOPPED,
                Instant.parse("2026-06-02T00:00:00Z")));
        publishRepo.records.add(publish("pub-1", "brn-1"));
        // run-a：有成交（4 单 3 成交）+ equity。
        equityRepo.byRun.put("run-a", new ArrayList<>(List.of(
                equity("eq-a1", "run-a", "2026-06-03T00:00:00Z", "100000", "0"),
                equity("eq-a2", "run-a", "2026-06-03T01:00:00Z", "112000", "12000"))));
        orderRepo.byRun.put("run-a", new ArrayList<>(List.of(
                order("or-a1", "run-a"), order("or-a2", "run-a"), order("or-a3", "run-a"), order("or-a4", "run-a"))));
        tradeRepo.byRun.put("run-a", new ArrayList<>(List.of(
                trade("tr-a1", "run-a"), trade("tr-a2", "run-a"), trade("tr-a3", "run-a"))));
        // run-b：无订单无成交、无 equity → 数据不足 + 无订单。

        PaperExecutionDiagnostics diagnostics = service.diagnose();

        // ---- 批量读取语义：每类事实批量端口各调用一次（复用组合看板批量读取）----
        assertEquals(1, equityRepo.batchCalls);
        assertEquals(1, reportRepo.batchCalls);
        assertEquals(1, riskRepo.batchCalls);
        assertEquals(1, alertRepo.batchCalls);
        assertEquals(1, orderRepo.batchCalls);
        assertEquals(1, tradeRepo.batchCalls);
        // ---- 不出现逐 run 单条读取（无 O(run 数) 读放大）----
        assertEquals(0, equityRepo.perRunCalls);
        assertEquals(0, orderRepo.perRunCalls);
        assertEquals(0, tradeRepo.perRunCalls);
        assertEquals(0, riskRepo.perRunCalls);

        // ---- 归因总览：2 run，run-a 有成交（healthy），run-b 无订单且数据不足 ----
        assertEquals(2, diagnostics.overview().totalRuns());
        assertEquals(1, diagnostics.overview().filledRunCount());
        assertEquals(1, diagnostics.overview().noOrderRunCount());
        assertEquals(1, diagnostics.overview().dataInsufficientRunCount());
        assertEquals(2, diagnostics.runDiagnostics().size());
    }

    @Test
    void diagnoseShouldDowngradeToDataInsufficientWhenFactsMissing() {
        // 只 seed run，不 seed 任何 equity/order/trade/risk/alert → 不崩，归因 DATA_INSUFFICIENT。
        runRepo.runs.add(run("run-x", "pub-1", "sv-1", PaperTradingRunStatus.RUNNING,
                Instant.parse("2026-06-03T00:00:00Z")));

        PaperExecutionDiagnostics diagnostics = service.diagnose();

        assertEquals(1, diagnostics.overview().totalRuns());
        assertEquals(1, diagnostics.overview().dataInsufficientRunCount());
        assertEquals(PaperExecutionDiagnostics.Cause.DATA_INSUFFICIENT,
                diagnostics.runDiagnostics().get(0).primaryCause());
    }

    // ---- fixtures（与 PaperPortfolioServiceTest 同口径）----

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
}
