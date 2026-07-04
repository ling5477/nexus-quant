package com.guidinglight.nexusquant.marketdata.domain;

/**
 * Source health vocabulary aligned with GateO O-2 DataQualitySummary.
 * <p>
 * Why: this is source-health diagnostics for public marketdata readiness only. HEALTHY does not mean
 * real provider readiness, private trading readiness, LIVE readiness or permission authorization.
 */
public enum MarketdataReadinessSourceHealth {
    HEALTHY,
    DEGRADED,
    RATE_LIMITED,
    TIMEOUT,
    ERROR,
    UNKNOWN
}
