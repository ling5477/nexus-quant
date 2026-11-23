package com.guidinglight.nexusquant.strategy.application.shadowrun.model;

/**
 * Shadow Run overview divergence severity。
 *
 * <p>该枚举只用于验证只读视图 read model的风险排序和诊断展示，不回写 `shadow_runs.status`，
 * 不表示 approval、LIVE ready、trading authorization 或真实 provider 可用。
 */
public enum ShadowRunOverviewDivergenceSeverity {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
    UNKNOWN
}
