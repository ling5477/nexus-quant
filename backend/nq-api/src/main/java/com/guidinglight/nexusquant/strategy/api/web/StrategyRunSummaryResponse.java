package com.guidinglight.nexusquant.strategy.api.web;

import com.guidinglight.nexusquant.strategy.domain.StrategyRunSummary;

import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * StrategyRunSummaryResponse 描述接口响应体。
 */
@Schema(name = "StrategyRunSummaryResponse", description = "接口响应体")
public record StrategyRunSummaryResponse(
        @Schema(description = "strategyId")
        String strategyId,
        @Schema(description = "scheduleJobId")
        String scheduleJobId,
        @Schema(description = "strategyRunId")
        String strategyRunId,
        @Schema(description = "requestId")
        String requestId,
        @Schema(description = "triggerType")
        String triggerType,
        @Schema(description = "status")
        String status,
        @Schema(description = "exchangeCode")
        String exchangeCode,
        @Schema(description = "accountId")
        Long accountId,
        @Schema(description = "tradeEnv")
        String tradeEnv,
        @Schema(description = "startedAt")
        Instant startedAt,
        @Schema(description = "finishedAt")
        Instant finishedAt,
        @Schema(description = "errorMessage")
        String errorMessage
) {
    public static StrategyRunSummaryResponse from(StrategyRunSummary summary) {
        return new StrategyRunSummaryResponse(
                summary.strategyId(),
                summary.scheduleJobId(),
                summary.strategyRunId(),
                summary.requestId(),
                summary.triggerType(),
                summary.status().name(),
                summary.exchangeCode(),
                summary.accountId(),
                summary.tradeEnv(),
                summary.startedAt(),
                summary.finishedAt(),
                summary.errorMessage()
        );
    }
}



