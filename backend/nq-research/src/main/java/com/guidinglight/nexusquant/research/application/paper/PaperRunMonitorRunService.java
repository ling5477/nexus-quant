package com.guidinglight.nexusquant.research.application.paper;

import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlert;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunHeartbeat;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunScheduleFire;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunScheduleFireStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRun;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRunStatus;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunAlertRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunHeartbeatRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunScheduleFireRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PaperRunMonitorRunService {

    public static final long DEFAULT_HEARTBEAT_LAG_THRESHOLD_SECONDS = 300L;
    public static final Duration DEDUPE_WINDOW = Duration.ofMinutes(5);

    private final PaperTradingRunService runService;
    private final PaperRunMonitorService monitorService;
    private final PaperRunHeartbeatRepository heartbeatRepository;
    private final PaperRunAlertRepository alertRepository;
    private final PaperRunScheduleFireRepository scheduleFireRepository;
    private final Clock clock;
    private final long lagThresholdSeconds;

    @Autowired
    public PaperRunMonitorRunService(
            PaperTradingRunService runService,
            PaperRunMonitorService monitorService,
            PaperRunHeartbeatRepository heartbeatRepository,
            PaperRunAlertRepository alertRepository,
            PaperRunScheduleFireRepository scheduleFireRepository
    ) {
        this(runService, monitorService, heartbeatRepository, alertRepository, scheduleFireRepository,
                Clock.systemUTC(), DEFAULT_HEARTBEAT_LAG_THRESHOLD_SECONDS);
    }

    public PaperRunMonitorRunService(
            PaperTradingRunService runService,
            PaperRunMonitorService monitorService,
            PaperRunHeartbeatRepository heartbeatRepository,
            PaperRunAlertRepository alertRepository,
            PaperRunScheduleFireRepository scheduleFireRepository,
            Clock clock,
            long lagThresholdSeconds
    ) {
        this.runService = Objects.requireNonNull(runService);
        this.monitorService = Objects.requireNonNull(monitorService);
        this.heartbeatRepository = Objects.requireNonNull(heartbeatRepository);
        this.alertRepository = Objects.requireNonNull(alertRepository);
        this.scheduleFireRepository = Objects.requireNonNull(scheduleFireRepository);
        this.clock = Objects.requireNonNull(clock);
        this.lagThresholdSeconds = lagThresholdSeconds;
    }

    public MonitorRunOnceResult runOnce(String paperRunId) {
        PaperTradingRun run = runService.getById(paperRunId);
        List<PaperRunAlert> created = new ArrayList<>();
        Instant now = clock.instant();
        Instant dedupeStart = now.minus(DEDUPE_WINDOW);
        Instant dedupeEnd = now.plusSeconds(1);

        // 1. HEARTBEAT_LAG check
        if (run.status() == PaperTradingRunStatus.RUNNING) {
            PaperRunHeartbeat latest = heartbeatRepository.findLatestByRunId(paperRunId).orElse(null);
            boolean lagging;
            String detail;
            if (latest == null) {
                lagging = true;
                detail = "no heartbeat recorded";
            } else {
                long lag = Duration.between(latest.heartbeatTime(), now).getSeconds();
                if (lag < 0) {
                    lag = 0;
                }
                if (lag >= lagThresholdSeconds) {
                    lagging = true;
                    detail = "heartbeat lag=" + lag + "s exceeds threshold=" + lagThresholdSeconds + "s";
                } else {
                    lagging = false;
                    detail = null;
                }
            }
            if (lagging) {
                int existing = alertRepository.countByRunIdAndTypeAndDateRange(
                        paperRunId, "HEARTBEAT_LAG", dedupeStart, dedupeEnd);
                if (existing == 0) {
                    PaperRunAlert alert = monitorService.createAlert(new PaperRunAlertCreateCommand(
                            paperRunId,
                            "HEARTBEAT_LAG",
                            "HIGH",
                            "心跳延迟",
                            detail,
                            "MONITOR",
                            String.format("{\"thresholdSeconds\":%d,\"checkedAt\":\"%s\"}",
                                    lagThresholdSeconds, now)
                    ));
                    created.add(alert);
                }
            }
        }

        // 2. SCHEDULE_FIRE_FAILED check
        List<PaperRunScheduleFire> failedFires = scheduleFireRepository.listByRunIdAndStatus(
                paperRunId, PaperRunScheduleFireStatus.FAILED.name(), dedupeStart, dedupeEnd);
        if (!failedFires.isEmpty()) {
            int existing = alertRepository.countByRunIdAndTypeAndDateRange(
                    paperRunId, "SCHEDULE_FIRE_FAILED", dedupeStart, dedupeEnd);
            if (existing == 0) {
                PaperRunScheduleFire latestFailed = failedFires.get(0);
                PaperRunAlert alert = monitorService.createAlert(new PaperRunAlertCreateCommand(
                        paperRunId,
                        "SCHEDULE_FIRE_FAILED",
                        "MEDIUM",
                        "调度触发失败",
                        "failed fires in last " + DEDUPE_WINDOW.toMinutes() + " minutes: " + failedFires.size(),
                        "SCHEDULE",
                        String.format("{\"failedFireCount\":%d,\"latestFireId\":\"%s\",\"latestErrorMessage\":\"%s\"}",
                                failedFires.size(),
                                latestFailed.fireId(),
                                safeJson(latestFailed.errorMessage()))
                ));
                created.add(alert);
            }
        }

        return new MonitorRunOnceResult(paperRunId, now, created);
    }

    private String safeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public record MonitorRunOnceResult(String paperRunId, Instant checkedAt, List<PaperRunAlert> createdAlerts) {}
}
