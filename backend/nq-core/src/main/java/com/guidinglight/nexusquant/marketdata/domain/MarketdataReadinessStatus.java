package com.guidinglight.nexusquant.marketdata.domain;

/**
 * MarketdataReadinessStatus is the fail-closed status set for local marketdata readiness summaries.
 * <p>
 * Why: GateM-2E must distinguish missing or uncertain local evidence from usable data. `NO_DATA` and
 * `UNKNOWN` are explicit non-ready states and must never be interpreted as `FRESH`.
 */
public enum MarketdataReadinessStatus {
    FRESH,
    STALE,
    VERY_STALE,
    GAP,
    ERROR,
    DISABLED,
    UNKNOWN,
    NO_DATA
}
