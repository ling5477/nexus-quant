package com.guidinglight.nexusquant.strategy.application.consistencyevidence;

/**
 * ConsistencyEvidenceDivergenceSeverity 表达 Paper vs Shadow consistency evidence 的诊断优先级。
 *
 * <p>该枚举只用于排序和人工复核优先级。`HIGH` 或 `CRITICAL` 不表示自动处置完成、不表示风险已解除，
 * 也不表示交易授权或 LIVE readiness。
 */
public enum ConsistencyEvidenceDivergenceSeverity {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
    UNKNOWN
}
