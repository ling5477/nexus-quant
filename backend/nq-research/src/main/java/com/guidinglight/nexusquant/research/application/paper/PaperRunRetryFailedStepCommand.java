package com.guidinglight.nexusquant.research.application.paper;

public record PaperRunRetryFailedStepCommand(
        String paperRunId,
        String failedStep,
        String reason,
        String requestJson
) {}
