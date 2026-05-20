package com.guidinglight.nexusquant.paper.api.dto;

import com.guidinglight.nexusquant.research.domain.paper.PaperTradingRun;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "PaperTradingRunResponse", description = "GateI-3 Paper Trading run 接口响应体")
public record PaperTradingRunResponse(
        String paperRunId,
        String publishId,
        String strategyVersionId,
        String status,
        String tradeEnv,
        String exchangeCode,
        String marketType,
        String symbol,
        String intervalCode,
        Instant startedAt,
        Instant stoppedAt,
        String publishSnapshotJson,
        String strategyVersionSnapshotJson,
        String datasetSnapshotJson,
        String paramSnapshotJson,
        String configSnapshotJson,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {
    public static PaperTradingRunResponse from(PaperTradingRun run) {
        return new PaperTradingRunResponse(
                run.paperRunId(),
                run.publishId(),
                run.strategyVersionId(),
                run.status().name(),
                run.tradeEnv(),
                run.exchangeCode(),
                run.marketType(),
                run.symbol(),
                run.intervalCode(),
                run.startedAt(),
                run.stoppedAt(),
                run.publishSnapshotJson(),
                run.strategyVersionSnapshotJson(),
                run.datasetSnapshotJson(),
                run.paramSnapshotJson(),
                run.configSnapshotJson(),
                run.createdBy(),
                run.createdAt(),
                run.updatedAt()
        );
    }
}
