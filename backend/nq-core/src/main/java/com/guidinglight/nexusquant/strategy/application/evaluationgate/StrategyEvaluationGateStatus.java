package com.guidinglight.nexusquant.strategy.application.evaluationgate;

/**
 * StrategyEvaluationGateStatus 是 GateQ-1 evaluation gate 的诊断状态枚举。
 *
 * <p>Why: 本枚举只回答“研究与评估证据是否足以进入后续 Shadow review”。它不表达交易授权，
 * 因此不得新增 LIVE_READY、TRADE_APPROVED、AUTHORIZED 等放行语义。
 */
public enum StrategyEvaluationGateStatus {
    READY_FOR_SHADOW_REVIEW,
    BLOCKED_MISSING_STRATEGY_VERSION,
    BLOCKED_MISSING_DATASET,
    BLOCKED_MISSING_EVALUATION,
    BLOCKED_EVALUATION_FAILED,
    BLOCKED_DATA_QUALITY,
    BLOCKED_MISSING_PAPER_EVIDENCE,
    BLOCKED_NOT_PUBLISHED,
    UNKNOWN,
    NOT_AVAILABLE
}
