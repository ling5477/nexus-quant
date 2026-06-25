package com.guidinglight.nexusquant.research.application.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.research.domain.paper.EquityCurveSnapshot;
import com.guidinglight.nexusquant.research.domain.paper.PaperRiskCheckResult;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlert;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlertSeverity;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlertStatus;
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

    // ---- fixtures ----

    private PaperPortfolioAssembler.RunInput profitRun() {
        // 升序权益 100000 -> 120000，最新已实现 20000 → 回推初始 100000，单调上行无回撤。
        var run = run("run-profit", "sv-1", "pub-1", PaperTradingRunStatus.STOPPED,
                Instant.parse("2026-06-01T02:00:00Z"));
        var equity = List.of(
                equity("eq-p1", "run-profit", "2026-06-01T00:00:00Z", "100000", "0", "0"),
                equity("eq-p2", "run-profit", "2026-06-01T01:00:00Z", "120000", "20000", "0"));
        var risk = List.of(risk("rk-p1", "run-profit", RiskCheckStatus.PASSED, RiskCheckSeverity.LOW, "2026-06-01T01:30:00Z"));
        return new PaperPortfolioAssembler.RunInput(run, equity, List.of(), risk, List.of(), 5, true, true);
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
        var alerts = List.of(
                alert("al-l1", "run-loss", PaperRunAlertStatus.OPEN),
                alert("al-l2", "run-loss", PaperRunAlertStatus.OPEN),
                alert("al-l3", "run-loss", PaperRunAlertStatus.RESOLVED));
        return new PaperPortfolioAssembler.RunInput(run, equity, List.of(), risk, alerts, 3, true, true);
    }

    private PaperPortfolioAssembler.RunInput dataInsufficientRun() {
        // 无 equity / 无成交 / 无 backtest 来源：数据不足、无交易、缺 equity、缺 backtest。
        var run = run("run-empty", "sv-1", "pub-1", PaperTradingRunStatus.CREATED,
                Instant.parse("2026-05-30T00:00:00Z"));
        return new PaperPortfolioAssembler.RunInput(run, List.of(), List.of(), List.of(), List.of(), 0, true, false);
    }

    private PaperPortfolioAssembler.RunInput reportOnlyRun() {
        var run = run("run-report", "sv-9", "pub-9", PaperTradingRunStatus.STOPPED,
                Instant.parse("2026-06-04T00:00:00Z"));
        var report = new PaperRunDailyReport(
                "rep-1", "run-report", LocalDate.parse("2026-06-04"), PaperRunDailyReportStatus.GENERATED,
                new BigDecimal("98000"), new BigDecimal("-2000"), new BigDecimal("-0.02"), new BigDecimal("0.18"),
                4, 4, 0, 0, "{}", Instant.parse("2026-06-04T01:00:00Z"), Instant.parse("2026-06-04T01:00:00Z"));
        return new PaperPortfolioAssembler.RunInput(run, List.of(), List.of(report), List.of(), List.of(), 4, true, true);
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

    private PaperRunAlert alert(String id, String runId, PaperRunAlertStatus status) {
        return new PaperRunAlert(
                id, runId, "RISK", PaperRunAlertSeverity.HIGH, status, "alert", "msg", "SRC", "{}",
                null, null, null, Instant.parse("2026-06-02T01:40:00Z"), Instant.parse("2026-06-02T01:40:00Z"));
    }
}
