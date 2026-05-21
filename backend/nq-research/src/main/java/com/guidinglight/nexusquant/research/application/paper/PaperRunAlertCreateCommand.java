package com.guidinglight.nexusquant.research.application.paper;

public record PaperRunAlertCreateCommand(
        String paperRunId,
        String alertType,
        String severity,
        String title,
        String message,
        String source,
        String eventSnapshotJson
) {}
