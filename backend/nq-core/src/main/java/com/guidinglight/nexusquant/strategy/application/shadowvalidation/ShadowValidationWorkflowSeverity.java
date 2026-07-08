package com.guidinglight.nexusquant.strategy.application.shadowvalidation;

/**
 * Shadow Validation Workflow 的诊断优先级。
 *
 * <p>severity 只用于 operator review 排序和排障优先级；不能解释为真实交易风险已经处置，也不能映射为
 * order / account / ledger / LIVE 状态。
 */
public enum ShadowValidationWorkflowSeverity {
    NONE,
    INFO,
    WARNING,
    HIGH,
    CRITICAL,
    UNKNOWN
}
