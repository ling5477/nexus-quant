package com.guidinglight.nexusquant.strategy.application.shadowvalidation;

/**
 * Shadow Validation Workflow 的派生状态。
 *
 * <p>这些状态均由只读 fact deterministically 派生，不持久化，不代表 review / acknowledge 写侧已经实现，
 * 更不代表交易授权或自动处置完成。
 */
public enum ShadowValidationWorkflowState {
    INTAKE,
    EVIDENCE_REVIEW,
    NEEDS_EVIDENCE,
    READY_FOR_OPERATOR_REVIEW,
    BLOCKED,
    CLOSED_RECOMMENDATION
}
