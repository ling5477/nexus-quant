package com.guidinglight.nexusquant.marketdata.domain;

/**
 * 来源健康状态词汇与 DataQualitySummary 保持一致。
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
