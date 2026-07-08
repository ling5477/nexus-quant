package com.guidinglight.nexusquant.strategy.application.consistencyevidence;

/**
 * ConsistencyEvidenceComparisonStatus 是 GateT-2 consistency evidence overview 的响应状态枚举。
 *
 * <p>Why：底层 `shadow_consistency_reports.comparison_status` 当前只保存已生成 report 的比较状态。
 * Overview 需要额外表达 `NO_REPORT` 和 `UNKNOWN`，但这些值只属于 read model 诊断语义，不会回写
 * `shadow_consistency_reports`，也不会触发 report 创建、runner、scheduler 或交易动作。
 */
public enum ConsistencyEvidenceComparisonStatus {
    CONSISTENT,
    DIVERGED,
    PARTIAL,
    NOT_COMPARABLE,
    FAILED,
    NO_REPORT,
    UNKNOWN
}
