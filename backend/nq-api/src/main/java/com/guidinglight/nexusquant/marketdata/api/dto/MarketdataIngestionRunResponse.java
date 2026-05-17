package com.guidinglight.nexusquant.marketdata.api.dto;

import com.guidinglight.nexusquant.marketdata.domain.MarketdataIngestionRun;

import java.time.Instant;
import java.util.UUID;

/**
 * MarketdataIngestionRunResponse 是一次 run-once 执行结果的 API 响应。
 */
public record MarketdataIngestionRunResponse(
        UUID jobId,
        UUID runId,
        String status,
        int fetchedBars,
        int insertedBars,
        int updatedBars,
        int skippedBars,
        Instant startedAt,
        Instant finishedAt,
        Instant requestedStartTime,
        Instant requestedEndTime,
        Instant actualStartTime,
        Instant actualEndTime,
        String errorMessage
) {
    public static MarketdataIngestionRunResponse from(MarketdataIngestionRun run) {
        return new MarketdataIngestionRunResponse(
                run.jobId(),
                run.runId(),
                run.status().name(),
                run.fetchedBars(),
                run.insertedBars(),
                run.updatedBars(),
                run.skippedBars(),
                run.startedAt(),
                run.finishedAt(),
                run.requestedStartTime(),
                run.requestedEndTime(),
                run.actualStartTime(),
                run.actualEndTime(),
                run.errorMessage()
        );
    }
}
