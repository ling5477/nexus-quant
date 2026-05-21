package com.guidinglight.nexusquant.research.application.paper;

public record PaperRunScheduleCreateCommand(
        String paperRunId,
        String scheduleName,
        String cronExpr,
        String timezone,
        String requestJson,
        String createdBy
) {}
