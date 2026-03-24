package com.guidinglight.nexusquant.app.web;

import com.guidinglight.nexusquant.core.service.StrategyScheduleScanResult;

/**
 * GateEStrategyScheduleScanResponse 描述 GateE-2.1 最小扫描结果。
 */
public record GateEStrategyScheduleScanResponse(
        String scheduleJobId,
        String strategyId,
        boolean triggered,
        String requestId,
        String strategyRunId,
        String reason
) {
    public static GateEStrategyScheduleScanResponse from(StrategyScheduleScanResult result) {
        return new GateEStrategyScheduleScanResponse(
                result.scheduleJobId(),
                result.strategyId(),
                result.triggered(),
                result.requestId(),
                result.strategyRunId(),
                result.reason()
        );
    }
}
