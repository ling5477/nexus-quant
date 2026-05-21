package com.guidinglight.nexusquant.paper.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PaperRunScheduleCreateRequestBody(
        @NotBlank(message = "paperRunId must not be blank")
        @Size(max = 64)
        String paperRunId,
        @NotBlank(message = "scheduleName must not be blank")
        @Size(max = 256)
        String scheduleName,
        @NotBlank(message = "cronExpr must not be blank")
        @Size(max = 128)
        String cronExpr,
        @Size(max = 64)
        String timezone,
        String requestJson
) {}
