package com.guidinglight.nexusquant.paper.api.dto;

import com.guidinglight.nexusquant.research.domain.paper.PaperRunScheduleFire;

import java.time.Instant;

public record PaperRunScheduleFireResponse(
        String fireId,
        String scheduleId,
        String paperRunId,
        String status,
        Instant firedAt,
        Instant finishedAt,
        Long durationMs,
        String resultJson,
        String errorMessage,
        Instant createdAt
) {
    public static PaperRunScheduleFireResponse from(PaperRunScheduleFire f) {
        return new PaperRunScheduleFireResponse(
                f.fireId(), f.scheduleId(), f.paperRunId(), f.status().name(),
                f.firedAt(), f.finishedAt(), f.durationMs(), f.resultJson(),
                f.errorMessage(), f.createdAt());
    }
}
