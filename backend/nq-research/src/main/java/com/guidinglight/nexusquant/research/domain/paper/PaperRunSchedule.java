package com.guidinglight.nexusquant.research.domain.paper;

import java.time.Instant;

public record PaperRunSchedule(
        String scheduleId,
        String paperRunId,
        String scheduleName,
        String cronExpr,
        PaperRunScheduleStatus status,
        String timezone,
        Instant nextFireTime,
        Instant lastFireTime,
        String createdBy,
        Instant createdAt,
        Instant updatedAt,
        String requestJson
) {}
