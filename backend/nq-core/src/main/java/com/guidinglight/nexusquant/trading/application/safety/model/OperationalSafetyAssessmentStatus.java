package com.guidinglight.nexusquant.trading.application.safety.model;

/**
 * 只读诊断 internal diagnostic assessment的封闭状态集合。
 */
public enum OperationalSafetyAssessmentStatus {
    PASS,
    BLOCKED,
    UNKNOWN,
    NOT_EVALUATED
}
