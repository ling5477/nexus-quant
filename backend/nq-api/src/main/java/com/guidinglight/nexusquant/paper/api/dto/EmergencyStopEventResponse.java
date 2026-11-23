package com.guidinglight.nexusquant.paper.api.dto;

import com.guidinglight.nexusquant.research.domain.paper.EmergencyStopEvent;

import java.time.Instant;

public record EmergencyStopEventResponse(
        String emergencyStopId,
        String paperRunId,
        String triggerType,
        String status,
        String reason,
        String triggeredBy,
        Instant triggeredAt,
        Instant resolvedAt,
        String requestJson,
        String resultJson,
        Instant createdAt
) {
    public static EmergencyStopEventResponse from(EmergencyStopEvent e) {
        return new EmergencyStopEventResponse(
                e.emergencyStopId(), e.paperRunId(), e.triggerType().name(),
                e.status().name(), e.reason(), e.triggeredBy(),
                e.triggeredAt(), e.resolvedAt(), e.requestJson(), e.resultJson(),
                e.createdAt());
    }
}
