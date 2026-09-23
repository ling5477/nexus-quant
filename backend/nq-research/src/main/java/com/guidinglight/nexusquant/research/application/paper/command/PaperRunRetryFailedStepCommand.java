package com.guidinglight.nexusquant.research.application.paper.command;

public record PaperRunRetryFailedStepCommand(
        String paperRunId,
        String failedStep,
        String reason,
        String requestJson
) {}
