package com.guidinglight.nexusquant.strategy.application.evaluationgate.model;

/**
 * StrategyValidationDecision 表示验证只读视图 validation overview的验证层决策。
 *
 * <p>这些枚举只描述研究/评估证据状态，不代表交易授权、LIVE 可用或真实订单权限。
 */
public enum StrategyValidationDecision {
    APPROVED,
    REJECTED,
    NEEDS_REVIEW,
    BLOCKED,
    NO_EVIDENCE,
    STALE_EVIDENCE
}
