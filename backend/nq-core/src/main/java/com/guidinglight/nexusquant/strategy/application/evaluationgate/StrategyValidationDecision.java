package com.guidinglight.nexusquant.strategy.application.evaluationgate;

/**
 * StrategyValidationDecision 表示 GateS-3 validation overview 的验证层决策。
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
