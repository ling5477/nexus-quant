package com.guidinglight.nexusquant.research.application.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.research.domain.BacktestPublishRecord;
import com.guidinglight.nexusquant.research.domain.BacktestRun;
import com.guidinglight.nexusquant.research.domain.BacktestRunStatus;
import com.guidinglight.nexusquant.research.domain.PublishStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlert;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunHeartbeat;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunHeartbeatStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunScheduleFire;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunScheduleFireStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRun;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaperRunMonitorRunServiceTest {

    private final Instant now = Instant.parse("2026-05-21T12:00:00Z");
    private final Clock fixedClock = Clock.fixed(now, ZoneOffset.UTC);

    private PaperTradingRunService runService;
    private PaperRunMonitorService monitorService;
    private PaperRunStabilityCheckServiceTest.InMemoryHeartbeatRepo heartbeatRepo;
    private PaperRunStabilityCheckServiceTest.InMemoryAlertRepo alertRepo;
    private PaperRunStabilityCheckServiceTest.InMemoryScheduleFireRepo fireRepo;
    private PaperRunMonitorRunService service;

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

        heartbeatRepo = new PaperRunStabilityCheckServiceTest.InMemoryHeartbeatRepo();
        alertRepo = new PaperRunStabilityCheckServiceTest.InMemoryAlertRepo();
        fireRepo = new PaperRunStabilityCheckServiceTest.InMemoryScheduleFireRepo();
        var dailyReportRepo = new PaperRunStabilityCheckServiceTest.InMemoryDailyReportRepo();
        monitorService = new PaperRunMonitorService(runService, dailyReportRepo, alertRepo, fixedClock);
        service = new PaperRunMonitorRunService(
                runService, monitorService, heartbeatRepo, alertRepo, fireRepo, fixedClock, 300L);
    }

    @Test
    void runOnceShouldCreateHeartbeatLagAlertWhenNoHeartbeat() {
        PaperTradingRun run = startedRun();

        PaperRunMonitorRunService.MonitorRunOnceResult result = service.runOnce(run.paperRunId());

        assertEquals(1, result.createdAlerts().size());
        PaperRunAlert alert = result.createdAlerts().get(0);
        assertEquals("HEARTBEAT_LAG", alert.alertType());
        assertTrue(alert.message().contains("no heartbeat"));
    }

    @Test
    void runOnceShouldCreateHeartbeatLagAlertWhenLagExceedsThreshold() {
        PaperTradingRun run = startedRun();
        heartbeatRepo.insert(new PaperRunHeartbeat(
                "hbt-1", run.paperRunId(), now.minusSeconds(600),
                PaperRunHeartbeatStatus.OK, null, null, null, 600L, "{}", now.minusSeconds(600)));

        PaperRunMonitorRunService.MonitorRunOnceResult result = service.runOnce(run.paperRunId());

        assertEquals(1, result.createdAlerts().size());
        assertEquals("HEARTBEAT_LAG", result.createdAlerts().get(0).alertType());
    }

    @Test
    void runOnceShouldNotCreateHeartbeatLagAlertWhenLagBelowThreshold() {
        PaperTradingRun run = startedRun();
        heartbeatRepo.insert(new PaperRunHeartbeat(
                "hbt-1", run.paperRunId(), now.minusSeconds(60),
                PaperRunHeartbeatStatus.OK, null, null, null, 60L, "{}", now.minusSeconds(60)));

        PaperRunMonitorRunService.MonitorRunOnceResult result = service.runOnce(run.paperRunId());

        assertEquals(0, result.createdAlerts().size());
    }

    @Test
    void runOnceShouldCreateScheduleFireFailedAlertWhenFailedFire() {
        PaperTradingRun run = startedRun();
        heartbeatRepo.insert(new PaperRunHeartbeat(
                "hbt-1", run.paperRunId(), now.minusSeconds(30),
                PaperRunHeartbeatStatus.OK, null, null, null, 30L, "{}", now.minusSeconds(30)));
        fireRepo.insert(new PaperRunScheduleFire(
                "fir-1", "sch-1", run.paperRunId(), PaperRunScheduleFireStatus.FAILED,
                now.minusSeconds(120), now.minusSeconds(119), 1000L, "{}", "boom", now.minusSeconds(120)));

        PaperRunMonitorRunService.MonitorRunOnceResult result = service.runOnce(run.paperRunId());

        assertEquals(1, result.createdAlerts().size());
        assertEquals("SCHEDULE_FIRE_FAILED", result.createdAlerts().get(0).alertType());
    }

    @Test
    void runOnceShouldDedupeWithinWindow() {
        PaperTradingRun run = startedRun();

        PaperRunMonitorRunService.MonitorRunOnceResult first = service.runOnce(run.paperRunId());
        PaperRunMonitorRunService.MonitorRunOnceResult second = service.runOnce(run.paperRunId());

        assertEquals(1, first.createdAlerts().size());
        assertEquals(0, second.createdAlerts().size());
    }

    @Test
    void runOnceShouldSkipHeartbeatCheckWhenRunNotRunning() {
        PaperTradingRun run = createRun();

        PaperRunMonitorRunService.MonitorRunOnceResult result = service.runOnce(run.paperRunId());

        assertEquals(0, result.createdAlerts().size());
    }

    @Test
    void runOnceShouldEmitBothAlertsWhenBothConditionsFire() {
        PaperTradingRun run = startedRun();
        fireRepo.insert(new PaperRunScheduleFire(
                "fir-1", "sch-1", run.paperRunId(), PaperRunScheduleFireStatus.FAILED,
                now.minusSeconds(120), now.minusSeconds(119), 1000L, "{}", "boom", now.minusSeconds(120)));

        PaperRunMonitorRunService.MonitorRunOnceResult result = service.runOnce(run.paperRunId());

        assertEquals(2, result.createdAlerts().size());
    }

    @Test
    void runOnceShouldReturnPaperRunIdAndCheckedAt() {
        PaperTradingRun run = startedRun();
        PaperRunMonitorRunService.MonitorRunOnceResult result = service.runOnce(run.paperRunId());
        assertEquals(run.paperRunId(), result.paperRunId());
        assertNotNull(result.checkedAt());
    }

    private PaperTradingRun createRun() {
        return runService.create(new PaperTradingRunCreateCommand(
                "pub-001", "SIM", "BINANCE", "SPOT", "BTC-USDT", "1m", null, "tester"));
    }

    private PaperTradingRun startedRun() {
        PaperTradingRun run = createRun();
        return runService.start(run.paperRunId());
    }

    private BacktestPublishRecord samplePublish() {
        return new BacktestPublishRecord(
                "pub-001", "brn-001", "rc-001", "bc-001", "strat-001", "eval-001",
                null, "sv-001", PublishStatus.SUCCEEDED, "Test Publish",
                "{\"publish\":\"snapshot\"}", "{\"version\":\"snapshot\"}", "{\"eval\":\"summary\"}",
                null, null, Instant.parse("2026-05-19T09:00:00Z"),
                Instant.parse("2026-05-19T09:00:00Z"), Instant.parse("2026-05-19T09:00:00Z"));
    }

    private BacktestRun sampleBacktestRun() {
        return new BacktestRun(
                "brn-001", "bc-001", "rc-001", "strat-001", "{}", "sv-001",
                "{\"version\":\"snapshot\"}", "{\"param\":\"snapshot\"}", "{}",
                "{\"config\":\"snapshot\"}", "{\"dataset\":\"snapshot\"}",
                BacktestRunStatus.SUCCEEDED,
                Instant.parse("2026-05-19T08:00:00Z"), Instant.parse("2026-05-19T08:00:00Z"),
                Instant.parse("2026-05-19T08:30:00Z"), null, null, "{}",
                Instant.parse("2026-05-19T08:00:00Z"), Instant.parse("2026-05-19T08:30:00Z"));
    }
}
