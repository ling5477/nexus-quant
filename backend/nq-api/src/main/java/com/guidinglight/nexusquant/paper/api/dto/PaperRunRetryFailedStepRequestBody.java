package com.guidinglight.nexusquant.paper.api.dto;

import jakarta.validation.constraints.Size;

public record PaperRunRetryFailedStepRequestBody(
        @Size(max = 128)
        String failedStep,
        @Size(max = 1024)
        String reason,
        @Size(max = 4096)
        String requestJson
) {}
