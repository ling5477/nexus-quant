package com.guidinglight.nexusquant.research.domain.paper;

import java.time.Instant;

public record EmergencyStopEvent(
        String emergencyStopId,
        String paperRunId,
        EmergencyStopTriggerType triggerType,
        EmergencyStopStatus status,
        String reason,
        String triggeredBy,
        Instant triggeredAt,
        Instant resolvedAt,
        String requestJson,
        String resultJson,
        Instant createdAt
) {}
