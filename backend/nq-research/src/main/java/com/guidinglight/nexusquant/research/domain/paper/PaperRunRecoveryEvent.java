package com.guidinglight.nexusquant.research.domain.paper;

import java.time.Instant;

public record PaperRunRecoveryEvent(
        String recoveryEventId,
        String paperRunId,
        PaperRunRecoveryType recoveryType,
        PaperRunRecoveryStatus status,
        String reason,
        String requestJson,
        String resultJson,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt
) {}
