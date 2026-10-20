package com.guidinglight.nexusquant.paper.api.dto;

import com.guidinglight.nexusquant.research.domain.paper.PaperRunRecoveryEvent;

import java.time.Instant;

public record PaperRunRecoveryEventResponse(
        String recoveryEventId,
        String paperRunId,
        String recoveryType,
        String status,
        String reason,
        String requestJson,
        String resultJson,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt
) {
    public static PaperRunRecoveryEventResponse from(PaperRunRecoveryEvent event) {
        return new PaperRunRecoveryEventResponse(
                event.recoveryEventId(),
                event.paperRunId(),
                event.recoveryType().name(),
                event.status().name(),
                event.reason(),
                event.requestJson(),
                event.resultJson(),
                event.startedAt(),
                event.finishedAt(),
                event.createdAt()
        );
    }
}
