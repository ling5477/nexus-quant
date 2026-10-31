package com.guidinglight.nexusquant.trading.application.riskpreflight.model;

/**
 * 只读诊断 diagnostic risk preflight 各独立维度的封闭状态。
 */
public enum DiagnosticOrderRiskPreflightStatus {
    PASS,
    BLOCKED,
    UNKNOWN,
    NOT_EVALUATED
}
