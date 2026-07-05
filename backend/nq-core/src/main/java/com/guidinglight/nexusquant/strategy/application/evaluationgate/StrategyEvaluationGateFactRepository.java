package com.guidinglight.nexusquant.strategy.application.evaluationgate;

/**
 * StrategyEvaluationGateFactRepository 暴露 GateQ-1 所需的本地只读事实查询。
 *
 * <p>Why: core 负责 gate 语义，infra 负责 SQL。该 port 只提供 SELECT 聚合入口，不提供写库、
 * 调度、外部 HTTP、credential material、adapter、Paper run 启动或 Shadow runner 能力。
 */
public interface StrategyEvaluationGateFactRepository {

    /**
     * 读取当前 query 范围内的 strategy / dataset / evaluation / publish / Paper 事实。
     *
     * @param query 只读查询范围；缺失字段不会触发事实创造
     * @return 只读事实集合；不存在的事实用 missing fact 表达
     */
    StrategyEvaluationGateFacts loadFacts(StrategyEvaluationGateQuery query);
}
