package com.guidinglight.nexusquant.monitoring.application.incidentreview.model;

/**
 * IncidentReplayReviewSeverity 是事件回放复核工作流 review item 的诊断优先级。
 *
 * <p>HIGH / CRITICAL 只表示人工诊断优先级，不表示交易风险已处理、自动风控已完成或 LIVE readiness。
 */
public enum IncidentReplayReviewSeverity {
    NONE,
    INFO,
    WARNING,
    HIGH,
    CRITICAL,
    UNKNOWN
}
