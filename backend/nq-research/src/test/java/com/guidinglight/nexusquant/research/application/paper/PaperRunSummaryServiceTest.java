package com.guidinglight.nexusquant.research.application.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.research.domain.BacktestPublishRecord;
import com.guidinglight.nexusquant.research.domain.BacktestRun;
import com.guidinglight.nexusquant.research.domain.BacktestRunStatus;
import com.guidinglight.nexusquant.research.domain.PublishStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRun;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PaperRunSummaryService 集成口径单测：复用既有 in-memory repo 装配真实读服务，
 * 验证 summary 编排只依赖读服务（无 adapter / exchange / credential / 外呼），
 * 空事实返回稳定结构，run 不存在抛 IllegalArgumentException（上层映射 404）。
 */
class PaperRunSummaryServiceTest {

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-06-24T02:00:00Z"), ZoneOffset.UTC);

    private PaperTradingRunService runService;
    private PaperRunSummaryService summaryService;

    @BeforeEach
    void setUp() {
        var runRepo = new PaperTradingRunServiceTest.InMemoryRunRepo();
        var publishRepo = new PaperTradingRunServiceTest.InMemoryPublishRepo();
        var backtestRunRepo = new PaperTradingRunServiceTest.InMemoryBacktestRunRepo();
        publishRepo.records.add(samplePublish());
        backtestRunRepo.runs.add(sampleBacktestRun());

        runService = new PaperTradingRunService(
                runRepo, new PaperTradingRunServiceTest.InMemoryOrderRepo(),
                new PaperTradingRunServiceTest.InMemoryTradeRepo(),
                new PaperTradingRunServiceTest.InMemoryPositionRepo(),
                publishRepo, backtestRunRepo, fixedClock);

        var monitorService = new PaperTradingMonitorService(
                runService,
                new PaperTradingMonitorServiceTest.InMemoryRiskCheckRepo(),
                new PaperTradingMonitorServiceTest.InMemoryEquityCurveRepo(),
                new PaperTradingMonitorServiceTest.InMemoryPositionCurveRepo(),
                new PaperTradingMonitorServiceTest.InMemoryReplayRepo(),
                new PaperTradingMonitorServiceTest.InMemoryEmergencyStopRepo(),
                fixedClock);

        var paperRunMonitorService = new PaperRunMonitorService(
                runService,
                new PaperRunMonitorServiceTest.InMemoryDailyReportRepo(),
                new PaperRunMonitorServiceTest.InMemoryAlertRepo(),
                fixedClock);

        var recoveryService = new PaperRunRecoveryService(
                runService, new PaperRunRecoveryServiceTest.InMemoryRecoveryEventRepo(), fixedClock);

        summaryService = new PaperRunSummaryService(
                runService, monitorService, paperRunMonitorService, recoveryService, fixedClock);
    }

    @Test
    void summarizeShouldReturnStableStructureForCreatedRunWithoutFacts() {
        PaperTradingRun run = createRun();

        PaperRunSummary summary = summaryService.summarize(run.paperRunId());

        assertNotNull(summary);
        assertEquals(run.paperRunId(), summary.run().paperRunId());
        assertEquals(0, summary.counts().orderCount());
        assertEquals(0, summary.counts().tradeCount());
        assertEquals(0, summary.counts().positionCount());
        assertEquals(0, summary.counts().openAlertCount());
        assertEquals(0, summary.counts().recoveryEventCount());
        assertNull(summary.resultReview().netPnl());
        // CREATED 且无订单 → 尚未启动。
        assertEquals("尚未启动", summary.resultReview().conclusion());
        // 空事实下诊断仍稳定返回（NO_ORDER / DATA_INSUFFICIENT），不为 null。
        var types = summary.diagnoses().stream()
                .map(PaperRunSummary.Diagnosis::type).collect(Collectors.toSet());
        assertTrue(types.contains("NO_ORDER"));
        assertTrue(types.contains("DATA_INSUFFICIENT"));
        // 时间线至少包含创建事件。
        assertTrue(summary.timeline().stream().anyMatch(t -> "RUN_CREATED".equals(t.type())));
    }

    @Test
    void summarizeShouldThrowForMissingRun() {
        assertThrows(IllegalArgumentException.class, () -> summaryService.summarize("missing-run"));
    }

    private PaperTradingRun createRun() {
        return runService.create(new PaperTradingRunCreateCommand(
                "pub-001", "SIM", "BINANCE", "SPOT", "BTC-USDT", "1m", null, "tester"));
    }

    private BacktestPublishRecord samplePublish() {
        return new BacktestPublishRecord(
                "pub-001", "brn-001", "rc-001", "bc-001", "strat-001", "eval-001",
                null, "sv-001", PublishStatus.SUCCEEDED, "Test Publish",
                "{\"publish\":\"snapshot\"}", "{\"version\":\"snapshot\"}", "{\"eval\":\"summary\"}",
                null, null, Instant.parse("2026-06-23T09:00:00Z"),
                Instant.parse("2026-06-23T09:00:00Z"), Instant.parse("2026-06-23T09:00:00Z"));
    }

    private BacktestRun sampleBacktestRun() {
        return new BacktestRun(
                "brn-001", "bc-001", "rc-001", "strat-001", "{}", "sv-001",
                "{\"version\":\"snapshot\"}", "{\"param\":\"snapshot\"}", "{}",
                "{\"config\":\"snapshot\"}", "{\"dataset\":\"snapshot\"}",
                BacktestRunStatus.SUCCEEDED,
                Instant.parse("2026-06-23T08:00:00Z"), Instant.parse("2026-06-23T08:00:00Z"),
                Instant.parse("2026-06-23T08:30:00Z"), null, null, "{}",
                Instant.parse("2026-06-23T08:00:00Z"), Instant.parse("2026-06-23T08:30:00Z"));
    }
}
