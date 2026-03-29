package com.guidinglight.nexusquant.strategy.api.web;

import com.guidinglight.nexusquant.strategy.domain.StrategyRunDetail;

import java.time.Instant;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * StrategyRunDetailResponse 描述接口响应体。
 */
@Schema(name = "StrategyRunDetailResponse", description = "接口响应体")
public record StrategyRunDetailResponse(
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
        String errorMessage,
        @Schema(description = "orders")
        List<StrategyRunOrderResponse> orders,
        @Schema(description = "trades")
        List<StrategyRunTradeResponse> trades,
        @Schema(description = "ledgerSummary")
        String ledgerSummary,
        @Schema(description = "riskSummary")
        String riskSummary,
        @Schema(description = "eventSummary")
        String eventSummary
) {
    public static StrategyRunDetailResponse from(StrategyRunDetail detail) {
        return new StrategyRunDetailResponse(
                detail.strategyId(),
                detail.scheduleJobId(),
                detail.strategyRunId(),
                detail.requestId(),
                detail.triggerType(),
                detail.status().name(),
                detail.exchangeCode(),
                detail.accountId(),
                detail.tradeEnv(),
                detail.startedAt(),
                detail.finishedAt(),
                detail.errorMessage(),
                detail.executionResult().orders().stream().map(StrategyRunOrderResponse::from).toList(),
                detail.executionResult().trades().stream().map(StrategyRunTradeResponse::from).toList(),
                detail.executionResult().ledgerSummary(),
                detail.executionResult().riskSummary(),
                detail.executionResult().eventSummary()
        );
    }
}



