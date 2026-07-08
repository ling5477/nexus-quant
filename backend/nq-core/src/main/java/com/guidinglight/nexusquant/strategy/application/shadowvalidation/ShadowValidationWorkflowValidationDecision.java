package com.guidinglight.nexusquant.strategy.application.shadowvalidation;

/**
 * Shadow Validation Workflow 的验证材料决策。
 *
 * <p>VALIDATION_READY 只表示材料可进入人工复核；不得解释为 trade approval、LIVE ready、真实 provider
 * ready 或任何自动交易授权。
 */
public enum ShadowValidationWorkflowValidationDecision {
    NO_DECISION,
    VALIDATION_READY,
    NEEDS_REVIEW,
    REJECTED,
    BLOCKED,
    STALE_EVIDENCE
}
