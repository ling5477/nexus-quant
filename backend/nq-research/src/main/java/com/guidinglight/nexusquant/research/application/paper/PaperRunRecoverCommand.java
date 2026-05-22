package com.guidinglight.nexusquant.research.application.paper;

public record PaperRunRecoverCommand(
        String paperRunId,
        String reason,
        String requestJson
) {}
