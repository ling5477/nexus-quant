package com.guidinglight.nexusquant.marketdata.domain;

import java.time.Instant;
import java.util.List;

/** 展示事实独立于历史入库、策略和估值使用的 K 线类型。 */
public record PublicMarketSnapshot(
        String exchangeCode, String marketType, String symbol, String interval,
        String lastPrice, Instant lastPriceObservedAt, Instant lastPriceReceivedAt, String lastPriceSource,
        List<Bar> bars, Instant barsObservedAt, String source, Instant observedAt,
        Freshness freshness, Freshness tickerFreshness, Freshness barsFreshness,
        SourceStatus tickerStatus, SourceStatus barsStatus, Instant retryAt) {

    public record Bar(Instant openTime, Instant closeTime, String openPrice, String highPrice,
               String lowPrice, String closePrice, String volume, BarStatus status) {}

    public enum BarStatus { FORMING, CLOSED }
    public enum Freshness { FRESH, STALE, PARTIAL, UNAVAILABLE }
    public enum SourceStatus {
        AVAILABLE, DISABLED, DENIED, RATE_LIMITED, TIMEOUT, SOURCE_UNAVAILABLE,
        INVALID_RESPONSE, INCOMPLETE_RESPONSE, BUSY
    }
}
