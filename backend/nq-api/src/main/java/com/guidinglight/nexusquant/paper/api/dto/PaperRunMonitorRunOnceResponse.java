package com.guidinglight.nexusquant.paper.api.dto;

import com.guidinglight.nexusquant.research.application.paper.PaperRunMonitorRunService;

import java.time.Instant;
import java.util.List;

public record PaperRunMonitorRunOnceResponse(
        String paperRunId,
        Instant checkedAt,
        int createdAlertCount,
        List<PaperRunAlertResponse> createdAlerts
) {
    public static PaperRunMonitorRunOnceResponse from(PaperRunMonitorRunService.MonitorRunOnceResult result) {
        List<PaperRunAlertResponse> alertResponses = result.createdAlerts().stream()
                .map(PaperRunAlertResponse::from)
                .toList();
        return new PaperRunMonitorRunOnceResponse(
                result.paperRunId(),
                result.checkedAt(),
                alertResponses.size(),
                alertResponses
        );
    }
}
