package com.guidinglight.nexusquant.strategy.application.evaluationgate;

/**
 * StrategyEvaluationGateDecision 是对 gateStatus 的业务决策归类。
 *
 * <p>Why: decision 字段面向 API 消费方解释当前能否进入下一步 review。命名刻意使用
 * RESEARCH_EVALUATION 前缀，避免把 evaluation gate 误读成下单、LIVE 或实盘放行。
 */
public enum StrategyEvaluationGateDecision {
    RESEARCH_EVALUATION_READY_FOR_SHADOW_REVIEW,
    RESEARCH_EVALUATION_BLOCKED,
    RESEARCH_EVALUATION_UNKNOWN,
    RESEARCH_EVALUATION_NOT_AVAILABLE
}
