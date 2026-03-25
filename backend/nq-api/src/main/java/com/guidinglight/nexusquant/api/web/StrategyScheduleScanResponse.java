package com.guidinglight.nexusquant.api.web;

import com.guidinglight.nexusquant.core.service.StrategyScheduleScanBatchResult;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * StrategyScheduleScanResponse 描述接口响应体。
 */
@Schema(name = "StrategyScheduleScanResponse", description = "接口响应体")
public record StrategyScheduleScanResponse(
        @Schema(description = "scannedCount")
        int scannedCount,
        @Schema(description = "triggeredCount")
        int triggeredCount,
        @Schema(description = "skippedWindowCount")
        int skippedWindowCount,
        @Schema(description = "skippedDedupCount")
        int skippedDedupCount,
        @Schema(description = "skippedDisabledCount")
        int skippedDisabledCount,
        @Schema(description = "skippedStrategyDisabledCount")
        int skippedStrategyDisabledCount,
        @Schema(description = "skippedBusyCount")
        int skippedBusyCount,
        @Schema(description = "skippedNotDueCount")
        int skippedNotDueCount,
        @Schema(description = "failedCount")
        int failedCount,
        @Schema(description = "details")
        List<StrategyScheduleScanDetailResponse> details
) {
    public static StrategyScheduleScanResponse from(StrategyScheduleScanBatchResult result) {
        return new StrategyScheduleScanResponse(
                result.scannedCount(),
                result.triggeredCount(),
                result.skippedWindowCount(),
                result.skippedDedupCount(),
                result.skippedDisabledCount(),
                result.skippedStrategyDisabledCount(),
                result.skippedBusyCount(),
                result.skippedNotDueCount(),
                result.failedCount(),
                result.results().stream().map(StrategyScheduleScanDetailResponse::from).toList()
        );
    }
}
