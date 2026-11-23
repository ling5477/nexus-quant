package com.guidinglight.nexusquant.paper.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PaperRunAlertCreateRequestBody(
        @NotBlank(message = "alertType must not be blank")
        @Size(max = 64)
        String alertType,
        @NotBlank(message = "severity must not be blank")
        @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL", message = "severity must be LOW, MEDIUM, HIGH, or CRITICAL")
        String severity,
        @NotBlank(message = "title must not be blank")
        @Size(max = 512)
        String title,
        String message,
        @Size(max = 128)
        String source,
        String eventSnapshotJson
) {}
