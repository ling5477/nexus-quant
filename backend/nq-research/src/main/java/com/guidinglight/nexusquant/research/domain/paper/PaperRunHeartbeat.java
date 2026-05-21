package com.guidinglight.nexusquant.research.domain.paper;

import java.time.Instant;

public record PaperRunHeartbeat(
        String heartbeatId,
        String paperRunId,
        Instant heartbeatTime,
        PaperRunHeartbeatStatus status,
        Instant lastEventTime,
        Instant lastOrderTime,
        Instant lastTradeTime,
        Long lagSeconds,
        String summaryJson,
        Instant createdAt
) {}
