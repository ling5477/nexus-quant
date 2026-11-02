package com.guidinglight.nexusquant.research.domain.paper;

import java.math.BigDecimal;
import java.time.Instant;

public record PaperRunStabilityCheck(
        String stabilityCheckId,
        String paperRunId,
        Instant checkWindowStart,
        Instant checkWindowEnd,
        PaperRunStabilityCheckStatus status,
        BigDecimal uptimeRatio,
        int heartbeatCount,
        int alertCount,
        int failedFireCount,
        int recoveryCount,
        int reportCount,
        String summaryJson,
        Instant createdAt
) {}
