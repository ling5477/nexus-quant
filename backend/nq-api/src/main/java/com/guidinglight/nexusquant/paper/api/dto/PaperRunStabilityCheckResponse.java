package com.guidinglight.nexusquant.paper.api.dto;

import com.guidinglight.nexusquant.research.domain.paper.PaperRunStabilityCheck;

import java.math.BigDecimal;
import java.time.Instant;

public record PaperRunStabilityCheckResponse(
        String stabilityCheckId,
        String paperRunId,
        Instant checkWindowStart,
        Instant checkWindowEnd,
        String status,
        BigDecimal uptimeRatio,
        int heartbeatCount,
        int alertCount,
        int failedFireCount,
        int recoveryCount,
        int reportCount,
        String summaryJson,
        Instant createdAt
) {
    public static PaperRunStabilityCheckResponse from(PaperRunStabilityCheck check) {
        return new PaperRunStabilityCheckResponse(
                check.stabilityCheckId(),
                check.paperRunId(),
                check.checkWindowStart(),
                check.checkWindowEnd(),
                check.status().name(),
                check.uptimeRatio(),
                check.heartbeatCount(),
                check.alertCount(),
                check.failedFireCount(),
                check.recoveryCount(),
                check.reportCount(),
                check.summaryJson(),
                check.createdAt()
        );
    }
}
