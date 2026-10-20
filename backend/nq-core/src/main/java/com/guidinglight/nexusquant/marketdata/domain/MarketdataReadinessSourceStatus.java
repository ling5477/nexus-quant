package com.guidinglight.nexusquant.marketdata.domain;

/**
 * 来源状态词汇与 DataQualitySummary 保持一致。
 * <p>
 * Why: source status describes read-only marketdata diagnostics. It is not trading authorization and
 * must never be used to infer LIVE, private trading or permission readiness.
 */
public enum MarketdataReadinessSourceStatus {
    ENABLED,
    DISABLED,
    DEGRADED,
    ERROR,
    RATE_LIMITED
}
