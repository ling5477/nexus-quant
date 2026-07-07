package com.guidinglight.nexusquant.strategy.application.shadowrun;

/**
 * PaperShadowConsistencyDrilldownComparisonStatus 是 GateS-2 drilldown 响应层的一致性状态。
 *
 * <p>Why：底层 `shadow_consistency_reports.comparison_status` 当前没有 `NO_REPORT` 或
 * `STALE_EVIDENCE`，但 drilldown 需要把“没有 report”和“证据不足”显式返回给前端和审查者。
 * 这些状态只表达证据层诊断，不表示 approval、LIVE ready、trading authorization 或真实 provider 可用。
 */
public enum PaperShadowConsistencyDrilldownComparisonStatus {
    CONSISTENT,
    DIVERGED,
    PARTIAL,
    NOT_COMPARABLE,
    FAILED,
    STALE_EVIDENCE,
    NO_REPORT
}
