package com.guidinglight.nexusquant.marketdata.domain;

/**
 * MarketdataReadinessStatus is the fail-closed status set for local marketdata readiness summaries.
 * <p>
 * 原因：行情就绪状态必须区分本地证据缺失、不确定与可用数据。 `NO_DATA` and
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
