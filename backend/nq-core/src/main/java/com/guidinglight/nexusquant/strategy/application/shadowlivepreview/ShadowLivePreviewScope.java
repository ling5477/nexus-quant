package com.guidinglight.nexusquant.strategy.application.shadowlivepreview;

import java.util.UUID;

/**
 * ShadowLivePreviewScope 是预览 API 回显的查询范围。
 *
 * <p>Why: scope 仅用于审计和 trace preview，帮助使用方确认本次只读预览聚合了哪些既有事实；
 * 它不代表任何运行、交易或实盘放行。
 */
public record ShadowLivePreviewScope(
        String strategyId,
        String strategyVersionId,
        UUID datasetId,
        String evaluationId,
        String publishId,
        String paperRunId,
        String shadowRunId
) {
}
