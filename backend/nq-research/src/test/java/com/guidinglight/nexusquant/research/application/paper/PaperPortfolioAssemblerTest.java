package com.guidinglight.nexusquant.research.application.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.research.domain.paper.EquityCurveSnapshot;
import com.guidinglight.nexusquant.research.domain.paper.PaperRiskCheckResult;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunDailyReport;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunDailyReportStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRun;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRunStatus;
import com.guidinglight.nexusquant.research.domain.paper.RiskCheckSeverity;
import com.guidinglight.nexusquant.research.domain.paper.RiskCheckStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * PaperPortfolioAssembler 纯函数式聚合单测：覆盖盈利 / 亏损高回撤 / 数据不足三类 run，
 * 验证组合总览、策略/发布分组、Run 排行、数据质量与空数据稳定结构。
 */
class PaperPortfolioAssemblerTest {

    @Test
    void assembleShouldReturnStableEmptyStructureForNoRuns() {
        PaperPortfolioSummary summary = PaperPortfolioAssembler.assemble(List.of());

        assertEquals(0, summary.overview().totalRuns());
        assertEquals(0, summary.overview().returnEligibleRunCount());
        assertNull(summary.overview().totalCurrentEquity());
        assertNull(summary.overview().totalReturn());
        assertNull(summary.overview().worstRunDrawdown());
        assertTrue(summary.strategyGroups().isEmpty());
        assertTrue(summary.publishGroups().isEmpty());
        assertNull(summary.highlights().topWinner());
        assertNull(summary.highlights().worstDrawdown());
        assertNull(summary.highlights().highestRisk());
        assertNull(summary.highlights().mostRecent());
        assertTrue(summary.highlights().noTradeRuns().isEmpty());
        assertTrue(summary.dataQuality().missingEquityRuns().isEmpty());
    }

    @Test
    void assembleShouldAggregateMultipleRuns() {
        PaperPortfolioSummary summary = PaperPortfolioAssembler.assemble(List.of(profitRun(), lossRun(), dataInsufficientRun()));

        // ---- 组合总览 ----
        var overview = summary.overview();
        assertEquals(3, overview.totalRuns());
        assertEquals(2, overview.stoppedCount());
        assertEquals(1, overview.createdCount());
        assertEquals(0, overview.runningCount());
        assertEquals(0, overview.failedCount());
        assertEquals(0, overview.cancelledCount());
        // 可比 run：profit(+20000) + loss(-10000)，初始资金均 100000。
        assertEquals(2, overview.returnEligibleRunCount());
        assertEquals(0, new BigDecimal("210000").compareTo(overview.totalCurrentEquity()));
        assertEquals(0, new BigDecimal("200000").compareTo(overview.totalInitialEquity()));
        assertEquals(0, new BigDecimal("10000").compareTo(overview.totalPnl()));
        assertEquals(0, new BigDecimal("0.05").compareTo(overview.totalReturn())); // 10000 / 200000
        // worstRunDrawdown 取最负：亏损 run 回撤 (90000-120000)/120000 = -0.25。
        assertEquals(0, new BigDecimal("-0.25").compareTo(overview.worstRunDrawdown()));
        assertEquals(2, overview.openAlertCount());
        assertEquals(1, overview.riskBlockedRunCount());
        assertEquals(1, overview.noTradeRunCount());
        assertEquals(1, overview.dataInsufficientRunCount());

        // ---- 策略分组（按总 PnL 降序）----
        assertEquals(2, summary.strategyGroups().size());
        var topStrategy = summary.strategyGroups().get(0);
        assertEquals("sv-1", topStrategy.key());
        assertEquals(2, topStrategy.runCount()); // profit + data-insufficient
        assertEquals(0, new BigDecimal("20000").compareTo(topStrategy.totalPnl()));
        assertEquals(0, new BigDecimal("0.2").compareTo(topStrategy.totalReturn()));
        var bottomStrategy = summary.strategyGroups().get(1);
        assertEquals("sv-2", bottomStrategy.key());
        assertEquals(1, bottomStrategy.riskBlockedCount());
        assertEquals(2, bottomStrategy.openAlertCount());
        assertEquals(0, new BigDecimal("-0.25").compareTo(bottomStrategy.worstDrawdown()));

        // ---- 发布分组 ----
        assertEquals(2, summary.publishGroups().size());
        assertEquals("pub-1", summary.publishGroups().get(0).key());

        // ---- Run 排行 ----
        var highlights = summary.highlights();
        assertEquals("run-profit", highlights.topWinner().paperRunId());
        assertEquals("run-loss", highlights.worstDrawdown().paperRunId());
        assertEquals("run-loss", highlights.highestRisk().paperRunId()); // 风控拦截优先
        assertEquals("run-loss", highlights.mostRecent().paperRunId());  // updatedAt 最新
        assertEquals(1, highlights.noTradeRuns().size());
        assertEquals("run-empty", highlights.noTradeRuns().get(0).paperRunId());
        assertEquals(1, highlights.riskBlockedRuns().size());
        assertEquals("run-loss", highlights.riskBlockedRuns().get(0).paperRunId());

        // ---- 数据质量 ----
        var dq = summary.dataQuality();
        assertEquals(1, dq.missingEquityRuns().size());
        assertEquals("run-empty", dq.missingEquityRuns().get(0).paperRunId());
        assertEquals(1, dq.dataInsufficientRuns().size());
        assertEquals(1, dq.missingBacktestSourceRuns().size());
        assertEquals("run-empty", dq.missingBacktestSourceRuns().get(0).paperRunId());
        assertTrue(dq.missingPublishSourceRuns().isEmpty());
    }

    @Test
    void assembleShouldNotFabricateReturnWhenInitialEquityMissing() {
        // 仅有日报最大回撤、无 equity 快照、无法回推初始资金 → 收益率/总资产口径置 null，回撤退化展示。
        PaperPortfolioSummary summary = PaperPortfolioAssembler.assemble(List.of(reportOnlyRun()));

        var overview = summary.overview();
        assertEquals(1, overview.totalRuns());
        assertEquals(0, overview.returnEligibleRunCount());
        assertNull(overview.totalReturn());
        assertNull(overview.totalCurrentEquity());
        assertEquals(1, overview.dataInsufficientRunCount());
        // 日报最大回撤规整为负比例后参与 worst 统计。
        assertEquals(0, new BigDecimal("-0.18").compareTo(overview.worstRunDrawdown()));
        assertEquals(1, summary.dataQuality().dataInsufficientRuns().size());
        assertFalse(summary.dataQuality().missingEquityRuns().isEmpty());
    }

    @Test
    void assembleShouldBuildPortfolioCurveAcrossRunsWithDrawdown() {
        // run-x：初始 100000，t1=100000 / t2=120000 / t3=90000（最新已实现 -10000 回推初始 100000）。
        var runX = new PaperPortfolioAssembler.RunInput(
                run("run-x", "sv-1", "pub-1", PaperTradingRunStatus.STOPPED, Instant.parse("2026-06-03T00:00:00Z")),
                List.of(
                        equity("eqx1", "run-x", "2026-06-01T00:00:00Z", "100000", "0", "0"),
                        equity("eqx2", "run-x", "2026-06-02T00:00:00Z", "120000", "15000", "5000"),
                        equity("eqx3", "run-x", "2026-06-03T00:00:00Z", "90000", "-10000", "0")),
                List.of(), List.of(), 0, 4, true, true);
        // run-y：初始 50000，t2=50000 / t3=55000（最新已实现 5000 回推初始 50000），t1 时尚未起跑。
        var runY = new PaperPortfolioAssembler.RunInput(
                run("run-y", "sv-2", "pub-2", PaperTradingRunStatus.STOPPED, Instant.parse("2026-06-03T00:00:00Z")),
                List.of(
                        equity("eqy1", "run-y", "2026-06-02T00:00:00Z", "50000", "0", "0"),
                        equity("eqy2", "run-y", "2026-06-03T00:00:00Z", "55000", "5000", "0")),
                List.of(), List.of(), 0, 2, true, true);

        var curve = PaperPortfolioAssembler.assemble(List.of(runX, runY)).portfolioCurve();

        // 时间轴并集 t1/t2/t3 共 3 点；指标基于完整曲线。
        assertEquals(3, curve.pointCount());
        assertEquals(3, curve.points().size());
        assertEquals(0, new BigDecimal("145000").compareTo(curve.latestEquity()));   // t3: 90000 + 55000
        assertEquals(0, new BigDecimal("170000").compareTo(curve.peakEquity()));     // t2: 120000 + 50000
        assertEquals(0, new BigDecimal("-0.147059").compareTo(curve.currentDrawdown())); // (145000-170000)/170000
        assertEquals(0, new BigDecimal("-0.147059").compareTo(curve.maxDrawdown()));
        assertEquals(2, curve.coverage().comparableRunCount());
        assertEquals(0, curve.coverage().missingEquityRunCount());
        assertEquals(1, curve.coverage().incompletePointCount()); // 仅 t1 缺 run-y

        // t1：仅 run-x 可用，run-y 计入 missingRunCount，不计入权益与初始资金。
        var p0 = curve.points().get(0);
        assertEquals(0, new BigDecimal("100000").compareTo(p0.totalEquity()));
        assertEquals(0, new BigDecimal("100000").compareTo(p0.totalInitialEquity()));
        assertEquals(1, p0.sourceRunCount());
        assertEquals(1, p0.missingRunCount());

        // t2：两 run 均可用，组合权益 170000、初始 150000、PnL +20000，刷新峰值后回撤 0。
        var p1 = curve.points().get(1);
        assertEquals(0, new BigDecimal("170000").compareTo(p1.totalEquity()));
        assertEquals(0, new BigDecimal("150000").compareTo(p1.totalInitialEquity()));
        assertEquals(0, new BigDecimal("20000").compareTo(p1.totalPnl()));
        assertEquals(0, p1.missingRunCount());

        // t3：组合回落到 145000，PnL -5000，当前回撤 -0.147059。
        var p2 = curve.points().get(2);
        assertEquals(0, new BigDecimal("-5000").compareTo(p2.totalPnl()));
        assertEquals(0, new BigDecimal("-0.147059").compareTo(p2.drawdown()));
    }

    @Test
    void assembleShouldReturnStablePortfolioCurveWhenNoComparableRuns() {
        // 无 run → 稳定空曲线，指标全 null。
        var emptyCurve = PaperPortfolioAssembler.assemble(List.of()).portfolioCurve();
        assertTrue(emptyCurve.points().isEmpty());
        assertEquals(0, emptyCurve.pointCount());
        assertNull(emptyCurve.latestEquity());
        assertNull(emptyCurve.peakEquity());
        assertNull(emptyCurve.currentDrawdown());
        assertNull(emptyCurve.maxDrawdown());
        assertEquals(0, emptyCurve.coverage().comparableRunCount());
        assertEquals(0, emptyCurve.coverage().missingEquityRunCount());

        // 有 run 但无 equity 快照 → 不可比，计入 missingEquityRunCount，仍稳定空曲线、不伪造回撤。
        var noEquityCurve = PaperPortfolioAssembler.assemble(List.of(dataInsufficientRun())).portfolioCurve();
        assertTrue(noEquityCurve.points().isEmpty());
        assertNull(noEquityCurve.maxDrawdown());
        assertEquals(0, noEquityCurve.coverage().comparableRunCount());
        assertEquals(1, noEquityCurve.coverage().missingEquityRunCount());
    }

    // ---- fixtures ----

    private PaperPortfolioAssembler.RunInput profitRun() {
        // 升序权益 100000 -> 120000，最新已实现 20000 → 回推初始 100000，单调上行无回撤。
        var run = run("run-profit", "sv-1", "pub-1", PaperTradingRunStatus.STOPPED,
                Instant.parse("2026-06-01T02:00:00Z"));
        var equity = List.of(
                equity("eq-p1", "run-profit", "2026-06-01T00:00:00Z", "100000", "0", "0"),
                equity("eq-p2", "run-profit", "2026-06-01T01:00:00Z", "120000", "20000", "0"));
        var risk = List.of(risk("rk-p1", "run-profit", RiskCheckStatus.PASSED, RiskCheckSeverity.LOW, "2026-06-01T01:30:00Z"));
        return new PaperPortfolioAssembler.RunInput(run, equity, List.of(), risk, 0, 5, true, true);
    }

    private PaperPortfolioAssembler.RunInput lossRun() {
        // 100000 -> 峰值 120000 -> 90000，最新已实现 -10000 → 回推初始 100000，最大回撤 -0.25。
        var run = run("run-loss", "sv-2", "pub-2", PaperTradingRunStatus.STOPPED,
                Instant.parse("2026-06-02T02:00:00Z"));
        var equity = List.of(
                equity("eq-l1", "run-loss", "2026-06-02T00:00:00Z", "100000", "0", "0"),
                equity("eq-l2", "run-loss", "2026-06-02T00:30:00Z", "120000", "20000", "0"),
                equity("eq-l3", "run-loss", "2026-06-02T01:00:00Z", "90000", "-10000", "0"));
        var risk = List.of(risk("rk-l1", "run-loss", RiskCheckStatus.REJECTED, RiskCheckSeverity.HIGH, "2026-06-02T01:30:00Z"));
        // 2 个 OPEN + 1 个 RESOLVED → openAlertCount=2（计数口径由 service 经批量聚合得出）。
        return new PaperPortfolioAssembler.RunInput(run, equity, List.of(), risk, 2, 3, true, true);
    }

    private PaperPortfolioAssembler.RunInput dataInsufficientRun() {
        // 无 equity / 无成交 / 无 backtest 来源：数据不足、无交易、缺 equity、缺 backtest。
        var run = run("run-empty", "sv-1", "pub-1", PaperTradingRunStatus.CREATED,
                Instant.parse("2026-05-30T00:00:00Z"));
        return new PaperPortfolioAssembler.RunInput(run, List.of(), List.of(), List.of(), 0, 0, true, false);
    }

    private PaperPortfolioAssembler.RunInput reportOnlyRun() {
        var run = run("run-report", "sv-9", "pub-9", PaperTradingRunStatus.STOPPED,
                Instant.parse("2026-06-04T00:00:00Z"));
        var report = new PaperRunDailyReport(
                "rep-1", "run-report", LocalDate.parse("2026-06-04"), PaperRunDailyReportStatus.GENERATED,
                new BigDecimal("98000"), new BigDecimal("-2000"), new BigDecimal("-0.02"), new BigDecimal("0.18"),
                4, 4, 0, 0, "{}", Instant.parse("2026-06-04T01:00:00Z"), Instant.parse("2026-06-04T01:00:00Z"));
        return new PaperPortfolioAssembler.RunInput(run, List.of(), List.of(report), List.of(), 0, 4, true, true);
    }

    private PaperTradingRun run(String id, String sv, String pub, PaperTradingRunStatus status, Instant updatedAt) {
        return new PaperTradingRun(
                id, pub, sv, status, "SIM", "BINANCE", "SPOT", "BTC-USDT", "1m",
                Instant.parse("2026-06-01T00:00:00Z"), null,
                "{}", "{}", "{}", "{}", "{}", "tester",
                Instant.parse("2026-06-01T00:00:00Z"), updatedAt);
    }

    private EquityCurveSnapshot equity(String id, String runId, String time, String total, String realized, String unrealized) {
        return new EquityCurveSnapshot(
                id, runId, Instant.parse(time), new BigDecimal(total), new BigDecimal(total),
                BigDecimal.ZERO, new BigDecimal(unrealized), new BigDecimal(realized),
                BigDecimal.ZERO, "TEST", Instant.parse(time));
    }

    private PaperRiskCheckResult risk(String id, String runId, RiskCheckStatus status, RiskCheckSeverity severity, String time) {
        return new PaperRiskCheckResult(id, runId, "CHECK", status, severity, "msg", "{}", "{}", Instant.parse(time));
    }
}
