package com.guidinglight.nexusquant.research.application.paper;

import com.guidinglight.nexusquant.research.domain.paper.PaperRunStabilityCheck;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunStabilityCheckStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunScheduleFireStatus;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunAlertRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunDailyReportRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunHeartbeatRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunRecoveryEventRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunScheduleFireRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunStabilityCheckRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PaperRunStabilityCheckService {

    private final PaperTradingRunService runService;
    private final PaperRunStabilityCheckRepository stabilityCheckRepository;
    private final PaperRunHeartbeatRepository heartbeatRepository;
    private final PaperRunAlertRepository alertRepository;
    private final PaperRunScheduleFireRepository scheduleFireRepository;
    private final PaperRunRecoveryEventRepository recoveryEventRepository;
    private final PaperRunDailyReportRepository dailyReportRepository;
    private final Clock clock;

    @Autowired
    public PaperRunStabilityCheckService(
            PaperTradingRunService runService,
            PaperRunStabilityCheckRepository stabilityCheckRepository,
            PaperRunHeartbeatRepository heartbeatRepository,
            PaperRunAlertRepository alertRepository,
            PaperRunScheduleFireRepository scheduleFireRepository,
            PaperRunRecoveryEventRepository recoveryEventRepository,
            PaperRunDailyReportRepository dailyReportRepository
    ) {
        this(runService, stabilityCheckRepository, heartbeatRepository, alertRepository,
                scheduleFireRepository, recoveryEventRepository, dailyReportRepository, Clock.systemUTC());
    }

    public PaperRunStabilityCheckService(
            PaperTradingRunService runService,
            PaperRunStabilityCheckRepository stabilityCheckRepository,
            PaperRunHeartbeatRepository heartbeatRepository,
            PaperRunAlertRepository alertRepository,
            PaperRunScheduleFireRepository scheduleFireRepository,
            PaperRunRecoveryEventRepository recoveryEventRepository,
            PaperRunDailyReportRepository dailyReportRepository,
            Clock clock
    ) {
        this.runService = Objects.requireNonNull(runService);
        this.stabilityCheckRepository = Objects.requireNonNull(stabilityCheckRepository);
        this.heartbeatRepository = Objects.requireNonNull(heartbeatRepository);
        this.alertRepository = Objects.requireNonNull(alertRepository);
        this.scheduleFireRepository = Objects.requireNonNull(scheduleFireRepository);
        this.recoveryEventRepository = Objects.requireNonNull(recoveryEventRepository);
        this.dailyReportRepository = Objects.requireNonNull(dailyReportRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    public PaperRunStabilityCheck generate(PaperRunStabilityCheckGenerateCommand command) {
        runService.getById(command.paperRunId());
        if (command.checkWindowStart() == null || command.checkWindowEnd() == null) {
            throw new IllegalArgumentException("checkWindowStart and checkWindowEnd are required");
        }
        if (!command.checkWindowEnd().isAfter(command.checkWindowStart())) {
            throw new IllegalArgumentException("invalid stability window: checkWindowEnd must be after checkWindowStart");
        }

        Instant start = command.checkWindowStart();
        Instant end = command.checkWindowEnd();
        int heartbeatCount = heartbeatRepository.countByRunIdAndDateRange(command.paperRunId(), start, end);
        int alertCount = alertRepository.countByRunIdAndDateRange(command.paperRunId(), start, end);
        int criticalOpenCount = alertRepository.countCriticalOpenByRunIdAndDateRange(command.paperRunId(), start, end);
        int failedFireCount = scheduleFireRepository.countByRunIdAndStatusAndDateRange(
                command.paperRunId(), PaperRunScheduleFireStatus.FAILED.name(), start, end);
        int recoveryCount = recoveryEventRepository.countByRunIdAndDateRange(command.paperRunId(), start, end);
        int reportCount = dailyReportRepository.countByRunIdAndDateRange(command.paperRunId(), start, end);

        PaperRunStabilityCheckStatus status;
        BigDecimal uptimeRatio;
        String judgement;
        if (heartbeatCount <= 0 || criticalOpenCount > 0 || failedFireCount > 0) {
            status = PaperRunStabilityCheckStatus.FAILED;
            uptimeRatio = heartbeatCount > 0 ? BigDecimal.valueOf(0.5) : BigDecimal.ZERO;
            judgement = "FAILED: " + buildJudgementReason(heartbeatCount, criticalOpenCount, failedFireCount);
        } else if (alertCount > 0 || recoveryCount > 0) {
            status = PaperRunStabilityCheckStatus.PARTIAL;
            uptimeRatio = BigDecimal.valueOf(0.9);
            judgement = "PARTIAL: alertCount=" + alertCount + ", recoveryCount=" + recoveryCount;
        } else {
            status = PaperRunStabilityCheckStatus.PASSED;
            uptimeRatio = BigDecimal.ONE;
            judgement = "PASSED: heartbeatCount=" + heartbeatCount + ", failedFireCount=0";
        }
        uptimeRatio = uptimeRatio.setScale(4, RoundingMode.HALF_UP);

        String summary = String.format(
                "{\"version\":\"v1\",\"heartbeatCount\":%d,\"alertCount\":%d,\"criticalOpenAlertCount\":%d,"
                        + "\"failedFireCount\":%d,\"recoveryCount\":%d,\"reportCount\":%d,\"judgement\":\"%s\"}",
                heartbeatCount, alertCount, criticalOpenCount, failedFireCount, recoveryCount, reportCount, judgement);

        Instant now = clock.instant();
        PaperRunStabilityCheck check = new PaperRunStabilityCheck(
                "stb-" + UUID.randomUUID(),
                command.paperRunId(),
                start,
                end,
                status,
                uptimeRatio,
                heartbeatCount,
                alertCount,
                failedFireCount,
                recoveryCount,
                reportCount,
                summary,
                now
        );
        stabilityCheckRepository.upsert(check);
        return stabilityCheckRepository.findByRunIdAndWindow(command.paperRunId(), start, end).orElse(check);
    }

    public PaperRunStabilityCheck getById(String stabilityCheckId) {
        return stabilityCheckRepository.findById(stabilityCheckId)
                .orElseThrow(() -> new IllegalArgumentException("stability check not found: " + stabilityCheckId));
    }

    public List<PaperRunStabilityCheck> list(String paperRunId, String status) {
        runService.getById(paperRunId);
        if (status != null && !status.isBlank()) {
            try {
                PaperRunStabilityCheckStatus.valueOf(status);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("invalid stability check status: " + status);
            }
        }
        return stabilityCheckRepository.listByRunId(paperRunId, status);
    }

    private String buildJudgementReason(int heartbeatCount, int criticalOpenCount, int failedFireCount) {
        StringBuilder sb = new StringBuilder();
        if (heartbeatCount <= 0) {
            sb.append("no heartbeat;");
        }
        if (criticalOpenCount > 0) {
            sb.append("critical open alert=").append(criticalOpenCount).append(';');
        }
        if (failedFireCount > 0) {
            sb.append("failed fire=").append(failedFireCount).append(';');
        }
        return sb.toString();
    }
}
