package com.guidinglight.nexusquant.marketdata.domain;

/**
 * Gap diagnostic state for the MarketData readiness read model.
 * <p>
 * Why: a numeric gap count alone cannot distinguish "no gap" from "insufficient evidence". UNKNOWN
 * and PARTIAL remain fail-closed and must not be interpreted as trading-ready data.
 */
public enum MarketdataReadinessGapStatus {
    NONE,
    GAP,
    PARTIAL,
    UNKNOWN
}
