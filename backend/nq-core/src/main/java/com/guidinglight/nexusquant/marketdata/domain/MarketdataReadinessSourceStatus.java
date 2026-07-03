package com.guidinglight.nexusquant.marketdata.domain;

/**
 * Source status vocabulary aligned with GateO O-2 DataQualitySummary.
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
