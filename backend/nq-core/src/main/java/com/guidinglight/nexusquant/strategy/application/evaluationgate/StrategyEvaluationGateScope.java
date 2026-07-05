package com.guidinglight.nexusquant.strategy.application.evaluationgate;

import java.util.UUID;

/**
 * StrategyEvaluationGateScope 回显本次只读诊断范围。
 *
 * <p>Why: scope 只用于追溯 API 查询与后端事实解析结果，不表示策略可以进入真实交易、LIVE、
 * private adapter 或 Shadow runner。缺失字段会在 gate evidence 中 fail-closed。
 */
public record StrategyEvaluationGateScope(
        String strategyId,
        String strategyVersionId,
        UUID datasetId,
        String evaluationId,
        String publishId,
        String paperRunId
) {
}
