package com.guidinglight.nexusquant.paper.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PaperRunScheduleStatusUpdateRequestBody(
        @NotBlank(message = "status must not be blank")
        @Pattern(regexp = "ENABLED|DISABLED|PAUSED", message = "status must be ENABLED, DISABLED, or PAUSED")
        String status
) {}
