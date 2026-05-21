package com.guidinglight.nexusquant.research.application.paper;

import com.guidinglight.nexusquant.research.domain.paper.PaperRunHeartbeat;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunHeartbeatStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunSchedule;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunScheduleFire;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunScheduleFireStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunScheduleStatus;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRun;
import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRunStatus;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunHeartbeatRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunScheduleFireRepository;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunScheduleRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PaperRunScheduleService {

    private final PaperTradingRunService runService;
    private final PaperRunScheduleRepository scheduleRepository;
    private final PaperRunScheduleFireRepository scheduleFireRepository;
    private final PaperRunHeartbeatRepository heartbeatRepository;
    private final Clock clock;

    @Autowired
    public PaperRunScheduleService(
            PaperTradingRunService runService,
            PaperRunScheduleRepository scheduleRepository,
            PaperRunScheduleFireRepository scheduleFireRepository,
            PaperRunHeartbeatRepository heartbeatRepository
    ) {
        this(runService, scheduleRepository, scheduleFireRepository, heartbeatRepository, Clock.systemUTC());
    }

    public PaperRunScheduleService(
            PaperTradingRunService runService,
            PaperRunScheduleRepository scheduleRepository,
            PaperRunScheduleFireRepository scheduleFireRepository,
            PaperRunHeartbeatRepository heartbeatRepository,
            Clock clock
    ) {
        this.runService = Objects.requireNonNull(runService);
        this.scheduleRepository = Objects.requireNonNull(scheduleRepository);
        this.scheduleFireRepository = Objects.requireNonNull(scheduleFireRepository);
        this.heartbeatRepository = Objects.requireNonNull(heartbeatRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    public PaperRunSchedule createSchedule(PaperRunScheduleCreateCommand command) {
        runService.getById(command.paperRunId());
        validateCronExpr(command.cronExpr());

        Instant now = clock.instant();
        String timezone = command.timezone() == null || command.timezone().isBlank() ? "UTC" : command.timezone();
        String requestJson = command.requestJson() != null ? command.requestJson() : "{}";
        String createdBy = command.createdBy() != null ? command.createdBy() : "system";

        PaperRunSchedule schedule = new PaperRunSchedule(
                "sch-" + UUID.randomUUID(),
                command.paperRunId(),
                command.scheduleName(),
                command.cronExpr(),
                PaperRunScheduleStatus.ENABLED,
                timezone,
                null,
                null,
                createdBy,
                now,
                now,
                requestJson
        );
        scheduleRepository.insert(schedule);
        return schedule;
    }

    public PaperRunSchedule getScheduleById(String scheduleId) {
        return scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("paper run schedule not found: " + scheduleId));
    }

    public List<PaperRunSchedule> listSchedules(String paperRunId, String status) {
        return scheduleRepository.list(paperRunId, status);
    }

    public PaperRunSchedule updateScheduleStatus(String scheduleId, String status) {
        PaperRunScheduleStatus target;
        try {
            target = PaperRunScheduleStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("invalid schedule status: " + status);
        }
        getScheduleById(scheduleId);
        Instant now = clock.instant();
        scheduleRepository.updateStatus(scheduleId, target, now);
        return scheduleRepository.findById(scheduleId).orElseThrow();
    }

    public PaperRunScheduleFire runScheduleOnce(String scheduleId) {
        PaperRunSchedule schedule = getScheduleById(scheduleId);
        if (schedule.status() != PaperRunScheduleStatus.ENABLED) {
            throw new IllegalStateException("schedule must be ENABLED to run-once, current: " + schedule.status());
        }
        runService.getById(schedule.paperRunId());

        Instant firedAt = clock.instant();
        Instant finishedAt = firedAt;
        long durationMs = 0L;

        PaperRunScheduleFire fire = new PaperRunScheduleFire(
                "fir-" + UUID.randomUUID(),
                scheduleId,
                schedule.paperRunId(),
                PaperRunScheduleFireStatus.SUCCEEDED,
                firedAt,
                finishedAt,
                durationMs,
                "{\"trigger\":\"manual\"}",
                null,
                firedAt
        );
        scheduleFireRepository.insert(fire);
        scheduleRepository.updateLastFireTime(scheduleId, firedAt, firedAt);
        return fire;
    }

    public List<PaperRunScheduleFire> listFires(String scheduleId) {
        getScheduleById(scheduleId);
        return scheduleFireRepository.listByScheduleId(scheduleId);
    }

    public PaperRunHeartbeat runHeartbeatOnce(String paperRunId) {
        PaperTradingRun run = runService.getById(paperRunId);
        Instant now = clock.instant();

        PaperRunHeartbeatStatus status;
        Long lagSeconds = 0L;
        if (run.status() == PaperTradingRunStatus.RUNNING) {
            status = PaperRunHeartbeatStatus.OK;
        } else if (run.status() == PaperTradingRunStatus.STOPPED || run.status() == PaperTradingRunStatus.FAILED) {
            status = PaperRunHeartbeatStatus.STOPPED;
        } else {
            status = PaperRunHeartbeatStatus.UNKNOWN;
        }

        PaperRunHeartbeat heartbeat = new PaperRunHeartbeat(
                "hbt-" + UUID.randomUUID(),
                paperRunId,
                now,
                status,
                null,
                null,
                null,
                lagSeconds,
                "{\"runStatus\":\"" + run.status() + "\"}",
                now
        );
        heartbeatRepository.insert(heartbeat);
        return heartbeat;
    }

    public List<PaperRunHeartbeat> listHeartbeats(String paperRunId) {
        runService.getById(paperRunId);
        return heartbeatRepository.listByRunId(paperRunId);
    }

    private static void validateCronExpr(String cronExpr) {
        if (cronExpr == null || cronExpr.isBlank()) {
            throw new IllegalArgumentException("cronExpr must not be blank");
        }
        String[] parts = cronExpr.trim().split("\\s+");
        if (parts.length < 5 || parts.length > 7) {
            throw new IllegalArgumentException("cronExpr must have 5 to 7 fields, got: " + cronExpr);
        }
    }
}
