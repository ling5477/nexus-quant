package com.guidinglight.nexusquant.research.domain.paper;

import java.time.Instant;

public record PaperRiskCheckResult(
        String riskResultId,
        String paperRunId,
        String checkType,
        RiskCheckStatus status,
        RiskCheckSeverity severity,
        String message,
        String inputSnapshotJson,
        String resultSnapshotJson,
        Instant createdAt
) {}
