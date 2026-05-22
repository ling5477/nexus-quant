package com.guidinglight.nexusquant.research.application.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.research.domain.BacktestPublishRecord;
import com.guidinglight.nexusquant.research.domain.BacktestRun;
import com.guidinglight.nexusquant.research.domain.BacktestRunStatus;
import com.guidinglight.nexusquant.research.domain.PublishStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunRecoveryEvent;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunRecoveryStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunRecoveryType;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRun;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunRecoveryEventRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaperRunRecoveryServiceTest {

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-05-21T12:00:00Z"), ZoneOffset.UTC);

    private PaperTradingRunService runService;
    private InMemoryRecoveryEventRepo recoveryRepo;
    private PaperRunRecoveryService recoveryService;

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
                publishRepo, backtestRunRepo, fixedClock
        );
        recoveryRepo = new InMemoryRecoveryEventRepo();
        recoveryService = new PaperRunRecoveryService(runService, recoveryRepo, fixedClock);
    }

    @Test
    void recoverShouldRecordManualEvent() {
        PaperTradingRun run = createRun();

        PaperRunRecoveryEvent event = recoveryService.recover(
                new PaperRunRecoverCommand(run.paperRunId(), "manual test", null));

        assertNotNull(event.recoveryEventId());
        assertTrue(event.recoveryEventId().startsWith("rec-"));
        assertEquals(PaperRunRecoveryType.MANUAL_RECOVER, event.recoveryType());
        assertEquals(PaperRunRecoveryStatus.SUCCEEDED, event.status());
        assertEquals("manual test", event.reason());
        assertNotNull(event.startedAt());
        assertNotNull(event.finishedAt());
        assertEquals(1, recoveryRepo.records.size());
    }

    @Test
    void recoverShouldRejectMissingRun() {
        assertThrows(IllegalArgumentException.class, () ->
                recoveryService.recover(new PaperRunRecoverCommand("missing", null, null))
        );
    }

    @Test
    void recoverShouldSkipWhenRunStopped() {
        PaperTradingRun run = createRun();
        runService.start(run.paperRunId());
        runService.stop(run.paperRunId());

        PaperRunRecoveryEvent event = recoveryService.recover(
                new PaperRunRecoverCommand(run.paperRunId(), "after stop", null));

        assertEquals(PaperRunRecoveryStatus.SKIPPED, event.status());
    }

    @Test
    void retryFailedStepShouldRecordRetryEvent() {
        PaperTradingRun run = createRun();

        PaperRunRecoveryEvent event = recoveryService.retryFailedStep(
                new PaperRunRetryFailedStepCommand(run.paperRunId(), "settle", "retry now", null));

        assertEquals(PaperRunRecoveryType.RETRY_FAILED_STEP, event.recoveryType());
        assertEquals(PaperRunRecoveryStatus.SUCCEEDED, event.status());
        assertTrue(event.requestJson().contains("settle"));
    }

    @Test
    void listRecoveryEventsShouldFilterByType() {
        PaperTradingRun run = createRun();
        recoveryService.recover(new PaperRunRecoverCommand(run.paperRunId(), "a", null));
        recoveryService.retryFailedStep(new PaperRunRetryFailedStepCommand(run.paperRunId(), "x", "b", null));

        List<PaperRunRecoveryEvent> manualEvents = recoveryService.listRecoveryEvents(
                run.paperRunId(), "MANUAL_RECOVER", null);
        assertEquals(1, manualEvents.size());

        List<PaperRunRecoveryEvent> retryEvents = recoveryService.listRecoveryEvents(
                run.paperRunId(), "RETRY_FAILED_STEP", null);
        assertEquals(1, retryEvents.size());

        List<PaperRunRecoveryEvent> all = recoveryService.listRecoveryEvents(run.paperRunId(), null, null);
        assertEquals(2, all.size());
    }

    @Test
    void listRecoveryEventsShouldRejectInvalidType() {
        PaperTradingRun run = createRun();
        assertThrows(IllegalArgumentException.class, () ->
                recoveryService.listRecoveryEvents(run.paperRunId(), "INVALID", null)
        );
    }

    @Test
    void listRecoveryEventsShouldRejectInvalidStatus() {
        PaperTradingRun run = createRun();
        assertThrows(IllegalArgumentException.class, () ->
                recoveryService.listRecoveryEvents(run.paperRunId(), null, "INVALID")
        );
    }

    @Test
    void recordHeartbeatLagRecoverShouldRecordEvent() {
        PaperTradingRun run = createRun();
        PaperRunRecoveryEvent event = recoveryService.recordHeartbeatLagRecover(
                run.paperRunId(), "lag > 300s", null);
        assertEquals(PaperRunRecoveryType.HEARTBEAT_LAG_RECOVER, event.recoveryType());
    }

    @Test
    void recordScheduleFireRecoverShouldRecordEvent() {
        PaperTradingRun run = createRun();
        PaperRunRecoveryEvent event = recoveryService.recordScheduleFireRecover(
                run.paperRunId(), "fire failed", null);
        assertEquals(PaperRunRecoveryType.SCHEDULE_FIRE_RECOVER, event.recoveryType());
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

    static class InMemoryRecoveryEventRepo implements PaperRunRecoveryEventRepository {
        final List<PaperRunRecoveryEvent> records = new ArrayList<>();

        @Override public void insert(PaperRunRecoveryEvent event) { records.add(event); }

        @Override public Optional<PaperRunRecoveryEvent> findById(String id) {
            return records.stream().filter(e -> e.recoveryEventId().equals(id)).findFirst();
        }

        @Override public List<PaperRunRecoveryEvent> listByRunId(String paperRunId, String type, String status) {
            return records.stream()
                    .filter(e -> e.paperRunId().equals(paperRunId))
                    .filter(e -> type == null || e.recoveryType().name().equals(type))
                    .filter(e -> status == null || e.status().name().equals(status))
                    .toList();
        }

        @Override public int countByRunIdAndDateRange(String paperRunId, Instant start, Instant end) {
            return (int) records.stream()
                    .filter(e -> e.paperRunId().equals(paperRunId))
                    .filter(e -> !e.createdAt().isBefore(start) && e.createdAt().isBefore(end))
                    .count();
        }
    }
}
