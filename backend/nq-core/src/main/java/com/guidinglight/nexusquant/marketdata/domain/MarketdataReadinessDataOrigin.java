package com.guidinglight.nexusquant.marketdata.domain;

/**
 * MarketdataReadinessDataOrigin 与 DataQualitySummary 使用同一来源词汇。
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
