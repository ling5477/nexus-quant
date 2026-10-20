package com.guidinglight.nexusquant.research.domain.paper;

import java.time.Instant;

public record PaperRunAlert(
        String alertId,
        String paperRunId,
        String alertType,
        PaperRunAlertSeverity severity,
        PaperRunAlertStatus status,
        String title,
        String message,
        String source,
        String eventSnapshotJson,
        String acknowledgedBy,
        Instant acknowledgedAt,
        Instant resolvedAt,
        Instant createdAt,
        Instant updatedAt
) {}
