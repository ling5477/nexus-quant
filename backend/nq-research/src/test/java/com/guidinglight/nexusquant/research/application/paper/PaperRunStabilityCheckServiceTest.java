package com.guidinglight.nexusquant.research.application.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.research.domain.BacktestPublishRecord;
import com.guidinglight.nexusquant.research.domain.BacktestRun;
import com.guidinglight.nexusquant.research.domain.BacktestRunStatus;
import com.guidinglight.nexusquant.research.domain.PublishStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlert;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlertSeverity;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlertStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunHeartbeat;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunHeartbeatStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunScheduleFire;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunScheduleFireStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunStabilityCheck;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunStabilityCheckStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRun;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunAlertRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunHeartbeatRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunScheduleFireRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunStabilityCheckRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaperRunStabilityCheckServiceTest {

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-05-21T12:00:00Z"), ZoneOffset.UTC);
    private final Instant windowStart = Instant.parse("2026-05-20T00:00:00Z");
    private final Instant windowEnd = Instant.parse("2026-05-21T00:00:00Z");

    private PaperTradingRunService runService;
    private InMemoryStabilityRepo stabilityRepo;
    private InMemoryHeartbeatRepo heartbeatRepo;
    private InMemoryAlertRepo alertRepo;
    private InMemoryScheduleFireRepo fireRepo;
    private PaperRunRecoveryServiceTest.InMemoryRecoveryEventRepo recoveryRepo;
    private InMemoryDailyReportRepo dailyReportRepo;
    private PaperRunStabilityCheckService service;

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

        stabilityRepo = new InMemoryStabilityRepo();
        heartbeatRepo = new InMemoryHeartbeatRepo();
        alertRepo = new InMemoryAlertRepo();
        fireRepo = new InMemoryScheduleFireRepo();
        recoveryRepo = new PaperRunRecoveryServiceTest.InMemoryRecoveryEventRepo();
        dailyReportRepo = new InMemoryDailyReportRepo();

        service = new PaperRunStabilityCheckService(
                runService, stabilityRepo, heartbeatRepo, alertRepo, fireRepo,
                recoveryRepo, dailyReportRepo, fixedClock);
    }

    @Test
    void generateShouldReturnPassedWhenAllClean() {
        PaperTradingRun run = createRun();
        addHeartbeat(run.paperRunId(), Instant.parse("2026-05-20T12:00:00Z"));

        PaperRunStabilityCheck check = service.generate(new PaperRunStabilityCheckGenerateCommand(
                run.paperRunId(), windowStart, windowEnd));

        assertNotNull(check.stabilityCheckId());
        assertTrue(check.stabilityCheckId().startsWith("stb-"));
        assertEquals(PaperRunStabilityCheckStatus.PASSED, check.status());
        assertEquals(0, check.uptimeRatio().compareTo(new BigDecimal("1.0000")));
        assertEquals(1, check.heartbeatCount());
        assertEquals(0, check.failedFireCount());
    }

    @Test
    void generateShouldReturnFailedWhenNoHeartbeat() {
        PaperTradingRun run = createRun();

        PaperRunStabilityCheck check = service.generate(new PaperRunStabilityCheckGenerateCommand(
                run.paperRunId(), windowStart, windowEnd));

        assertEquals(PaperRunStabilityCheckStatus.FAILED, check.status());
        assertEquals(0, check.heartbeatCount());
    }

    @Test
    void generateShouldReturnFailedWhenCriticalOpenAlert() {
        PaperTradingRun run = createRun();
        addHeartbeat(run.paperRunId(), Instant.parse("2026-05-20T12:00:00Z"));
        addAlert(run.paperRunId(), PaperRunAlertSeverity.CRITICAL, PaperRunAlertStatus.OPEN,
                Instant.parse("2026-05-20T13:00:00Z"));

        PaperRunStabilityCheck check = service.generate(new PaperRunStabilityCheckGenerateCommand(
                run.paperRunId(), windowStart, windowEnd));

        assertEquals(PaperRunStabilityCheckStatus.FAILED, check.status());
    }

    @Test
    void generateShouldReturnFailedWhenFailedFire() {
        PaperTradingRun run = createRun();
        addHeartbeat(run.paperRunId(), Instant.parse("2026-05-20T12:00:00Z"));
        addFire(run.paperRunId(), PaperRunScheduleFireStatus.FAILED,
                Instant.parse("2026-05-20T13:00:00Z"));

        PaperRunStabilityCheck check = service.generate(new PaperRunStabilityCheckGenerateCommand(
                run.paperRunId(), windowStart, windowEnd));

        assertEquals(PaperRunStabilityCheckStatus.FAILED, check.status());
        assertEquals(1, check.failedFireCount());
    }

    @Test
    void generateShouldReturnPartialWhenAlertOrRecoveryButNoCritical() {
        PaperTradingRun run = createRun();
        addHeartbeat(run.paperRunId(), Instant.parse("2026-05-20T12:00:00Z"));
        addAlert(run.paperRunId(), PaperRunAlertSeverity.LOW, PaperRunAlertStatus.OPEN,
                Instant.parse("2026-05-20T13:00:00Z"));

        PaperRunStabilityCheck check = service.generate(new PaperRunStabilityCheckGenerateCommand(
                run.paperRunId(), windowStart, windowEnd));

        assertEquals(PaperRunStabilityCheckStatus.PARTIAL, check.status());
    }

    @Test
    void generateShouldBeIdempotentBySameWindow() {
        PaperTradingRun run = createRun();
        addHeartbeat(run.paperRunId(), Instant.parse("2026-05-20T12:00:00Z"));

        PaperRunStabilityCheck first = service.generate(new PaperRunStabilityCheckGenerateCommand(
                run.paperRunId(), windowStart, windowEnd));
        PaperRunStabilityCheck second = service.generate(new PaperRunStabilityCheckGenerateCommand(
                run.paperRunId(), windowStart, windowEnd));

        assertEquals(first.checkWindowStart(), second.checkWindowStart());
        assertEquals(first.checkWindowEnd(), second.checkWindowEnd());
        assertEquals(1, stabilityRepo.records.size());
    }

    @Test
    void generateShouldRejectInvalidWindow() {
        PaperTradingRun run = createRun();
        assertThrows(IllegalArgumentException.class, () ->
                service.generate(new PaperRunStabilityCheckGenerateCommand(
                        run.paperRunId(), windowEnd, windowStart))
        );
        assertThrows(IllegalArgumentException.class, () ->
                service.generate(new PaperRunStabilityCheckGenerateCommand(
                        run.paperRunId(), windowStart, windowStart))
        );
    }

    @Test
    void generateShouldRejectMissingRun() {
        assertThrows(IllegalArgumentException.class, () ->
                service.generate(new PaperRunStabilityCheckGenerateCommand(
                        "missing", windowStart, windowEnd))
        );
    }

    @Test
    void listShouldFilterByStatus() {
        PaperTradingRun run = createRun();
        addHeartbeat(run.paperRunId(), Instant.parse("2026-05-20T12:00:00Z"));
        service.generate(new PaperRunStabilityCheckGenerateCommand(run.paperRunId(), windowStart, windowEnd));

        List<PaperRunStabilityCheck> passed = service.list(run.paperRunId(), "PASSED");
        assertEquals(1, passed.size());

        List<PaperRunStabilityCheck> failed = service.list(run.paperRunId(), "FAILED");
        assertEquals(0, failed.size());

        List<PaperRunStabilityCheck> all = service.list(run.paperRunId(), null);
        assertEquals(1, all.size());
    }

    @Test
    void listShouldRejectInvalidStatus() {
        PaperTradingRun run = createRun();
        assertThrows(IllegalArgumentException.class, () ->
                service.list(run.paperRunId(), "INVALID")
        );
    }

    private PaperTradingRun createRun() {
        return runService.create(new PaperTradingRunCreateCommand(
                "pub-001", "SIM", "BINANCE", "SPOT", "BTC-USDT", "1m", null, "tester"));
    }

    private void addHeartbeat(String paperRunId, Instant time) {
        heartbeatRepo.insert(new PaperRunHeartbeat(
                "hbt-" + UUID.randomUUID(), paperRunId, time,
                PaperRunHeartbeatStatus.OK, null, null, null, 0L, "{}", time));
    }

    private void addAlert(String paperRunId, PaperRunAlertSeverity severity, PaperRunAlertStatus status, Instant time) {
        alertRepo.insert(new PaperRunAlert(
                "alt-" + UUID.randomUUID(), paperRunId, "SYSTEM_NOTICE",
                severity, status, "test", null, "MANUAL", "{}", null, null, null, time, time));
    }

    private void addFire(String paperRunId, PaperRunScheduleFireStatus status, Instant time) {
        fireRepo.insert(new PaperRunScheduleFire(
                "fir-" + UUID.randomUUID(), "sch-001", paperRunId, status,
                time, time, 100L, "{}", null, time));
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

    static class InMemoryStabilityRepo implements PaperRunStabilityCheckRepository {
        final List<PaperRunStabilityCheck> records = new ArrayList<>();

        @Override public void upsert(PaperRunStabilityCheck c) {
            records.removeIf(r -> r.paperRunId().equals(c.paperRunId())
                    && r.checkWindowStart().equals(c.checkWindowStart())
                    && r.checkWindowEnd().equals(c.checkWindowEnd()));
            records.add(c);
        }

        @Override public Optional<PaperRunStabilityCheck> findById(String id) {
            return records.stream().filter(c -> c.stabilityCheckId().equals(id)).findFirst();
        }

        @Override public Optional<PaperRunStabilityCheck> findByRunIdAndWindow(String paperRunId, Instant s, Instant e) {
            return records.stream().filter(c -> c.paperRunId().equals(paperRunId)
                    && c.checkWindowStart().equals(s) && c.checkWindowEnd().equals(e)).findFirst();
        }

        @Override public List<PaperRunStabilityCheck> listByRunId(String paperRunId, String status) {
            return records.stream()
                    .filter(c -> c.paperRunId().equals(paperRunId))
                    .filter(c -> status == null || c.status().name().equals(status))
                    .sorted(Comparator.comparing(PaperRunStabilityCheck::createdAt).reversed())
                    .toList();
        }
    }

    static class InMemoryHeartbeatRepo implements PaperRunHeartbeatRepository {
        final List<PaperRunHeartbeat> records = new ArrayList<>();

        @Override public void insert(PaperRunHeartbeat h) { records.add(h); }

        @Override public List<PaperRunHeartbeat> listByRunId(String paperRunId) {
            return records.stream().filter(h -> h.paperRunId().equals(paperRunId)).toList();
        }

        @Override public int countByRunIdAndDateRange(String paperRunId, Instant start, Instant end) {
            return (int) records.stream()
                    .filter(h -> h.paperRunId().equals(paperRunId))
                    .filter(h -> !h.heartbeatTime().isBefore(start) && h.heartbeatTime().isBefore(end))
                    .count();
        }

        @Override public Optional<PaperRunHeartbeat> findLatestByRunId(String paperRunId) {
            return records.stream()
                    .filter(h -> h.paperRunId().equals(paperRunId))
                    .max(Comparator.comparing(PaperRunHeartbeat::heartbeatTime));
        }
    }

    static class InMemoryAlertRepo implements PaperRunAlertRepository {
        final List<PaperRunAlert> records = new ArrayList<>();

        @Override public void insert(PaperRunAlert alert) { records.add(alert); }

        @Override public Optional<PaperRunAlert> findById(String id) {
            return records.stream().filter(a -> a.alertId().equals(id)).findFirst();
        }

        @Override public List<PaperRunAlert> listByRunId(String paperRunId, String status, String severity) {
            return records.stream()
                    .filter(a -> a.paperRunId().equals(paperRunId))
                    .filter(a -> status == null || a.status().name().equals(status))
                    .filter(a -> severity == null || a.severity().name().equals(severity))
                    .toList();
        }

        @Override public boolean updateStatus(String id, PaperRunAlertStatus status, String ackBy, Instant ackAt, Instant resAt, Instant updAt) {
            return false;
        }

        @Override public int countByRunIdAndDateRange(String paperRunId, Instant start, Instant end) {
            return (int) records.stream()
                    .filter(a -> a.paperRunId().equals(paperRunId))
                    .filter(a -> !a.createdAt().isBefore(start) && a.createdAt().isBefore(end))
                    .count();
        }

        @Override public int countCriticalOpenByRunIdAndDateRange(String paperRunId, Instant start, Instant end) {
            return (int) records.stream()
                    .filter(a -> a.paperRunId().equals(paperRunId))
                    .filter(a -> a.severity() == PaperRunAlertSeverity.CRITICAL)
                    .filter(a -> a.status() == PaperRunAlertStatus.OPEN)
                    .filter(a -> !a.createdAt().isBefore(start) && a.createdAt().isBefore(end))
                    .count();
        }

        @Override public int countByRunIdAndTypeAndDateRange(String paperRunId, String type, Instant start, Instant end) {
            return (int) records.stream()
                    .filter(a -> a.paperRunId().equals(paperRunId))
                    .filter(a -> a.alertType().equals(type))
                    .filter(a -> !a.createdAt().isBefore(start) && a.createdAt().isBefore(end))
                    .count();
        }
    }

    static class InMemoryScheduleFireRepo implements PaperRunScheduleFireRepository {
        final List<PaperRunScheduleFire> records = new ArrayList<>();

        @Override public void insert(PaperRunScheduleFire f) { records.add(f); }

        @Override public List<PaperRunScheduleFire> listByScheduleId(String scheduleId) {
            return records.stream().filter(f -> f.scheduleId().equals(scheduleId)).toList();
        }

        @Override public List<PaperRunScheduleFire> listByRunIdAndStatus(String paperRunId, String status, Instant start, Instant end) {
            return records.stream()
                    .filter(f -> f.paperRunId().equals(paperRunId))
                    .filter(f -> f.status().name().equals(status))
                    .filter(f -> !f.firedAt().isBefore(start) && f.firedAt().isBefore(end))
                    .toList();
        }

        @Override public int countByRunIdAndStatusAndDateRange(String paperRunId, String status, Instant start, Instant end) {
            return listByRunIdAndStatus(paperRunId, status, start, end).size();
        }
    }

    static class InMemoryDailyReportRepo implements com.guidinglight.nexusquant.research.domain.paper.port.PaperRunDailyReportRepository {
        final List<com.guidinglight.nexusquant.research.domain.paper.PaperRunDailyReport> records = new ArrayList<>();

        @Override public void upsert(com.guidinglight.nexusquant.research.domain.paper.PaperRunDailyReport r) { records.add(r); }

        @Override public Optional<com.guidinglight.nexusquant.research.domain.paper.PaperRunDailyReport> findById(String id) {
            return Optional.empty();
        }

        @Override public Optional<com.guidinglight.nexusquant.research.domain.paper.PaperRunDailyReport> findByRunIdAndDate(
                String paperRunId, java.time.LocalDate date) {
            return Optional.empty();
        }

        @Override public List<com.guidinglight.nexusquant.research.domain.paper.PaperRunDailyReport> listByRunId(String paperRunId) {
            return records.stream().filter(r -> r.paperRunId().equals(paperRunId)).toList();
        }

        @Override public int countByRunIdAndDateRange(String paperRunId, Instant start, Instant end) {
            return (int) records.stream()
                    .filter(r -> r.paperRunId().equals(paperRunId))
                    .filter(r -> !r.createdAt().isBefore(start) && r.createdAt().isBefore(end))
                    .count();
        }
    }
}
