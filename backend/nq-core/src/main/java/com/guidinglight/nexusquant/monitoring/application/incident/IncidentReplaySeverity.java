package com.guidinglight.nexusquant.monitoring.application.incident;

/**
 * IncidentReplaySeverity 表示 GateS-6 Incident / Replay overview 的诊断优先级。
 *
 * <p>该枚举只用于 monitoring read model 展示，不表示交易授权、LIVE ready 或 real provider ready。
 */
public enum IncidentReplaySeverity {
    NONE,
    INFO,
    WARNING,
    HIGH,
    CRITICAL,
    UNKNOWN
}
