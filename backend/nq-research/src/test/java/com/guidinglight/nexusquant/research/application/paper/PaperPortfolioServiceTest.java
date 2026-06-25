package com.guidinglight.nexusquant.research.application.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.research.domain.BacktestPublishRecord;
import com.guidinglight.nexusquant.research.domain.PublishStatus;
import com.guidinglight.nexusquant.research.domain.paper.EquityCurveSnapshot;
import com.guidinglight.nexusquant.research.domain.paper.PaperRiskCheckResult;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlert;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlertSeverity;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlertStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunDailyReport;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRun;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRunStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingTrade;
import com.guidinglight.nexusquant.research.domain.paper.port.EquityCurveSnapshotRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRiskCheckResultRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunAlertRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunDailyReportRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperTradingTradeRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PaperPortfolioService 集成口径单测：用 in-memory 仓储装配服务，验证：
 * 1) 组合聚合只依赖只读仓储（无 adapter / exchange / credential / 外呼）。
 * 2) 无 run 时返回稳定空结构，有 run 时正确聚合总览与排行。
 * 3) run 事实改为批量读取——每类事实批量端口各调用一次，且不再逐 run 调用单条读取（消除读放大）。
 */
class PaperPortfolioServiceTest {

    private PaperTradingRunServiceTest.InMemoryRunRepo runRepo;
    private CountingEquityRepo equityRepo;
    private CountingDailyReportRepo reportRepo;
    private CountingRiskRepo riskRepo;
    private CountingAlertRepo alertRepo;
    private CountingTradeRepo tradeRepo;
    private PaperTradingRunServiceTest.InMemoryPublishRepo publishRepo;
    private PaperPortfolioService service;

    @BeforeEach
    void setUp() {
        runRepo = new PaperTradingRunServiceTest.InMemoryRunRepo();
        equityRepo = new CountingEquityRepo();
        reportRepo = new CountingDailyReportRepo();
        riskRepo = new CountingRiskRepo();
        alertRepo = new CountingAlertRepo();
        tradeRepo = new CountingTradeRepo();
        publishRepo = new PaperTradingRunServiceTest.InMemoryPublishRepo();
        service = new PaperPortfolioService(runRepo, equityRepo, reportRepo, riskRepo, alertRepo, tradeRepo, publishRepo);
    }

    @Test
    void summarizeShouldReturnStableEmptyStructureWhenNoRuns() {
        PaperPortfolioSummary summary = service.summarize();

        assertEquals(0, summary.overview().totalRuns());
        assertEquals(0, summary.overview().returnEligibleRunCount());
        assertNull(summary.overview().totalReturn());
        assertTrue(summary.strategyGroups().isEmpty());
        assertTrue(summary.publishGroups().isEmpty());
        assertNull(summary.highlights().topWinner());
        assertTrue(summary.dataQuality().missingEquityRuns().isEmpty());
    }

    @Test
    void summarizeShouldAggregateSeededRunFromReadRepositoriesOnly() {
        runRepo.runs.add(run("run-1", "pub-1", "sv-1", PaperTradingRunStatus.STOPPED,
                Instant.parse("2026-06-01T02:00:00Z")));
        publishRepo.records.add(publish("pub-1", "brn-1"));
        equityRepo.byRun.put("run-1", new ArrayList<>(List.of(
                equity("eq-1", "run-1", "2026-06-01T00:30:00Z", "100000", "0"),
                equity("eq-2", "run-1", "2026-06-01T01:30:00Z", "112000", "12000"))));

        PaperPortfolioSummary summary = service.summarize();

        assertEquals(1, summary.overview().totalRuns());
        assertEquals(1, summary.overview().stoppedCount());
        assertEquals(1, summary.overview().returnEligibleRunCount());
        assertEquals(0, new BigDecimal("112000").compareTo(summary.overview().totalCurrentEquity()));
        assertEquals(0, new BigDecimal("100000").compareTo(summary.overview().totalInitialEquity()));
        assertEquals(0, new BigDecimal("12000").compareTo(summary.overview().totalPnl()));
        assertNotNull(summary.highlights().topWinner());
        assertEquals("run-1", summary.highlights().topWinner().paperRunId());
        // publish 含 backtestRunId → 不缺 backtest / publish 来源。
        assertTrue(summary.dataQuality().missingBacktestSourceRuns().isEmpty());
        assertTrue(summary.dataQuality().missingPublishSourceRuns().isEmpty());
        // 该 run 无成交 → 计入无交易清单。
        assertEquals(1, summary.overview().noTradeRunCount());

        // 组合曲线由 batch-read 的 equity 快照派生（无额外 per-run 查询）：单 run 两点、初始 100000、当前回撤 0。
        var curve = summary.portfolioCurve();
        assertEquals(2, curve.pointCount());
        assertEquals(0, new BigDecimal("112000").compareTo(curve.latestEquity()));
        assertEquals(0, new BigDecimal("112000").compareTo(curve.peakEquity()));
        assertEquals(0, BigDecimal.ZERO.compareTo(curve.currentDrawdown()));
        assertEquals(1, curve.coverage().comparableRunCount());
        assertEquals(0, curve.coverage().missingEquityRunCount());

        // Loop-17 组内精确计数从完整 bounded run 聚合（非 highlights 截断）：sv-1 = run-1(STOPPED,可比,无成交)。
        var strategyGroup = summary.strategyGroups().get(0);
        assertEquals("sv-1", strategyGroup.key());
        assertEquals(1, strategyGroup.noTradeCount());
        assertEquals(0, strategyGroup.dataInsufficientCount());
        assertEquals(1, strategyGroup.comparableRunCount());
        assertEquals(1, strategyGroup.stoppedCount());
        assertEquals(0, strategyGroup.failedCount());
    }

    @Test
    void summarizeShouldBatchLoadRunFactsAndAvoidPerRunReads() {
        // 三个 run，分别 seed equity / risk / alert / trade，验证批量读取语义与计数聚合口径。
        runRepo.runs.add(run("run-a", "pub-1", "sv-1", PaperTradingRunStatus.RUNNING,
                Instant.parse("2026-06-03T00:00:00Z")));
        runRepo.runs.add(run("run-b", "pub-1", "sv-1", PaperTradingRunStatus.STOPPED,
                Instant.parse("2026-06-02T00:00:00Z")));
        runRepo.runs.add(run("run-c", "pub-2", "sv-2", PaperTradingRunStatus.STOPPED,
                Instant.parse("2026-06-01T00:00:00Z")));
        publishRepo.records.add(publish("pub-1", "brn-1"));
        publishRepo.records.add(publish("pub-2", "brn-2"));

        equityRepo.byRun.put("run-a", new ArrayList<>(List.of(
                equity("eq-a1", "run-a", "2026-06-03T00:00:00Z", "100000", "0"),
                equity("eq-a2", "run-a", "2026-06-03T01:00:00Z", "110000", "10000"))));
        // run-a：2 个 OPEN + 1 个 RESOLVED 告警；3 笔成交。
        alertRepo.byRun.put("run-a", new ArrayList<>(List.of(
                alert("al-a1", "run-a", PaperRunAlertStatus.OPEN),
                alert("al-a2", "run-a", PaperRunAlertStatus.OPEN),
                alert("al-a3", "run-a", PaperRunAlertStatus.RESOLVED))));
        tradeRepo.byRun.put("run-a", new ArrayList<>(List.of(
                trade("tr-a1", "run-a"), trade("tr-a2", "run-a"), trade("tr-a3", "run-a"))));
        // run-b：1 个 OPEN 告警；1 笔成交。
        alertRepo.byRun.put("run-b", new ArrayList<>(List.of(
                alert("al-b1", "run-b", PaperRunAlertStatus.OPEN))));
        tradeRepo.byRun.put("run-b", new ArrayList<>(List.of(trade("tr-b1", "run-b"))));
        // run-c：无告警、无成交。

        PaperPortfolioSummary summary = service.summarize();

        // ---- 批量读取语义：每类事实批量端口各调用一次 ----
        assertEquals(1, equityRepo.batchCalls);
        assertEquals(1, reportRepo.batchCalls);
        assertEquals(1, riskRepo.batchCalls);
        assertEquals(1, alertRepo.batchCalls);
        assertEquals(1, tradeRepo.batchCalls);
        // ---- 不再逐 run 调用单条读取（消除 O(run 数) 读放大）----
        assertEquals(0, equityRepo.perRunCalls);
        assertEquals(0, reportRepo.perRunCalls);
        assertEquals(0, riskRepo.perRunCalls);
        assertEquals(0, alertRepo.perRunCalls);
        assertEquals(0, tradeRepo.perRunCalls);

        // ---- 计数聚合口径正确：OPEN 告警 3（run-a 2 + run-b 1），无交易 run 1（run-c）----
        assertEquals(3, summary.overview().totalRuns());
        assertEquals(3, summary.overview().openAlertCount());
        assertEquals(1, summary.overview().noTradeRunCount());
        // run-c 无成交 → 进入无交易清单。
        assertEquals(1, summary.highlights().noTradeRuns().size());
        assertEquals("run-c", summary.highlights().noTradeRuns().get(0).paperRunId());
    }

    @Test
    void inMemoryBatchReadShouldHandleEmptyAndMultiRunIds() {
        // 空 runIds → 空 Map，无异常。
        assertTrue(equityRepo.listByRunIds(List.of()).isEmpty());
        assertTrue(tradeRepo.countByRunIds(List.of()).isEmpty());
        assertTrue(alertRepo.countOpenByRunIds(List.of()).isEmpty());

        equityRepo.byRun.put("run-x", new ArrayList<>(List.of(
                equity("eq-x", "run-x", "2026-06-01T00:00:00Z", "100", "0"))));
        equityRepo.byRun.put("run-y", new ArrayList<>(List.of(
                equity("eq-y", "run-y", "2026-06-01T00:00:00Z", "200", "0"))));

        // 多 runIds → 按 run 分组；无数据 run 不作为 key 出现。
        Map<String, List<EquityCurveSnapshot>> byRun = equityRepo.listByRunIds(List.of("run-x", "run-y", "run-z"));
        assertEquals(2, byRun.size());
        assertEquals(1, byRun.get("run-x").size());
        assertFalse(byRun.containsKey("run-z"));
    }

    @Test
    void defaultBatchReadShouldDelegatePerRunAndSkipEmpty() {
        // 验证接口 default 兜底实现（生产代码）：空 runIds → 空 Map；逐 run 委托；无数据 run 不出现。
        EquityCurveSnapshotRepository repo = new EquityCurveSnapshotRepository() {
            final Map<String, List<EquityCurveSnapshot>> data = Map.of(
                    "run-x", List.of(equity("eq-x", "run-x", "2026-06-01T00:00:00Z", "100", "0")));
            @Override public void insert(EquityCurveSnapshot snapshot) { }
            @Override public List<EquityCurveSnapshot> listByRunId(String paperRunId) {
                return data.getOrDefault(paperRunId, List.of());
            }
        };
        assertTrue(repo.listByRunIds(List.of()).isEmpty());
        Map<String, List<EquityCurveSnapshot>> byRun = repo.listByRunIds(List.of("run-x", "run-z"));
        assertEquals(1, byRun.size());
        assertTrue(byRun.containsKey("run-x"));
        assertFalse(byRun.containsKey("run-z"));
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

    private PaperRunAlert alert(String id, String runId, PaperRunAlertStatus status) {
        return new PaperRunAlert(
                id, runId, "RISK", PaperRunAlertSeverity.HIGH, status, "alert", "msg", "SRC", "{}",
                null, null, null, Instant.parse("2026-06-02T01:40:00Z"), Instant.parse("2026-06-02T01:40:00Z"));
    }

    private PaperTradingTrade trade(String id, String runId) {
        return new PaperTradingTrade(
                id, "ord-" + id, runId, "BTC-USDT", "BUY",
                BigDecimal.ONE, new BigDecimal("100"), BigDecimal.ZERO,
                Instant.parse("2026-06-02T00:00:00Z"), Instant.parse("2026-06-02T00:00:00Z"));
    }

    // ---- 可注入数据并计数读取次数的 in-memory 只读仓储 ----

    static final class CountingEquityRepo implements EquityCurveSnapshotRepository {
        final Map<String, List<EquityCurveSnapshot>> byRun = new HashMap<>();
        int perRunCalls;
        int batchCalls;
        @Override public void insert(EquityCurveSnapshot snapshot) {
            byRun.computeIfAbsent(snapshot.paperRunId(), k -> new ArrayList<>()).add(snapshot);
        }
        @Override public List<EquityCurveSnapshot> listByRunId(String paperRunId) {
            perRunCalls++;
            return byRun.getOrDefault(paperRunId, List.of());
        }
        @Override public Map<String, List<EquityCurveSnapshot>> listByRunIds(Collection<String> runIds) {
            batchCalls++;
            Map<String, List<EquityCurveSnapshot>> result = new LinkedHashMap<>();
            for (String runId : runIds) {
                List<EquityCurveSnapshot> rows = byRun.get(runId);
                if (rows != null && !rows.isEmpty()) {
                    result.put(runId, rows);
                }
            }
            return result;
        }
    }

    static final class CountingRiskRepo implements PaperRiskCheckResultRepository {
        final Map<String, List<PaperRiskCheckResult>> byRun = new HashMap<>();
        int perRunCalls;
        int batchCalls;
        @Override public void insert(PaperRiskCheckResult result) {
            byRun.computeIfAbsent(result.paperRunId(), k -> new ArrayList<>()).add(result);
        }
        @Override public List<PaperRiskCheckResult> listByRunId(String paperRunId) {
            perRunCalls++;
            return byRun.getOrDefault(paperRunId, List.of());
        }
        @Override public Map<String, List<PaperRiskCheckResult>> listByRunIds(Collection<String> runIds) {
            batchCalls++;
            Map<String, List<PaperRiskCheckResult>> result = new LinkedHashMap<>();
            for (String runId : runIds) {
                List<PaperRiskCheckResult> rows = byRun.get(runId);
                if (rows != null && !rows.isEmpty()) {
                    result.put(runId, rows);
                }
            }
            return result;
        }
    }

    static final class CountingDailyReportRepo implements PaperRunDailyReportRepository {
        final Map<String, List<PaperRunDailyReport>> byRun = new HashMap<>();
        int perRunCalls;
        int batchCalls;
        @Override public void upsert(PaperRunDailyReport report) {
            byRun.computeIfAbsent(report.paperRunId(), k -> new ArrayList<>()).add(report);
        }
        @Override public Optional<PaperRunDailyReport> findById(String reportId) { return Optional.empty(); }
        @Override public Optional<PaperRunDailyReport> findByRunIdAndDate(String paperRunId, LocalDate reportDate) { return Optional.empty(); }
        @Override public List<PaperRunDailyReport> listByRunId(String paperRunId) {
            perRunCalls++;
            return byRun.getOrDefault(paperRunId, List.of());
        }
        @Override public int countByRunIdAndDateRange(String paperRunId, Instant start, Instant end) { return 0; }
        @Override public Map<String, List<PaperRunDailyReport>> listByRunIds(Collection<String> runIds) {
            batchCalls++;
            Map<String, List<PaperRunDailyReport>> result = new LinkedHashMap<>();
            for (String runId : runIds) {
                List<PaperRunDailyReport> rows = byRun.get(runId);
                if (rows != null && !rows.isEmpty()) {
                    result.put(runId, rows);
                }
            }
            return result;
        }
    }

    static final class CountingAlertRepo implements PaperRunAlertRepository {
        final Map<String, List<PaperRunAlert>> byRun = new HashMap<>();
        int perRunCalls;
        int batchCalls;
        @Override public void insert(PaperRunAlert alert) {
            byRun.computeIfAbsent(alert.paperRunId(), k -> new ArrayList<>()).add(alert);
        }
        @Override public Optional<PaperRunAlert> findById(String alertId) { return Optional.empty(); }
        @Override public List<PaperRunAlert> listByRunId(String paperRunId, String status, String severity) {
            perRunCalls++;
            return byRun.getOrDefault(paperRunId, List.of());
        }
        @Override public boolean updateStatus(String alertId, PaperRunAlertStatus status, String acknowledgedBy, Instant acknowledgedAt, Instant resolvedAt, Instant updatedAt) { return false; }
        @Override public int countByRunIdAndDateRange(String paperRunId, Instant start, Instant end) { return 0; }
        @Override public int countCriticalOpenByRunIdAndDateRange(String paperRunId, Instant start, Instant end) { return 0; }
        @Override public int countByRunIdAndTypeAndDateRange(String paperRunId, String alertType, Instant start, Instant end) { return 0; }
        @Override public Map<String, Long> countOpenByRunIds(Collection<String> runIds) {
            batchCalls++;
            Map<String, Long> result = new LinkedHashMap<>();
            for (String runId : runIds) {
                long open = byRun.getOrDefault(runId, List.of()).stream()
                        .filter(a -> a.status() == PaperRunAlertStatus.OPEN)
                        .count();
                if (open > 0) {
                    result.put(runId, open);
                }
            }
            return result;
        }
    }

    static final class CountingTradeRepo implements PaperTradingTradeRepository {
        final Map<String, List<PaperTradingTrade>> byRun = new HashMap<>();
        int perRunCalls;
        int batchCalls;
        @Override public void insert(PaperTradingTrade trade) {
            byRun.computeIfAbsent(trade.paperRunId(), k -> new ArrayList<>()).add(trade);
        }
        @Override public List<PaperTradingTrade> listByRunId(String paperRunId) {
            perRunCalls++;
            return byRun.getOrDefault(paperRunId, List.of());
        }
        @Override public Map<String, Long> countByRunIds(Collection<String> runIds) {
            batchCalls++;
            Map<String, Long> result = new LinkedHashMap<>();
            for (String runId : runIds) {
                long count = byRun.getOrDefault(runId, List.of()).size();
                if (count > 0) {
                    result.put(runId, count);
                }
            }
            return result;
        }
    }
}
