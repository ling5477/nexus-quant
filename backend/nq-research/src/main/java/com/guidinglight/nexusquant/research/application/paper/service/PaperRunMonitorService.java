package com.guidinglight.nexusquant.research.application.paper.service;

import static com.guidinglight.nexusquant.observability.operational.OperationalObservation.Operation.CRITICAL_ALERT;
import static com.guidinglight.nexusquant.observability.operational.OperationalObservation.Signal.EMITTED;

import com.guidinglight.nexusquant.observability.operational.OperationalObservation;
import com.guidinglight.nexusquant.observability.operational.SafeOperationalObservation;
import com.guidinglight.nexusquant.research.application.paper.command.PaperRunAlertCreateCommand;
import com.guidinglight.nexusquant.research.application.paper.command.PaperRunDailyReportGenerateCommand;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlert;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlertSeverity;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlertStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunDailyReport;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunDailyReportStatus;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunAlertRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunDailyReportRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 日报及告警必须服从实际父运行权限，带路径的请求还须匹配父子归属；写入前完成检查。 */
@Service
public class PaperRunMonitorService {

    private final PaperTradingRunService runService;
    private final PaperRunDailyReportRepository dailyReportRepository;
    private final PaperRunAlertRepository alertRepository;
    private final Clock clock;
    private final OperationalObservation observation;

    public PaperRunMonitorService(
            PaperTradingRunService runService,
            PaperRunDailyReportRepository dailyReportRepository,
            PaperRunAlertRepository alertRepository) {
        this(runService, dailyReportRepository, alertRepository, OperationalObservation.NOOP);
    }

    @Autowired
    public PaperRunMonitorService(
            PaperTradingRunService runService,
            PaperRunDailyReportRepository dailyReportRepository,
            PaperRunAlertRepository alertRepository,
            OperationalObservation observation) {
        this(runService, dailyReportRepository, alertRepository, Clock.systemUTC(), observation);
    }

    public PaperRunMonitorService(
            PaperTradingRunService runService,
            PaperRunDailyReportRepository dailyReportRepository,
            PaperRunAlertRepository alertRepository,
            Clock clock) {
        this(
                runService,
                dailyReportRepository,
                alertRepository,
                clock,
                OperationalObservation.NOOP);
    }

    public PaperRunMonitorService(
            PaperTradingRunService runService,
            PaperRunDailyReportRepository dailyReportRepository,
            PaperRunAlertRepository alertRepository,
            Clock clock,
            OperationalObservation observation) {
        this.observation = new SafeOperationalObservation(observation);
        this.runService = Objects.requireNonNull(runService);
        this.dailyReportRepository = Objects.requireNonNull(dailyReportRepository);
        this.alertRepository = Objects.requireNonNull(alertRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    public PaperRunDailyReport generateDailyReport(PaperRunDailyReportGenerateCommand command) {
        requireLegacyDailyReportRun(command.paperRunId());
        LocalDate reportDate =
                command.reportDate() != null ? command.reportDate() : LocalDate.now(clock);
        Instant now = clock.instant();

        Instant dayStart = reportDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant dayEnd = reportDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        int alertCount =
                alertRepository.countByRunIdAndDateRange(command.paperRunId(), dayStart, dayEnd);

        PaperRunDailyReport report =
                new PaperRunDailyReport(
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
                        now);
        dailyReportRepository.upsert(report);
        return dailyReportRepository
                .findByRunIdAndDate(command.paperRunId(), reportDate)
                .orElse(report);
    }

    public PaperRunDailyReport getDailyReportById(String reportId) {
        return getDailyReportById(null, reportId);
    }

    /** 兼容内部按ID读取，但HTTP路径不能被忽略；不能用可见运行ID读取另一运行的子对象。 */
    public PaperRunDailyReport getDailyReportById(String paperRunId, String reportId) {
        PaperRunDailyReport report =
                dailyReportRepository
                        .findById(reportId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "daily report not found: " + reportId));
        requireRun(paperRunId, report.paperRunId());
        requireLegacyDailyReportRun(report.paperRunId());
        return report;
    }

    public List<PaperRunDailyReport> listDailyReports(String paperRunId) {
        requireLegacyDailyReportRun(paperRunId);
        return dailyReportRepository.listByRunId(paperRunId);
    }

    private void requireLegacyDailyReportRun(String paperRunId) {
        var run = runService.getById(paperRunId);
        // 旧日报没有 canonical 日结估值口径；不能把未读取的订单、成交和权益持久化或展示为零。
        if (run.canonicalAccountId() != null) {
            throw new IllegalStateException(
                    "STRATEGY_SIM_DAILY_REPORT_UNAVAILABLE_USE_CANONICAL_ANALYSIS");
        }
    }

    @Transactional(timeout = 15)
    public PaperRunAlert createAlert(PaperRunAlertCreateCommand command) {
        runService.getById(command.paperRunId());
        PaperRunAlertSeverity severity;
        try {
            severity = PaperRunAlertSeverity.valueOf(command.severity());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("invalid alert severity: " + command.severity());
        }

        Instant now = clock.instant();
        String eventSnapshotJson =
                command.eventSnapshotJson() != null ? command.eventSnapshotJson() : "{}";

        PaperRunAlert alert =
                new PaperRunAlert(
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
                        now);
        alertRepository.insert(alert);
        if (severity == PaperRunAlertSeverity.CRITICAL) {
            observation.record(CRITICAL_ALERT, EMITTED, 1);
        }
        return alert;
    }

    public List<PaperRunAlert> listAlerts(String paperRunId, String status, String severity) {
        runService.getById(paperRunId);
        return alertRepository.listByRunId(paperRunId, status, severity);
    }

    /** 精确查询不依赖列表窗口；先验证实际父运行和请求路径，不回退到另一条告警。 */
    @Transactional(readOnly = true, timeout = 10)
    public PaperRunAlert getAlert(String paperRunId, String alertId) {
        PaperRunAlert alert =
                alertRepository
                        .findById(alertId)
                        .orElseThrow(
                                () -> new IllegalArgumentException("alert not found: " + alertId));
        requireRun(paperRunId, alert.paperRunId());
        return alert;
    }

    @Transactional(timeout = 15)
    public PaperRunAlert ackAlert(String alertId, String acknowledgedBy) {
        return ackAlert(null, alertId, acknowledgedBy);
    }

    /** 在任何幂等返回或状态写入前验证实际运行权限与请求路径，拒绝请求不产生告警副作用。 */
    @Transactional(timeout = 15)
    public PaperRunAlert ackAlert(String paperRunId, String alertId, String acknowledgedBy) {
        PaperRunAlert alert =
                alertRepository
                        .findById(alertId)
                        .orElseThrow(
                                () -> new IllegalArgumentException("alert not found: " + alertId));
        requireRun(paperRunId, alert.paperRunId());
        if (alert.status() == PaperRunAlertStatus.RESOLVED) {
            throw new IllegalStateException("alert already resolved, cannot ack: " + alertId);
        }
        if (alert.status() == PaperRunAlertStatus.ACKED) {
            return alert;
        }
        Instant now = clock.instant();
        String ackBy = acknowledgedBy != null ? acknowledgedBy : "system";
        boolean changed =
                alertRepository.updateStatus(
                        alertId, PaperRunAlertStatus.ACKED, ackBy, now, null, now);
        PaperRunAlert current = alertRepository.findById(alertId).orElseThrow();
        // SQL按前态比较写入；较晚的确认不能把已经解决的告警退回ACKED。
        if (!changed && current.status() != PaperRunAlertStatus.ACKED)
            throw new IllegalStateException(
                    "alert changed concurrently, reread before acknowledging");
        return current;
    }

    @Transactional(timeout = 15)
    public PaperRunAlert resolveAlert(String alertId, String resolvedBy) {
        return resolveAlert(null, alertId, resolvedBy);
    }

    /** 在任何幂等返回或状态写入前验证实际运行权限与请求路径，拒绝请求不产生告警副作用。 */
    @Transactional(timeout = 15)
    public PaperRunAlert resolveAlert(String paperRunId, String alertId, String resolvedBy) {
        PaperRunAlert alert =
                alertRepository
                        .findById(alertId)
                        .orElseThrow(
                                () -> new IllegalArgumentException("alert not found: " + alertId));
        requireRun(paperRunId, alert.paperRunId());
        if (alert.status() == PaperRunAlertStatus.RESOLVED) {
            return alert;
        }
        Instant now = clock.instant();
        boolean changed =
                alertRepository.updateStatus(
                        alertId, PaperRunAlertStatus.RESOLVED, null, null, now, now);
        PaperRunAlert current = alertRepository.findById(alertId).orElseThrow();
        if (!changed && current.status() != PaperRunAlertStatus.RESOLVED)
            throw new IllegalStateException("alert changed concurrently, reread before resolving");
        return current;
    }

    private void requireRun(String expectedRunId, String actualRunId) {
        runService.getById(actualRunId);
        if (expectedRunId != null && !expectedRunId.equals(actualRunId)) {
            throw new IllegalArgumentException("paper child object not found in requested run");
        }
    }
}
