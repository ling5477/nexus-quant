package com.guidinglight.nexusquant.core.service;

/**
 * StrategyScheduleScanResult 描述一次 schedule scan 对单个计划的结构化结果。
 */
public record StrategyScheduleScanResult(
        String scheduleJobId,
        String strategyId,
        StrategyScheduleScanOutcome outcome,
        String requestId,
        String strategyRunId,
        String detail
) {
    public boolean triggered() {
        return outcome == StrategyScheduleScanOutcome.TRIGGERED;
    }
}
