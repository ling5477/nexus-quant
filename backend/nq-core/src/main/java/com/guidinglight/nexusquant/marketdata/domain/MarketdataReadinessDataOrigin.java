package com.guidinglight.nexusquant.marketdata.domain;

/**
 * MarketdataReadinessDataOrigin mirrors the GateO O-2 DataQualitySummary origin vocabulary.
 * <p>
 * Why: the readiness API exposes diagnostic data provenance only. These values do not prove public
 * outbound execution, real provider readiness, permission grants or trading authorization.
 */
public enum MarketdataReadinessDataOrigin {
    LOCAL_DB,
    FIXTURE,
    FAKE_SERVER,
    PUBLIC_CANDIDATE,
    UNKNOWN
}
