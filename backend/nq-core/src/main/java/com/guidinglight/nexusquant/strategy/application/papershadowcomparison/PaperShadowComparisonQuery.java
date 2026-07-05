package com.guidinglight.nexusquant.strategy.application.papershadowcomparison;

import java.util.UUID;

/**
 * PaperShadowComparisonQuery 描述 GateQ-2 Paper vs Shadow 只读对照的查询范围。
 *
 * <p>Why: query 只用于选择已有本地事实，不创建 Paper run、不创建 Shadow run、不启动 runner、
 * 不访问外部网络，也不改变 evaluation / publish / Paper 状态。
 */
public record PaperShadowComparisonQuery(
        String strategyId,
        String strategyVersionId,
        UUID datasetId,
        String evaluationId,
        String publishId,
        String paperRunId,
        String shadowRunId
) {
    public PaperShadowComparisonQuery {
        strategyId = normalize(strategyId);
        strategyVersionId = normalize(strategyVersionId);
        evaluationId = normalize(evaluationId);
        publishId = normalize(publishId);
        paperRunId = normalize(paperRunId);
        shadowRunId = normalize(shadowRunId);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
