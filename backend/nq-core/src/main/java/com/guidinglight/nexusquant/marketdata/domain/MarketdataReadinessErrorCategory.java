package com.guidinglight.nexusquant.marketdata.domain;

/**
 * Stable readiness error categories aligned with GateO O-2 Data Quality diagnostics.
 * <p>
 * Why: API clients need a safe classification without raw provider payload, credentials, headers or
 * private exchange details.
 */
public enum MarketdataReadinessErrorCategory {
    NONE,
    DISABLED,
    POLICY_DENIED,
    RATE_LIMITED,
    TIMEOUT,
    TEMPORARY_FAILURE,
    INVALID_RESPONSE,
    STALE,
    GAP,
    TRANSPORT_ERROR,
    UNKNOWN
}
