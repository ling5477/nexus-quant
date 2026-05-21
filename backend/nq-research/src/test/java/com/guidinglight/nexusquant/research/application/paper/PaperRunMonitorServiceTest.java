package com.guidinglight.nexusquant.research.application.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guidinglight.nexusquant.research.domain.BacktestPublishRecord;
import com.guidinglight.nexusquant.research.domain.BacktestRun;
import com.guidinglight.nexusquant.research.domain.BacktestRunStatus;
import com.guidinglight.nexusquant.research.domain.PublishStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlert;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlertSeverity;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlertStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunDailyReport;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunDailyReportStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRun;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunAlertRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunDailyReportRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaperRunMonitorServiceTest {

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-05-21T12:00:00Z"), ZoneOffset.UTC);

    private PaperTradingRunService runService;
    private InMemoryDailyReportRepo dailyReportRepo;
    private InMemoryAlertRepo alertRepo;
    private PaperRunMonitorService monitorService;

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

        dailyReportRepo = new InMemoryDailyReportRepo();
        alertRepo = new InMemoryAlertRepo();

        monitorService = new PaperRunMonitorService(
                runService, dailyReportRepo, alertRepo, fixedClock
        );
    }

    @Test
    void generateDailyReportShouldCreateReport() {
        PaperTradingRun run = createRun();

        PaperRunDailyReport report = monitorService.generateDailyReport(
                new PaperRunDailyReportGenerateCommand(run.paperRunId(), LocalDate.of(2026, 5, 21))
        );

        assertNotNull(report.reportId());
        assertEquals(run.paperRunId(), report.paperRunId());
        assertEquals(LocalDate.of(2026, 5, 21), report.reportDate());
        assertEquals(PaperRunDailyReportStatus.GENERATED, report.status());
        assertEquals(1, dailyReportRepo.records.size());
    }

    @Test
    void generateDailyReportShouldUseCurrentDateWhenNull() {
        PaperTradingRun run = createRun();

        PaperRunDailyReport report = monitorService.generateDailyReport(
                new PaperRunDailyReportGenerateCommand(run.paperRunId(), null)
        );

        assertEquals(LocalDate.of(2026, 5, 21), report.reportDate());
    }

    @Test
    void generateDailyReportShouldRejectMissingRun() {
        assertThrows(IllegalArgumentException.class, () ->
                monitorService.generateDailyReport(new PaperRunDailyReportGenerateCommand("missing", null))
        );
    }

    @Test
    void listDailyReportsShouldReturnByRunId() {
        PaperTradingRun run = createRun();
        monitorService.generateDailyReport(new PaperRunDailyReportGenerateCommand(run.paperRunId(), null));

        List<PaperRunDailyReport> reports = monitorService.listDailyReports(run.paperRunId());
        assertEquals(1, reports.size());
    }

    @Test
    void createAlertShouldInsertOpenAlert() {
        PaperTradingRun run = createRun();

        PaperRunAlert alert = monitorService.createAlert(new PaperRunAlertCreateCommand(
                run.paperRunId(), "HEARTBEAT_LAG", "MEDIUM", "心跳延迟", "延迟超过 60 秒", "HEARTBEAT", null
        ));

        assertNotNull(alert.alertId());
        assertEquals(run.paperRunId(), alert.paperRunId());
        assertEquals("HEARTBEAT_LAG", alert.alertType());
        assertEquals(PaperRunAlertSeverity.MEDIUM, alert.severity());
        assertEquals(PaperRunAlertStatus.OPEN, alert.status());
        assertEquals("心跳延迟", alert.title());
        assertEquals(1, alertRepo.records.size());
    }

    @Test
    void createAlertShouldRejectInvalidSeverity() {
        PaperTradingRun run = createRun();
        assertThrows(IllegalArgumentException.class, () ->
                monitorService.createAlert(new PaperRunAlertCreateCommand(
                        run.paperRunId(), "SYSTEM_NOTICE", "INVALID", "test", null, "MANUAL", null
                ))
        );
    }

    @Test
    void ackAlertShouldTransitionToAcked() {
        PaperTradingRun run = createRun();
        PaperRunAlert alert = monitorService.createAlert(new PaperRunAlertCreateCommand(
                run.paperRunId(), "SYSTEM_NOTICE", "LOW", "test", null, "MANUAL", null
        ));

        PaperRunAlert acked = monitorService.ackAlert(alert.alertId(), "admin");

        assertEquals(PaperRunAlertStatus.ACKED, acked.status());
        assertEquals("admin", acked.acknowledgedBy());
        assertNotNull(acked.acknowledgedAt());
    }

    @Test
    void ackAlertShouldBeIdempotent() {
        PaperTradingRun run = createRun();
        PaperRunAlert alert = monitorService.createAlert(new PaperRunAlertCreateCommand(
                run.paperRunId(), "SYSTEM_NOTICE", "LOW", "test", null, "MANUAL", null
        ));
        monitorService.ackAlert(alert.alertId(), "admin");

        PaperRunAlert acked2 = monitorService.ackAlert(alert.alertId(), "admin2");
        assertEquals(PaperRunAlertStatus.ACKED, acked2.status());
    }

    @Test
    void ackAlertShouldRejectResolved() {
        PaperTradingRun run = createRun();
        PaperRunAlert alert = monitorService.createAlert(new PaperRunAlertCreateCommand(
                run.paperRunId(), "SYSTEM_NOTICE", "LOW", "test", null, "MANUAL", null
        ));
        monitorService.resolveAlert(alert.alertId(), "admin");

        assertThrows(IllegalStateException.class, () -> monitorService.ackAlert(alert.alertId(), "admin"));
    }

    @Test
    void resolveAlertShouldTransitionToResolved() {
        PaperTradingRun run = createRun();
        PaperRunAlert alert = monitorService.createAlert(new PaperRunAlertCreateCommand(
                run.paperRunId(), "SYSTEM_NOTICE", "LOW", "test", null, "MANUAL", null
        ));

        PaperRunAlert resolved = monitorService.resolveAlert(alert.alertId(), "admin");

        assertEquals(PaperRunAlertStatus.RESOLVED, resolved.status());
        assertNotNull(resolved.resolvedAt());
    }

    @Test
    void resolveAlertShouldBeIdempotent() {
        PaperTradingRun run = createRun();
        PaperRunAlert alert = monitorService.createAlert(new PaperRunAlertCreateCommand(
                run.paperRunId(), "SYSTEM_NOTICE", "LOW", "test", null, "MANUAL", null
        ));
        monitorService.resolveAlert(alert.alertId(), "admin");

        PaperRunAlert resolved2 = monitorService.resolveAlert(alert.alertId(), "admin2");
        assertEquals(PaperRunAlertStatus.RESOLVED, resolved2.status());
    }

    @Test
    void listAlertsShouldFilterByStatus() {
        PaperTradingRun run = createRun();
        monitorService.createAlert(new PaperRunAlertCreateCommand(
                run.paperRunId(), "SYSTEM_NOTICE", "LOW", "test1", null, "MANUAL", null
        ));
        PaperRunAlert alert2 = monitorService.createAlert(new PaperRunAlertCreateCommand(
                run.paperRunId(), "RISK_WARNING", "HIGH", "test2", null, "RISK", null
        ));
        monitorService.ackAlert(alert2.alertId(), "admin");

        List<PaperRunAlert> openAlerts = monitorService.listAlerts(run.paperRunId(), "OPEN", null);
        assertEquals(1, openAlerts.size());

        List<PaperRunAlert> allAlerts = monitorService.listAlerts(run.paperRunId(), null, null);
        assertEquals(2, allAlerts.size());
    }

    private PaperTradingRun createRun() {
        return runService.create(new PaperTradingRunCreateCommand(
                "pub-001", "SIM", "BINANCE", "SPOT", "BTC-USDT", "1m", null, "tester"
        ));
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

    static class InMemoryDailyReportRepo implements PaperRunDailyReportRepository {
        final List<PaperRunDailyReport> records = new ArrayList<>();

        @Override public void upsert(PaperRunDailyReport report) {
            records.removeIf(r -> r.paperRunId().equals(report.paperRunId()) && r.reportDate().equals(report.reportDate()));
            records.add(report);
        }

        @Override public Optional<PaperRunDailyReport> findById(String reportId) {
            return records.stream().filter(r -> r.reportId().equals(reportId)).findFirst();
        }

        @Override public Optional<PaperRunDailyReport> findByRunIdAndDate(String paperRunId, LocalDate reportDate) {
            return records.stream()
                    .filter(r -> r.paperRunId().equals(paperRunId) && r.reportDate().equals(reportDate))
                    .findFirst();
        }

        @Override public List<PaperRunDailyReport> listByRunId(String paperRunId) {
            return records.stream().filter(r -> r.paperRunId().equals(paperRunId)).toList();
        }
    }

    static class InMemoryAlertRepo implements PaperRunAlertRepository {
        final List<PaperRunAlert> records = new ArrayList<>();

        @Override public void insert(PaperRunAlert alert) { records.add(alert); }

        @Override public Optional<PaperRunAlert> findById(String alertId) {
            return records.stream().filter(a -> a.alertId().equals(alertId)).findFirst();
        }

        @Override public List<PaperRunAlert> listByRunId(String paperRunId, String status, String severity) {
            return records.stream()
                    .filter(a -> a.paperRunId().equals(paperRunId))
                    .filter(a -> status == null || a.status().name().equals(status))
                    .filter(a -> severity == null || a.severity().name().equals(severity))
                    .toList();
        }

        @Override public boolean updateStatus(String alertId, PaperRunAlertStatus status, String acknowledgedBy, Instant acknowledgedAt, Instant resolvedAt, Instant updatedAt) {
            for (int i = 0; i < records.size(); i++) {
                PaperRunAlert a = records.get(i);
                if (a.alertId().equals(alertId)) {
                    records.set(i, new PaperRunAlert(
                            a.alertId(), a.paperRunId(), a.alertType(), a.severity(), status,
                            a.title(), a.message(), a.source(), a.eventSnapshotJson(),
                            acknowledgedBy != null ? acknowledgedBy : a.acknowledgedBy(),
                            acknowledgedAt != null ? acknowledgedAt : a.acknowledgedAt(),
                            resolvedAt != null ? resolvedAt : a.resolvedAt(),
                            a.createdAt(), updatedAt
                    ));
                    return true;
                }
            }
            return false;
        }

        @Override public int countByRunIdAndDateRange(String paperRunId, Instant start, Instant end) {
            return (int) records.stream()
                    .filter(a -> a.paperRunId().equals(paperRunId))
                    .filter(a -> !a.createdAt().isBefore(start) && a.createdAt().isBefore(end))
                    .count();
        }
    }
}
