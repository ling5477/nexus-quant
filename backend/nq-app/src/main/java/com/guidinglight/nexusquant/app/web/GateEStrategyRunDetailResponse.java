package com.guidinglight.nexusquant.app.web;

import com.guidinglight.nexusquant.core.model.StrategyRunDetail;

import java.time.Instant;
import java.util.List;

/**
 * GateEStrategyRunDetailResponse 表示单次运行详情的最小返回。
 */
public record GateEStrategyRunDetailResponse(
        String strategyId,
        String scheduleJobId,
        String strategyRunId,
        String requestId,
        String triggerType,
        String status,
        String exchangeCode,
        Long accountId,
        String tradeEnv,
        Instant startedAt,
        Instant finishedAt,
        String errorMessage,
        List<GateEStrategyRunOrderResponse> orders,
        List<GateEStrategyRunTradeResponse> trades,
        String ledgerSummary,
        String riskSummary,
        String eventSummary
) {
    public static GateEStrategyRunDetailResponse from(StrategyRunDetail detail) {
        return new GateEStrategyRunDetailResponse(
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
                detail.executionResult().orders().stream().map(GateEStrategyRunOrderResponse::from).toList(),
                detail.executionResult().trades().stream().map(GateEStrategyRunTradeResponse::from).toList(),
                detail.executionResult().ledgerSummary(),
                detail.executionResult().riskSummary(),
                detail.executionResult().eventSummary()
        );
    }
}
