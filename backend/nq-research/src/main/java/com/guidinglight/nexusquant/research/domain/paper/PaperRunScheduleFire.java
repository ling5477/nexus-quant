package com.guidinglight.nexusquant.research.domain.paper;

import java.time.Instant;

public record PaperRunScheduleFire(
        String fireId,
        String scheduleId,
        String paperRunId,
        PaperRunScheduleFireStatus status,
        Instant firedAt,
        Instant finishedAt,
        Long durationMs,
        String resultJson,
        String errorMessage,
        Instant createdAt
) {}
