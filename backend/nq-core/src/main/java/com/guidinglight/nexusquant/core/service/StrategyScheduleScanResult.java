package com.guidinglight.nexusquant.core.service;

/**
 * StrategyScheduleScanResult 描述一次 schedule scan 命中的最小结果。
 */
public record StrategyScheduleScanResult(
        String scheduleJobId,
        String strategyId,
        boolean triggered,
        String requestId,
        String strategyRunId,
        String reason
) {
}
