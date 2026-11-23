package com.guidinglight.nexusquant.paper.api.dto;

import com.guidinglight.nexusquant.research.domain.paper.PaperRiskCheckResult;

import java.time.Instant;

public record PaperRiskCheckResultResponse(
        String riskResultId,
        String paperRunId,
        String checkType,
        String status,
        String severity,
        String message,
        String inputSnapshotJson,
        String resultSnapshotJson,
        Instant createdAt
) {
    public static PaperRiskCheckResultResponse from(PaperRiskCheckResult r) {
        return new PaperRiskCheckResultResponse(
                r.riskResultId(), r.paperRunId(), r.checkType(),
                r.status().name(), r.severity().name(), r.message(),
                r.inputSnapshotJson(), r.resultSnapshotJson(), r.createdAt());
    }
}
