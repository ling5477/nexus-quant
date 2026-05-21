package com.guidinglight.nexusquant.research.application.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.guidinglight.nexusquant.research.domain.BacktestPublishRecord;
import com.guidinglight.nexusquant.research.domain.BacktestRun;
import com.guidinglight.nexusquant.research.domain.BacktestRunStatus;
import com.guidinglight.nexusquant.research.domain.PublishStatus;
import com.guidinglight.nexusquant.research.domain.paper.EmergencyStopEvent;
import com.guidinglight.nexusquant.research.domain.paper.EmergencyStopStatus;
import com.guidinglight.nexusquant.research.domain.paper.EmergencyStopTriggerType;
import com.guidinglight.nexusquant.research.domain.paper.EquityCurveSnapshot;
import com.guidinglight.nexusquant.research.domain.paper.PaperRiskCheckResult;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRun;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRunStatus;
import com.guidinglight.nexusquant.research.domain.paper.PositionCurveSnapshot;
import com.guidinglight.nexusquant.research.domain.paper.RiskCheckSeverity;
import com.guidinglight.nexusquant.research.domain.paper.RiskCheckStatus;
import com.guidinglight.nexusquant.research.domain.paper.TradeReplayRecord;
import com.guidinglight.nexusquant.research.domain.paper.port.EmergencyStopEventRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.EquityCurveSnapshotRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRiskCheckResultRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PositionCurveSnapshotRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.TradeReplayRecordRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaperTradingMonitorServiceTest {

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-05-20T10:00:00Z"), ZoneOffset.UTC);

    private PaperTradingRunServiceTest.InMemoryRunRepo runRepo;
    private PaperTradingRunService runService;
    private InMemoryRiskCheckRepo riskCheckRepo;
    private InMemoryEquityCurveRepo equityCurveRepo;
    private InMemoryPositionCurveRepo positionCurveRepo;
    private InMemoryReplayRepo replayRepo;
    private InMemoryEmergencyStopRepo emergencyStopRepo;
    private PaperTradingMonitorService monitorService;

    @BeforeEach
    void setUp() {
        runRepo = new PaperTradingRunServiceTest.InMemoryRunRepo();
        var publishRepo = new PaperTradingRunServiceTest.InMemoryPublishRepo();
        var backtestRunRepo = new PaperTradingRunServiceTest.InMemoryBacktestRunRepo();
        publishRepo.records.add(samplePublish());
        backtestRunRepo.runs.add(sampleBacktestRun());

        runService = new PaperTradingRunService(
                runRepo, new PaperTradingRunServiceTest.InMemoryOrderRepo(),
                new PaperTradingRunServiceTest.InMemoryTradeRepo(),
                new PaperTradingRunServiceTest.InMemoryPositionRepo(),
                publishRepo, backtestRunRepo, fixedClock
        );

        riskCheckRepo = new InMemoryRiskCheckRepo();
        equityCurveRepo = new InMemoryEquityCurveRepo();
        positionCurveRepo = new InMemoryPositionCurveRepo();
        replayRepo = new InMemoryReplayRepo();
        emergencyStopRepo = new InMemoryEmergencyStopRepo();

        monitorService = new PaperTradingMonitorService(
                runService, riskCheckRepo, equityCurveRepo,
                positionCurveRepo, replayRepo, emergencyStopRepo, fixedClock
        );
    }

    @Test
    void runRiskCheckOnceShouldInsertPassedResult() {
        PaperTradingRun run = createAndStartRun();

        PaperRiskCheckResult result = monitorService.runRiskCheckOnce(run.paperRunId());

        assertNotNull(result.riskResultId());
        assertEquals(run.paperRunId(), result.paperRunId());
        assertEquals("BASIC_HEALTH_CHECK", result.checkType());
        assertEquals(RiskCheckStatus.PASSED, result.status());
        assertEquals(RiskCheckSeverity.LOW, result.severity());
        assertEquals(1, riskCheckRepo.records.size());
    }

    @Test
    void listRiskResultsShouldReturnEmpty() {
        PaperTradingRun run = createAndStartRun();

        List<PaperRiskCheckResult> results = monitorService.listRiskResults(run.paperRunId());

        assertEquals(0, results.size());
    }

    @Test
    void emergencyStopShouldApplyWhenRunning() {
        PaperTradingRun run = createAndStartRun();

        EmergencyStopEvent event = monitorService.triggerEmergencyStop(
                run.paperRunId(), "MANUAL", "test stop", "admin"
        );

        assertEquals(EmergencyStopStatus.APPLIED, event.status());
        assertEquals(EmergencyStopTriggerType.MANUAL, event.triggerType());
        assertEquals("test stop", event.reason());
        assertEquals("admin", event.triggeredBy());
        assertNotNull(event.resolvedAt());
        assertEquals(1, emergencyStopRepo.records.size());

        PaperTradingRun stopped = runService.getById(run.paperRunId());
        assertEquals(PaperTradingRunStatus.STOPPED, stopped.status());
    }

    @Test
    void emergencyStopShouldFailWhenNotRunning() {
        PaperTradingRun run = runService.create(new PaperTradingRunCreateCommand(
                "pub-001", "SIM", "BINANCE", "SPOT", "BTC-USDT", "1m", null, "tester"
        ));

        EmergencyStopEvent event = monitorService.triggerEmergencyStop(
                run.paperRunId(), "MANUAL", "test stop", "admin"
        );

        assertEquals(EmergencyStopStatus.FAILED, event.status());
        assertNull(event.resolvedAt());
        assertEquals(PaperTradingRunStatus.CREATED, runService.getById(run.paperRunId()).status());
    }

    @Test
    void listEmergencyStopsShouldReturnEmpty() {
        PaperTradingRun run = createAndStartRun();

        List<EmergencyStopEvent> events = monitorService.listEmergencyStops(run.paperRunId());

        assertEquals(0, events.size());
    }

    private PaperTradingRun createAndStartRun() {
        PaperTradingRun created = runService.create(new PaperTradingRunCreateCommand(
                "pub-001", "SIM", "BINANCE", "SPOT", "BTC-USDT", "1m", null, "tester"
        ));
        return runService.start(created.paperRunId());
    }

    private BacktestPublishRecord samplePublish() {
        return new BacktestPublishRecord(
                "pub-001", "brn-001", "rc-001", "bc-001", "strat-001", "eval-001",
                null, "sv-001", PublishStatus.SUCCEEDED, "Test Publish",
                "{\"publish\":\"snapshot\"}", "{\"version\":\"snapshot\"}", "{\"eval\":\"summary\"}",
                null, null, Instant.parse("2026-05-19T09:00:00Z"),
                Instant.parse("2026-05-19T09:00:00Z"), Instant.parse("2026-05-19T09:00:00Z")
        );
    }

    private BacktestRun sampleBacktestRun() {
        return new BacktestRun(
                "brn-001", "bc-001", "rc-001", "strat-001", "{}", "sv-001",
                "{\"version\":\"snapshot\"}", "{\"param\":\"snapshot\"}", "{}",
                "{\"config\":\"snapshot\"}", "{\"dataset\":\"snapshot\"}",
                BacktestRunStatus.SUCCEEDED,
                Instant.parse("2026-05-19T08:00:00Z"), Instant.parse("2026-05-19T08:00:00Z"),
                Instant.parse("2026-05-19T08:30:00Z"), null, null, "{}",
                Instant.parse("2026-05-19T08:00:00Z"), Instant.parse("2026-05-19T08:30:00Z")
        );
    }

    static class InMemoryRiskCheckRepo implements PaperRiskCheckResultRepository {
        final List<PaperRiskCheckResult> records = new ArrayList<>();
        @Override public void insert(PaperRiskCheckResult result) { records.add(result); }
        @Override public List<PaperRiskCheckResult> listByRunId(String paperRunId) {
            return records.stream().filter(r -> r.paperRunId().equals(paperRunId)).toList();
        }
    }

    static class InMemoryEquityCurveRepo implements EquityCurveSnapshotRepository {
        final List<EquityCurveSnapshot> records = new ArrayList<>();
        @Override public void insert(EquityCurveSnapshot snapshot) { records.add(snapshot); }
        @Override public List<EquityCurveSnapshot> listByRunId(String paperRunId) {
            return records.stream().filter(r -> r.paperRunId().equals(paperRunId)).toList();
        }
    }

    static class InMemoryPositionCurveRepo implements PositionCurveSnapshotRepository {
        final List<PositionCurveSnapshot> records = new ArrayList<>();
        @Override public void insert(PositionCurveSnapshot snapshot) { records.add(snapshot); }
        @Override public List<PositionCurveSnapshot> listByRunId(String paperRunId) {
            return records.stream().filter(r -> r.paperRunId().equals(paperRunId)).toList();
        }
    }

    static class InMemoryReplayRepo implements TradeReplayRecordRepository {
        final List<TradeReplayRecord> records = new ArrayList<>();
        @Override public void insert(TradeReplayRecord record) { records.add(record); }
        @Override public List<TradeReplayRecord> listByRunId(String paperRunId) {
            return records.stream().filter(r -> r.paperRunId().equals(paperRunId)).toList();
        }
    }

    static class InMemoryEmergencyStopRepo implements EmergencyStopEventRepository {
        final List<EmergencyStopEvent> records = new ArrayList<>();
        @Override public void insert(EmergencyStopEvent event) { records.add(event); }
        @Override public List<EmergencyStopEvent> listByRunId(String paperRunId) {
            return records.stream().filter(r -> r.paperRunId().equals(paperRunId)).toList();
        }
        @Override public boolean updateStatus(String emergencyStopId, EmergencyStopStatus status, Instant resolvedAt, String resultJson) {
            return true;
        }
    }
}
