package com.guidinglight.nexusquant.paper.api.dto;

import jakarta.validation.constraints.Size;

public record PaperRunAlertAckRequestBody(
        @Size(max = 512)
        String acknowledgedBy
) {}
