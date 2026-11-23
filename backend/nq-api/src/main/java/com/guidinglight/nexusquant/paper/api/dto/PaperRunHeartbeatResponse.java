package com.guidinglight.nexusquant.paper.api.dto;

import com.guidinglight.nexusquant.research.domain.paper.PaperRunHeartbeat;

import java.time.Instant;

public record PaperRunHeartbeatResponse(
        String heartbeatId,
        String paperRunId,
        Instant heartbeatTime,
        String status,
        Instant lastEventTime,
        Instant lastOrderTime,
        Instant lastTradeTime,
        Long lagSeconds,
        String summaryJson,
        Instant createdAt
) {
    public static PaperRunHeartbeatResponse from(PaperRunHeartbeat h) {
        return new PaperRunHeartbeatResponse(
                h.heartbeatId(), h.paperRunId(), h.heartbeatTime(), h.status().name(),
                h.lastEventTime(), h.lastOrderTime(), h.lastTradeTime(),
                h.lagSeconds(), h.summaryJson(), h.createdAt());
    }
}
