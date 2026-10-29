package com.guidinglight.nexusquant.paper.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record PaperRunStabilityCheckGenerateRequestBody(
        @NotNull(message = "checkWindowStart must not be null")
        Instant checkWindowStart,
        @NotNull(message = "checkWindowEnd must not be null")
        Instant checkWindowEnd
) {}
