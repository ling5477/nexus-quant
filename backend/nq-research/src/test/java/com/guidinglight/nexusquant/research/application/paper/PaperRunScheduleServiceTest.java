package com.guidinglight.nexusquant.research.application.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guidinglight.nexusquant.research.domain.BacktestPublishRecord;
import com.guidinglight.nexusquant.research.domain.BacktestRun;
import com.guidinglight.nexusquant.research.domain.BacktestRunStatus;
import com.guidinglight.nexusquant.research.domain.PublishStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunHeartbeat;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunHeartbeatStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunSchedule;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunScheduleFire;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunScheduleFireStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunScheduleStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRun;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunHeartbeatRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunScheduleFireRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunScheduleRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaperRunScheduleServiceTest {

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-05-21T10:00:00Z"), ZoneOffset.UTC);

    private PaperTradingRunServiceTest.InMemoryRunRepo runRepo;
    private PaperTradingRunService runService;
    private InMemoryScheduleRepo scheduleRepo;
    private InMemoryScheduleFireRepo fireRepo;
    private InMemoryHeartbeatRepo heartbeatRepo;
    private PaperRunScheduleService scheduleService;

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

        scheduleRepo = new InMemoryScheduleRepo();
        fireRepo = new InMemoryScheduleFireRepo();
        heartbeatRepo = new InMemoryHeartbeatRepo();

        scheduleService = new PaperRunScheduleService(
                runService, scheduleRepo, fireRepo, heartbeatRepo, fixedClock
        );
    }

    @Test
    void createScheduleShouldInsertWithEnabledStatus() {
        PaperTradingRun run = createRun();

        PaperRunSchedule schedule = scheduleService.createSchedule(new PaperRunScheduleCreateCommand(
                run.paperRunId(), "smoke-schedule", "0 */5 * * * *", "Asia/Shanghai", null, "tester"
        ));

        assertNotNull(schedule.scheduleId());
        assertEquals(run.paperRunId(), schedule.paperRunId());
        assertEquals("smoke-schedule", schedule.scheduleName());
        assertEquals("0 */5 * * * *", schedule.cronExpr());
        assertEquals(PaperRunScheduleStatus.ENABLED, schedule.status());
        assertEquals("Asia/Shanghai", schedule.timezone());
        assertEquals("{}", schedule.requestJson());
        assertEquals(1, scheduleRepo.records.size());
    }

    @Test
    void createScheduleShouldRejectMissingRun() {
        assertThrows(IllegalArgumentException.class, () -> scheduleService.createSchedule(
                new PaperRunScheduleCreateCommand(
                        "missing", "smoke", "0 0 * * * *", null, null, "tester"
                )
        ));
    }

    @Test
    void createScheduleShouldRejectInvalidCron() {
        PaperTradingRun run = createRun();
        assertThrows(IllegalArgumentException.class, () -> scheduleService.createSchedule(
                new PaperRunScheduleCreateCommand(
                        run.paperRunId(), "smoke", "invalid", null, null, "tester"
                )
        ));
    }

    @Test
    void updateScheduleStatusShouldTransition() {
        PaperTradingRun run = createRun();
        PaperRunSchedule schedule = scheduleService.createSchedule(new PaperRunScheduleCreateCommand(
                run.paperRunId(), "smoke", "0 0 * * * *", null, null, "tester"
        ));

        PaperRunSchedule disabled = scheduleService.updateScheduleStatus(schedule.scheduleId(), "DISABLED");
        assertEquals(PaperRunScheduleStatus.DISABLED, disabled.status());

        PaperRunSchedule paused = scheduleService.updateScheduleStatus(schedule.scheduleId(), "PAUSED");
        assertEquals(PaperRunScheduleStatus.PAUSED, paused.status());
    }

    @Test
    void updateScheduleStatusShouldRejectInvalidStatus() {
        PaperTradingRun run = createRun();
        PaperRunSchedule schedule = scheduleService.createSchedule(new PaperRunScheduleCreateCommand(
                run.paperRunId(), "smoke", "0 0 * * * *", null, null, "tester"
        ));
        assertThrows(IllegalArgumentException.class,
                () -> scheduleService.updateScheduleStatus(schedule.scheduleId(), "BAD"));
    }

    @Test
    void runScheduleOnceShouldWriteSucceededFire() {
        PaperTradingRun run = createRun();
        PaperRunSchedule schedule = scheduleService.createSchedule(new PaperRunScheduleCreateCommand(
                run.paperRunId(), "smoke", "0 0 * * * *", null, null, "tester"
        ));

        PaperRunScheduleFire fire = scheduleService.runScheduleOnce(schedule.scheduleId());

        assertNotNull(fire.fireId());
        assertEquals(schedule.scheduleId(), fire.scheduleId());
        assertEquals(run.paperRunId(), fire.paperRunId());
        assertEquals(PaperRunScheduleFireStatus.SUCCEEDED, fire.status());
        assertNotNull(fire.firedAt());
        assertNotNull(fire.finishedAt());
        assertEquals(1, fireRepo.records.size());
    }

    @Test
    void runScheduleOnceShouldRejectDisabledSchedule() {
        PaperTradingRun run = createRun();
        PaperRunSchedule schedule = scheduleService.createSchedule(new PaperRunScheduleCreateCommand(
                run.paperRunId(), "smoke", "0 0 * * * *", null, null, "tester"
        ));
        scheduleService.updateScheduleStatus(schedule.scheduleId(), "DISABLED");

        assertThrows(IllegalStateException.class,
                () -> scheduleService.runScheduleOnce(schedule.scheduleId()));
    }

    @Test
    void listFiresShouldReturnByScheduleId() {
        PaperTradingRun run = createRun();
        PaperRunSchedule schedule = scheduleService.createSchedule(new PaperRunScheduleCreateCommand(
                run.paperRunId(), "smoke", "0 0 * * * *", null, null, "tester"
        ));
        scheduleService.runScheduleOnce(schedule.scheduleId());

        List<PaperRunScheduleFire> fires = scheduleService.listFires(schedule.scheduleId());
        assertEquals(1, fires.size());
    }

    @Test
    void runHeartbeatOnceShouldWriteRecord() {
        PaperTradingRun run = createAndStartRun();

        PaperRunHeartbeat heartbeat = scheduleService.runHeartbeatOnce(run.paperRunId());

        assertNotNull(heartbeat.heartbeatId());
        assertEquals(run.paperRunId(), heartbeat.paperRunId());
        assertEquals(PaperRunHeartbeatStatus.OK, heartbeat.status());
        assertNotNull(heartbeat.heartbeatTime());
        assertEquals(1, heartbeatRepo.records.size());
    }

    @Test
    void runHeartbeatOnceShouldRecordStoppedWhenRunStopped() {
        PaperTradingRun run = createRun();

        PaperRunHeartbeat heartbeat = scheduleService.runHeartbeatOnce(run.paperRunId());

        assertEquals(PaperRunHeartbeatStatus.UNKNOWN, heartbeat.status());
    }

    @Test
    void listHeartbeatsShouldReturnByRunId() {
        PaperTradingRun run = createAndStartRun();
        scheduleService.runHeartbeatOnce(run.paperRunId());

        List<PaperRunHeartbeat> heartbeats = scheduleService.listHeartbeats(run.paperRunId());
        assertEquals(1, heartbeats.size());
    }

    private PaperTradingRun createRun() {
        return runService.create(new PaperTradingRunCreateCommand(
                "pub-001", "SIM", "BINANCE", "SPOT", "BTC-USDT", "1m", null, "tester"
        ));
    }

    private PaperTradingRun createAndStartRun() {
        PaperTradingRun created = createRun();
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

    static class InMemoryScheduleRepo implements PaperRunScheduleRepository {
        final List<PaperRunSchedule> records = new ArrayList<>();

        @Override public void insert(PaperRunSchedule schedule) { records.add(schedule); }

        @Override public Optional<PaperRunSchedule> findById(String scheduleId) {
            return records.stream().filter(s -> s.scheduleId().equals(scheduleId)).findFirst();
        }

        @Override public List<PaperRunSchedule> list(String paperRunId, String status) {
            return records.stream()
                    .filter(s -> paperRunId == null || s.paperRunId().equals(paperRunId))
                    .filter(s -> status == null || s.status().name().equals(status))
                    .toList();
        }

        @Override public boolean updateStatus(String scheduleId, PaperRunScheduleStatus status, Instant updatedAt) {
            for (int i = 0; i < records.size(); i++) {
                PaperRunSchedule s = records.get(i);
                if (s.scheduleId().equals(scheduleId)) {
                    records.set(i, new PaperRunSchedule(
                            s.scheduleId(), s.paperRunId(), s.scheduleName(), s.cronExpr(),
                            status, s.timezone(), s.nextFireTime(), s.lastFireTime(),
                            s.createdBy(), s.createdAt(), updatedAt, s.requestJson()
                    ));
                    return true;
                }
            }
            return false;
        }

        @Override public boolean updateLastFireTime(String scheduleId, Instant lastFireTime, Instant updatedAt) {
            for (int i = 0; i < records.size(); i++) {
                PaperRunSchedule s = records.get(i);
                if (s.scheduleId().equals(scheduleId)) {
                    records.set(i, new PaperRunSchedule(
                            s.scheduleId(), s.paperRunId(), s.scheduleName(), s.cronExpr(),
                            s.status(), s.timezone(), s.nextFireTime(), lastFireTime,
                            s.createdBy(), s.createdAt(), updatedAt, s.requestJson()
                    ));
                    return true;
                }
            }
            return false;
        }
    }

    static class InMemoryScheduleFireRepo implements PaperRunScheduleFireRepository {
        final List<PaperRunScheduleFire> records = new ArrayList<>();
        @Override public void insert(PaperRunScheduleFire fire) { records.add(fire); }
        @Override public List<PaperRunScheduleFire> listByScheduleId(String scheduleId) {
            return records.stream().filter(f -> f.scheduleId().equals(scheduleId)).toList();
        }
        @Override public List<PaperRunScheduleFire> listByRunIdAndStatus(String paperRunId, String status, java.time.Instant start, java.time.Instant end) {
            return records.stream()
                    .filter(f -> f.paperRunId().equals(paperRunId))
                    .filter(f -> f.status().name().equals(status))
                    .filter(f -> !f.firedAt().isBefore(start) && f.firedAt().isBefore(end))
                    .toList();
        }
        @Override public int countByRunIdAndStatusAndDateRange(String paperRunId, String status, java.time.Instant start, java.time.Instant end) {
            return listByRunIdAndStatus(paperRunId, status, start, end).size();
        }
    }

    static class InMemoryHeartbeatRepo implements PaperRunHeartbeatRepository {
        final List<PaperRunHeartbeat> records = new ArrayList<>();
        @Override public void insert(PaperRunHeartbeat heartbeat) { records.add(heartbeat); }
        @Override public List<PaperRunHeartbeat> listByRunId(String paperRunId) {
            return records.stream().filter(h -> h.paperRunId().equals(paperRunId)).toList();
        }
        @Override public int countByRunIdAndDateRange(String paperRunId, java.time.Instant start, java.time.Instant end) {
            return (int) records.stream()
                    .filter(h -> h.paperRunId().equals(paperRunId))
                    .filter(h -> !h.heartbeatTime().isBefore(start) && h.heartbeatTime().isBefore(end))
                    .count();
        }
        @Override public java.util.Optional<PaperRunHeartbeat> findLatestByRunId(String paperRunId) {
            return records.stream()
                    .filter(h -> h.paperRunId().equals(paperRunId))
                    .max(java.util.Comparator.comparing(PaperRunHeartbeat::heartbeatTime));
        }
    }
}
