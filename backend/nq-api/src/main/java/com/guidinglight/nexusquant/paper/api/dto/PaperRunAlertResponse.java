package com.guidinglight.nexusquant.paper.api.dto;

import com.guidinglight.nexusquant.research.domain.paper.PaperRunAlert;

import java.time.Instant;

public record PaperRunAlertResponse(
        String alertId,
        String paperRunId,
        String alertType,
        String severity,
        String status,
        String title,
        String message,
        String source,
        String eventSnapshotJson,
        String acknowledgedBy,
        Instant acknowledgedAt,
        Instant resolvedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static PaperRunAlertResponse from(PaperRunAlert a) {
        return new PaperRunAlertResponse(
                a.alertId(), a.paperRunId(), a.alertType(), a.severity().name(),
                a.status().name(), a.title(), a.message(), a.source(),
                a.eventSnapshotJson(), a.acknowledgedBy(), a.acknowledgedAt(),
                a.resolvedAt(), a.createdAt(), a.updatedAt());
    }
}
