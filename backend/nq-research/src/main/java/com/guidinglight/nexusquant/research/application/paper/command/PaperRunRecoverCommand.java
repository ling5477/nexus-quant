package com.guidinglight.nexusquant.research.application.paper.command;

public record PaperRunRecoverCommand(
        String paperRunId,
        String reason,
        String requestJson
) {}
