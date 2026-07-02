package com.guidinglight.nexusquant.adapter.api.publicmarketdata;

/**
 * PublicMarketDataOutboundErrorCategory 是 O-1 出站结果的最小错误分类。
 *
 * <p>Why: HTTP status、timeout、policy denial 和 fallback 不能直接写进 Data Quality；
 * 先归一为稳定枚举，后续 O-2 才能把 source health、freshness、gap 和 data origin 做成可审计模型。
 * 枚举不携带 raw response 或 credential material。</p>
 */
public enum PublicMarketDataOutboundErrorCategory {
    NONE,
    DISABLED,
    DENIED,
    RATE_LIMITED,
    TIMEOUT,
    TEMPORARY_FAILURE,
    INVALID_RESPONSE,
    STALE,
    GAP,
    TRANSPORT_ERROR
}
