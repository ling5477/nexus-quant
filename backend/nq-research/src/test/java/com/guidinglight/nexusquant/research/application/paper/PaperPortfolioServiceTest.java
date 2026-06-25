package com.guidinglight.nexusquant.research.application.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.research.domain.BacktestPublishRecord;
import com.guidinglight.nexusquant.research.domain.PublishStatus;
import com.guidinglight.nexusquant.research.domain.paper.EquityCurveSnapshot;
import com.guidinglight.nexusquant.research.domain.paper.PaperRiskCheckResult;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlert;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlertStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunDailyReport;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRun;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRunStatus;
import com.guidinglight.nexusquant.research.domain.paper.port.EquityCurveSnapshotRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRiskCheckResultRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunAlertRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunDailyReportRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PaperPortfolioService 集成口径单测：用 in-memory 仓储装配服务，
 * 验证组合聚合只依赖只读仓储（无 adapter / exchange / credential / 外呼），
 * 无 run 时返回稳定空结构，有 run 时正确聚合总览与排行。
 */
class PaperPortfolioServiceTest {

    private PaperTradingRunServiceTest.InMemoryRunRepo runRepo;
    private InMemoryEquityRepo equityRepo;
    private InMemoryDailyReportRepo reportRepo;
    private InMemoryRiskRepo riskRepo;
    private InMemoryAlertRepo alertRepo;
    private PaperTradingRunServiceTest.InMemoryTradeRepo tradeRepo;
    private PaperTradingRunServiceTest.InMemoryPublishRepo publishRepo;
    private PaperPortfolioService service;

    @BeforeEach
    void setUp() {
        runRepo = new PaperTradingRunServiceTest.InMemoryRunRepo();
        equityRepo = new InMemoryEquityRepo();
        reportRepo = new InMemoryDailyReportRepo();
        riskRepo = new InMemoryRiskRepo();
        alertRepo = new InMemoryAlertRepo();
        tradeRepo = new PaperTradingRunServiceTest.InMemoryTradeRepo();
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
        runRepo.runs.add(new PaperTradingRun(
                "run-1", "pub-1", "sv-1", PaperTradingRunStatus.STOPPED, "SIM", "BINANCE", "SPOT", "BTC-USDT", "1m",
                Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-06-01T02:00:00Z"),
                "{}", "{}", "{}", "{}", "{}", "tester",
                Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-06-01T02:00:00Z")));
        publishRepo.records.add(new BacktestPublishRecord(
                "pub-1", "brn-1", "rc-1", "bc-1", "strat-1", "eval-1",
                null, "sv-1", PublishStatus.SUCCEEDED, "Publish",
                "{}", "{}", "{}", null, null,
                Instant.parse("2026-05-31T00:00:00Z"), Instant.parse("2026-05-31T00:00:00Z"), Instant.parse("2026-05-31T00:00:00Z")));
        equityRepo.byRun.put("run-1", List.of(
                equity("eq-1", "run-1", "2026-06-01T00:30:00Z", "100000", "0"),
                equity("eq-2", "run-1", "2026-06-01T01:30:00Z", "112000", "12000")));

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
    }

    private EquityCurveSnapshot equity(String id, String runId, String time, String total, String realized) {
        return new EquityCurveSnapshot(
                id, runId, Instant.parse(time), new BigDecimal(total), new BigDecimal(total),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal(realized), BigDecimal.ZERO, "TEST", Instant.parse(time));
    }

    // ---- 可注入数据的 in-memory 只读仓储 ----

    static final class InMemoryEquityRepo implements EquityCurveSnapshotRepository {
        final Map<String, List<EquityCurveSnapshot>> byRun = new HashMap<>();
        @Override public void insert(EquityCurveSnapshot snapshot) {
            byRun.computeIfAbsent(snapshot.paperRunId(), k -> new ArrayList<>()).add(snapshot);
        }
        @Override public List<EquityCurveSnapshot> listByRunId(String paperRunId) {
            return byRun.getOrDefault(paperRunId, List.of());
        }
    }

    static final class InMemoryRiskRepo implements PaperRiskCheckResultRepository {
        final Map<String, List<PaperRiskCheckResult>> byRun = new HashMap<>();
        @Override public void insert(PaperRiskCheckResult result) {
            byRun.computeIfAbsent(result.paperRunId(), k -> new ArrayList<>()).add(result);
        }
        @Override public List<PaperRiskCheckResult> listByRunId(String paperRunId) {
            return byRun.getOrDefault(paperRunId, List.of());
        }
    }

    static final class InMemoryDailyReportRepo implements PaperRunDailyReportRepository {
        final Map<String, List<PaperRunDailyReport>> byRun = new HashMap<>();
        @Override public void upsert(PaperRunDailyReport report) {
            byRun.computeIfAbsent(report.paperRunId(), k -> new ArrayList<>()).add(report);
        }
        @Override public Optional<PaperRunDailyReport> findById(String reportId) { return Optional.empty(); }
        @Override public Optional<PaperRunDailyReport> findByRunIdAndDate(String paperRunId, LocalDate reportDate) { return Optional.empty(); }
        @Override public List<PaperRunDailyReport> listByRunId(String paperRunId) {
            return byRun.getOrDefault(paperRunId, List.of());
        }
        @Override public int countByRunIdAndDateRange(String paperRunId, Instant start, Instant end) { return 0; }
    }

    static final class InMemoryAlertRepo implements PaperRunAlertRepository {
        final Map<String, List<PaperRunAlert>> byRun = new HashMap<>();
        @Override public void insert(PaperRunAlert alert) {
            byRun.computeIfAbsent(alert.paperRunId(), k -> new ArrayList<>()).add(alert);
        }
        @Override public Optional<PaperRunAlert> findById(String alertId) { return Optional.empty(); }
        @Override public List<PaperRunAlert> listByRunId(String paperRunId, String status, String severity) {
            return byRun.getOrDefault(paperRunId, List.of());
        }
        @Override public boolean updateStatus(String alertId, PaperRunAlertStatus status, String acknowledgedBy, Instant acknowledgedAt, Instant resolvedAt, Instant updatedAt) { return false; }
        @Override public int countByRunIdAndDateRange(String paperRunId, Instant start, Instant end) { return 0; }
        @Override public int countCriticalOpenByRunIdAndDateRange(String paperRunId, Instant start, Instant end) { return 0; }
        @Override public int countByRunIdAndTypeAndDateRange(String paperRunId, String alertType, Instant start, Instant end) { return 0; }
    }
}
