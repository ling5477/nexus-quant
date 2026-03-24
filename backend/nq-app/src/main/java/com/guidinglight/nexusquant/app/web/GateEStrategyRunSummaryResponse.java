package com.guidinglight.nexusquant.app.web;

import com.guidinglight.nexusquant.core.model.StrategyRunSummary;

import java.time.Instant;

/**
 * GateEStrategyRunSummaryResponse 表示运行列表的最小返回。
 */
public record GateEStrategyRunSummaryResponse(
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
        String errorMessage
) {
    public static GateEStrategyRunSummaryResponse from(StrategyRunSummary summary) {
        return new GateEStrategyRunSummaryResponse(
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
