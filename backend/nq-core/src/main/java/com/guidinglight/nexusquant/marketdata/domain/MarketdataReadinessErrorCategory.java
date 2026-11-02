package com.guidinglight.nexusquant.marketdata.domain;

/**
 * 稳定的就绪错误类别与数据质量诊断保持一致。
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
