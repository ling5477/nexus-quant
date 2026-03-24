package com.guidinglight.nexusquant.app.web;

import com.guidinglight.nexusquant.core.service.StrategyScheduleScanResult;

/**
 * GateEStrategyScheduleScanDetailResponse 表示单条 schedule 的扫描结果。
 */
public record GateEStrategyScheduleScanDetailResponse(
        String scheduleJobId,
        String strategyId,
        String outcome,
        String requestId,
        String strategyRunId,
        String detail
) {
    public static GateEStrategyScheduleScanDetailResponse from(StrategyScheduleScanResult result) {
        return new GateEStrategyScheduleScanDetailResponse(
                result.scheduleJobId(),
                result.strategyId(),
                result.outcome().name().toLowerCase(),
                result.requestId(),
                result.strategyRunId(),
                result.detail()
        );
    }
}
