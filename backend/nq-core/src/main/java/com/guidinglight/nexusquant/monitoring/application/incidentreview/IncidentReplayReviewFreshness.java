package com.guidinglight.nexusquant.monitoring.application.incidentreview;

/**
 * IncidentReplayReviewFreshness 表示 review item 所依赖 evidence 的新鲜度。
 *
 * <p>FRESH 只表示本地诊断证据未过期，不表示交易准入、LIVE readiness 或自动处置完成。
 */
public enum IncidentReplayReviewFreshness {
    FRESH,
    STALE,
    MISSING,
    UNKNOWN
}
