package com.guidinglight.nexusquant.app.web;

import com.guidinglight.nexusquant.core.service.StrategyScheduleScanBatchResult;

import java.util.List;

/**
 * GateEStrategyScheduleScanResponse 描述 GateE-2.2 的扫描摘要与明细。
 */
public record GateEStrategyScheduleScanResponse(
        int scannedCount,
        int triggeredCount,
        int skippedWindowCount,
        int skippedDedupCount,
        int skippedDisabledCount,
        int skippedStrategyDisabledCount,
        int skippedBusyCount,
        int skippedNotDueCount,
        int failedCount,
        List<GateEStrategyScheduleScanDetailResponse> details
) {
    public static GateEStrategyScheduleScanResponse from(StrategyScheduleScanBatchResult result) {
        return new GateEStrategyScheduleScanResponse(
                result.scannedCount(),
                result.triggeredCount(),
                result.skippedWindowCount(),
                result.skippedDedupCount(),
                result.skippedDisabledCount(),
                result.skippedStrategyDisabledCount(),
                result.skippedBusyCount(),
                result.skippedNotDueCount(),
                result.failedCount(),
                result.results().stream().map(GateEStrategyScheduleScanDetailResponse::from).toList()
        );
    }
}
