package com.guidinglight.nexusquant.paper.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EmergencyStopRequestBody(
        @NotBlank(message = "triggerType must not be blank")
        @Pattern(regexp = "MANUAL|RISK_LIMIT|SYSTEM_ERROR", message = "triggerType must be MANUAL, RISK_LIMIT, or SYSTEM_ERROR")
        String triggerType,
        @NotBlank(message = "reason must not be blank")
        @Size(max = 512)
        String reason,
        @Size(max = 128)
        String triggeredBy
) {}
