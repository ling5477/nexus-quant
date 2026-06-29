package com.guidinglight.nexusquant.marketdata.domain;

/**
 * MarketdataBackendSupportLevel explains how much backend evidence supports a readiness response.
 * <p>
 * Why: GateM-2E deliberately avoids a migration, so clients must know the summary is derived from
 * existing bars and ingestion tables rather than a persisted source-health table.
 */
public enum MarketdataBackendSupportLevel {
    NO_MIGRATION_MVP,
    UNAVAILABLE,
    FUTURE_PERSISTED_SOURCE_HEALTH_REQUIRED
}
