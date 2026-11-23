package com.guidinglight.nexusquant.strategy.domain.shadowrun;

/**
 * Paper vs Shadow 一致性复盘状态。
 *
 * <p>该状态只表达复盘比较结果，不表达 approval、trading authorization 或 live-ready。
 */
public enum ShadowConsistencyComparisonStatus {
    CONSISTENT,
    DIVERGED,
    NOT_COMPARABLE,
    PARTIAL,
    FAILED
}
