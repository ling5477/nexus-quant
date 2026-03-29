package com.guidinglight.nexusquant.strategy.application;

import java.util.List;

/**
 * StrategyScheduleScanBatchResult 汇总一次 scanOnce 的批量结果。
 * <p>
 * Why:
 * GateE-2.2 需要同时返回统计摘要和逐条明细，便于测试与排障复核，
 * 因此不能继续直接返回平铺列表。
 */
public record StrategyScheduleScanBatchResult(
        int scannedCount,
        int triggeredCount,
        int skippedWindowCount,
        int skippedDedupCount,
        int skippedDisabledCount,
        int skippedStrategyDisabledCount,
        int skippedBusyCount,
        int skippedNotDueCount,
        int failedCount,
        List<StrategyScheduleScanResult> results
) {

    public static StrategyScheduleScanBatchResult from(List<StrategyScheduleScanResult> results) {
        return new StrategyScheduleScanBatchResult(
                results.size(),
                count(results, StrategyScheduleScanOutcome.TRIGGERED),
                count(results, StrategyScheduleScanOutcome.SKIPPED_WINDOW),
                count(results, StrategyScheduleScanOutcome.SKIPPED_DEDUP),
                count(results, StrategyScheduleScanOutcome.SKIPPED_DISABLED),
                count(results, StrategyScheduleScanOutcome.SKIPPED_STRATEGY_DISABLED),
                count(results, StrategyScheduleScanOutcome.SKIPPED_BUSY),
                count(results, StrategyScheduleScanOutcome.SKIPPED_NOT_DUE),
                count(results, StrategyScheduleScanOutcome.FAILED),
                List.copyOf(results)
        );
    }

    private static int count(List<StrategyScheduleScanResult> results, StrategyScheduleScanOutcome outcome) {
        return (int) results.stream().filter(item -> item.outcome() == outcome).count();
    }
}


