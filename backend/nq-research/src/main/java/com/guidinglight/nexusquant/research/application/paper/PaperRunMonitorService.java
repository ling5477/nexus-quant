package com.guidinglight.nexusquant.research.application.paper;

import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlert;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlertSeverity;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlertStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunDailyReport;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunDailyReportStatus;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunAlertRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunDailyReportRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PaperRunMonitorService {

    private final PaperTradingRunService runService;
    private final PaperRunDailyReportRepository dailyReportRepository;
    private final PaperRunAlertRepository alertRepository;
    private final Clock clock;

    @Autowired
    public PaperRunMonitorService(
            PaperTradingRunService runService,
            PaperRunDailyReportRepository dailyReportRepository,
            PaperRunAlertRepository alertRepository
    ) {
        this(runService, dailyReportRepository, alertRepository, Clock.systemUTC());
    }

    public PaperRunMonitorService(
            PaperTradingRunService runService,
            PaperRunDailyReportRepository dailyReportRepository,
            PaperRunAlertRepository alertRepository,
            Clock clock
    ) {
        this.runService = Objects.requireNonNull(runService);
        this.dailyReportRepository = Objects.requireNonNull(dailyReportRepository);
        this.alertRepository = Objects.requireNonNull(alertRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    public PaperRunDailyReport generateDailyReport(PaperRunDailyReportGenerateCommand command) {
        runService.getById(command.paperRunId());
        LocalDate reportDate = command.reportDate() != null ? command.reportDate() : LocalDate.now(clock);
        Instant now = clock.instant();

        Instant dayStart = reportDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant dayEnd = reportDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        int alertCount = alertRepository.countByRunIdAndDateRange(command.paperRunId(), dayStart, dayEnd);

        PaperRunDailyReport report = new PaperRunDailyReport(
                "rpt-" + UUID.randomUUID(),
                command.paperRunId(),
                reportDate,
                PaperRunDailyReportStatus.GENERATED,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                0,
                alertCount,
                0,
                "{\"version\":\"v1\",\"note\":\"minimal report\"}",
                now,
                now
        );
        dailyReportRepository.upsert(report);
        return dailyReportRepository.findByRunIdAndDate(command.paperRunId(), reportDate).orElse(report);
    }

    public PaperRunDailyReport getDailyReportById(String reportId) {
        return dailyReportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("daily report not found: " + reportId));
    }

    public List<PaperRunDailyReport> listDailyReports(String paperRunId) {
        runService.getById(paperRunId);
        return dailyReportRepository.listByRunId(paperRunId);
    }

    public PaperRunAlert createAlert(PaperRunAlertCreateCommand command) {
        runService.getById(command.paperRunId());
        PaperRunAlertSeverity severity;
        try {
            severity = PaperRunAlertSeverity.valueOf(command.severity());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("invalid alert severity: " + command.severity());
        }

        Instant now = clock.instant();
        String eventSnapshotJson = command.eventSnapshotJson() != null ? command.eventSnapshotJson() : "{}";

        PaperRunAlert alert = new PaperRunAlert(
                "alt-" + UUID.randomUUID(),
                command.paperRunId(),
                command.alertType(),
                severity,
                PaperRunAlertStatus.OPEN,
                command.title(),
                command.message(),
                command.source(),
                eventSnapshotJson,
                null,
                null,
                null,
                now,
                now
        );
        alertRepository.insert(alert);
        return alert;
    }

    public List<PaperRunAlert> listAlerts(String paperRunId, String status, String severity) {
        runService.getById(paperRunId);
        return alertRepository.listByRunId(paperRunId, status, severity);
    }

    public PaperRunAlert ackAlert(String alertId, String acknowledgedBy) {
        PaperRunAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("alert not found: " + alertId));
        if (alert.status() == PaperRunAlertStatus.RESOLVED) {
            throw new IllegalStateException("alert already resolved, cannot ack: " + alertId);
        }
        if (alert.status() == PaperRunAlertStatus.ACKED) {
            return alert;
        }
        Instant now = clock.instant();
        String ackBy = acknowledgedBy != null ? acknowledgedBy : "system";
        alertRepository.updateStatus(alertId, PaperRunAlertStatus.ACKED, ackBy, now, null, now);
        return alertRepository.findById(alertId).orElseThrow();
    }

    public PaperRunAlert resolveAlert(String alertId, String resolvedBy) {
        PaperRunAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("alert not found: " + alertId));
        if (alert.status() == PaperRunAlertStatus.RESOLVED) {
            return alert;
        }
        Instant now = clock.instant();
        alertRepository.updateStatus(alertId, PaperRunAlertStatus.RESOLVED, null, null, now, now);
        return alertRepository.findById(alertId).orElseThrow();
    }
}
