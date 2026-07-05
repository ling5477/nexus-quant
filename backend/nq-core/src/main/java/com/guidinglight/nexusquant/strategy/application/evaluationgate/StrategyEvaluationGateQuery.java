package com.guidinglight.nexusquant.strategy.application.evaluationgate;

import java.util.UUID;

/**
 * StrategyEvaluationGateQuery 描述 GateQ-1 只读 evaluation gate 的查询范围。
 *
 * <p>Why: GateQ-1 只能聚合现有 strategy version、dataset、evaluation、publish 和 Paper run
 * 事实。query 只做范围选择，不会触发回测、发布、Paper run、Shadow run、外部网络或数据库写入。
 */
public record StrategyEvaluationGateQuery(
        String strategyId,
        String strategyVersionId,
        UUID datasetId,
        String evaluationId,
        String publishId,
        String paperRunId
) {
    public StrategyEvaluationGateQuery {
        strategyId = normalize(strategyId);
        strategyVersionId = normalize(strategyVersionId);
        evaluationId = normalize(evaluationId);
        publishId = normalize(publishId);
        paperRunId = normalize(paperRunId);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
