package com.guidinglight.nexusquant.strategy.application.papershadowcomparison;

import java.util.UUID;

/**
 * PaperShadowComparisonScope 回显本次只读对照查询范围。
 *
 * <p>Why: GateQ-2 对照必须能复盘 strategy version、dataset、evaluation、publish、Paper run
 * 和 Shadow run 的链路。scope 只描述查询范围；缺失字段由 evidence fail-closed，不会触发写库或 runner。
 */
public record PaperShadowComparisonScope(
        String strategyId,
        String strategyVersionId,
        UUID datasetId,
        String evaluationId,
        String publishId,
        String paperRunId,
        String shadowRunId
) {
}
