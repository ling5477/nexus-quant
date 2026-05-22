package com.guidinglight.nexusquant.research.application.paper;

import com.guidinglight.nexusquant.research.domain.paper.PaperRunRecoveryEvent;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunRecoveryStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunRecoveryType;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRun;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRunStatus;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunRecoveryEventRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PaperRunRecoveryService {

    private final PaperTradingRunService runService;
    private final PaperRunRecoveryEventRepository recoveryEventRepository;
    private final Clock clock;

    @Autowired
    public PaperRunRecoveryService(
            PaperTradingRunService runService,
            PaperRunRecoveryEventRepository recoveryEventRepository
    ) {
        this(runService, recoveryEventRepository, Clock.systemUTC());
    }

    public PaperRunRecoveryService(
            PaperTradingRunService runService,
            PaperRunRecoveryEventRepository recoveryEventRepository,
            Clock clock
    ) {
        this.runService = Objects.requireNonNull(runService);
        this.recoveryEventRepository = Objects.requireNonNull(recoveryEventRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    public PaperRunRecoveryEvent recover(PaperRunRecoverCommand command) {
        PaperTradingRun run = runService.getById(command.paperRunId());
        return appendRecord(
                run.paperRunId(),
                PaperRunRecoveryType.MANUAL_RECOVER,
                statusForRun(run),
                command.reason(),
                command.requestJson() != null ? command.requestJson() : "{}",
                buildResult("MANUAL_RECOVER", run)
        );
    }

    public PaperRunRecoveryEvent retryFailedStep(PaperRunRetryFailedStepCommand command) {
        PaperTradingRun run = runService.getById(command.paperRunId());
        String request = command.requestJson() != null
                ? command.requestJson()
                : String.format("{\"failedStep\":\"%s\"}", safe(command.failedStep()));
        return appendRecord(
                run.paperRunId(),
                PaperRunRecoveryType.RETRY_FAILED_STEP,
                statusForRun(run),
                command.reason(),
                request,
                buildResult("RETRY_FAILED_STEP", run)
        );
    }

    public PaperRunRecoveryEvent recordHeartbeatLagRecover(String paperRunId, String reason, String requestJson) {
        PaperTradingRun run = runService.getById(paperRunId);
        return appendRecord(
                run.paperRunId(),
                PaperRunRecoveryType.HEARTBEAT_LAG_RECOVER,
                statusForRun(run),
                reason,
                requestJson != null ? requestJson : "{}",
                buildResult("HEARTBEAT_LAG_RECOVER", run)
        );
    }

    public PaperRunRecoveryEvent recordScheduleFireRecover(String paperRunId, String reason, String requestJson) {
        PaperTradingRun run = runService.getById(paperRunId);
        return appendRecord(
                run.paperRunId(),
                PaperRunRecoveryType.SCHEDULE_FIRE_RECOVER,
                statusForRun(run),
                reason,
                requestJson != null ? requestJson : "{}",
                buildResult("SCHEDULE_FIRE_RECOVER", run)
        );
    }

    public List<PaperRunRecoveryEvent> listRecoveryEvents(String paperRunId, String recoveryType, String status) {
        runService.getById(paperRunId);
        if (recoveryType != null && !recoveryType.isBlank()) {
            try {
                PaperRunRecoveryType.valueOf(recoveryType);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("invalid recovery type: " + recoveryType);
            }
        }
        if (status != null && !status.isBlank()) {
            try {
                PaperRunRecoveryStatus.valueOf(status);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("invalid recovery status: " + status);
            }
        }
        return recoveryEventRepository.listByRunId(paperRunId, recoveryType, status);
    }

    public int countByRunIdAndDateRange(String paperRunId, Instant start, Instant end) {
        return recoveryEventRepository.countByRunIdAndDateRange(paperRunId, start, end);
    }

    private PaperRunRecoveryEvent appendRecord(
            String paperRunId,
            PaperRunRecoveryType type,
            PaperRunRecoveryStatus status,
            String reason,
            String requestJson,
            String resultJson
    ) {
        Instant now = clock.instant();
        Instant finishedAt = status == PaperRunRecoveryStatus.STARTED ? null : now;
        PaperRunRecoveryEvent event = new PaperRunRecoveryEvent(
                "rec-" + UUID.randomUUID(),
                paperRunId,
                type,
                status,
                reason,
                requestJson,
                resultJson,
                now,
                finishedAt,
                now
        );
        recoveryEventRepository.insert(event);
        return event;
    }

    private PaperRunRecoveryStatus statusForRun(PaperTradingRun run) {
        if (run.status() == PaperTradingRunStatus.STOPPED) {
            return PaperRunRecoveryStatus.SKIPPED;
        }
        return PaperRunRecoveryStatus.SUCCEEDED;
    }

    private String buildResult(String action, PaperTradingRun run) {
        return String.format(
                "{\"action\":\"%s\",\"paperRunStatus\":\"%s\"}",
                action,
                run.status() != null ? run.status().name() : "UNKNOWN"
        );
    }

    private String safe(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
