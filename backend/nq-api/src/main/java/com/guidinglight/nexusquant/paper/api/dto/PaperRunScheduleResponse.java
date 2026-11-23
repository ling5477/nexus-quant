package com.guidinglight.nexusquant.paper.api.dto;

import com.guidinglight.nexusquant.research.domain.paper.PaperRunSchedule;

import java.time.Instant;

public record PaperRunScheduleResponse(
        String scheduleId,
        String paperRunId,
        String scheduleName,
        String cronExpr,
        String status,
        String timezone,
        Instant nextFireTime,
        Instant lastFireTime,
        String createdBy,
        Instant createdAt,
        Instant updatedAt,
        String requestJson
) {
    public static PaperRunScheduleResponse from(PaperRunSchedule s) {
        return new PaperRunScheduleResponse(
                s.scheduleId(), s.paperRunId(), s.scheduleName(), s.cronExpr(),
                s.status().name(), s.timezone(), s.nextFireTime(), s.lastFireTime(),
                s.createdBy(), s.createdAt(), s.updatedAt(), s.requestJson());
    }
}
