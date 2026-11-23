package com.guidinglight.nexusquant.marketdata.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * MarketdataReadinessQuery is the canonical scope for local readiness aggregation.
 * <p>
 * Why: every query must be bounded by exchange/market/symbol/interval so the backend never performs
 * broad scans or falls back to external exchange probes.
 */
public record MarketdataReadinessQuery(
        String exchangeCode,
        String marketType,
        String symbol,
        BarInterval interval,
        Instant from,
        Instant to
) {
    public MarketdataReadinessQuery {
        exchangeCode = requireText(exchangeCode, "exchangeCode").toUpperCase(Locale.ROOT);
        marketType = requireText(marketType, "marketType").toUpperCase(Locale.ROOT);
        symbol = requireText(symbol, "symbol").toUpperCase(Locale.ROOT);
        interval = Objects.requireNonNull(interval, "interval must not be null");
        if (from != null && to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("to must not be before from");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
