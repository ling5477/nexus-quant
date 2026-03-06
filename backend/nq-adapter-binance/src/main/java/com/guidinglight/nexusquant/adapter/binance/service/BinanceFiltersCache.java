package com.guidinglight.nexusquant.adapter.binance.service;

import com.guidinglight.nexusquant.adapter.binance.model.BinanceSymbolFilters;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BinanceFiltersCache 缓存 Spot exchangeInfo 中的 filters 元数据。
 * <p>
 * Why:
 * GateC-2 要求下单前先用交易所元数据做 trim；同时本 PR 仍处于无 key 阶段，
 * 因此先实现“构造时预热 + TTL 惰性刷新”的最小可用缓存，不引入额外调度线程也能复用到后续 TradingAdapter。
 */
public class BinanceFiltersCache {

    private final BinanceExchangeInfoClient exchangeInfoClient;
    private final Clock clock;
    private final Duration refreshInterval;
    private final Map<String, BinanceSymbolFilters> exchangeSymbolIndex;
    private final Map<String, BinanceSymbolFilters> internalSymbolIndex;
    private volatile Instant lastRefreshAt;

    public BinanceFiltersCache(BinanceExchangeInfoClient exchangeInfoClient, Clock clock, Duration refreshInterval) {
        this.exchangeInfoClient = Objects.requireNonNull(exchangeInfoClient, "exchangeInfoClient must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.refreshInterval = Objects.requireNonNull(refreshInterval, "refreshInterval must not be null");
        this.exchangeSymbolIndex = new ConcurrentHashMap<>();
        this.internalSymbolIndex = new ConcurrentHashMap<>();
        this.lastRefreshAt = Instant.EPOCH;
    }

    /**
     * 获取指定 symbol 的 filters；支持内部符号 `BTC-USDT` 与 Binance 原生符号 `BTCUSDT`。
     */
    public BinanceSymbolFilters getRequired(String symbol, String traceId) {
        refreshIfDue(traceId);
        BinanceSymbolFilters filters = lookup(symbol);
        if (filters == null) {
            throw new BinanceApiException(
                    "Binance symbol filters not found, symbol=" + symbol + ", trace_id=" + traceId,
                    0,
                    "/api/v3/exchangeInfo",
                    "BINANCE_SYMBOL_FILTERS_NOT_FOUND",
                    "symbol filters not found",
                    traceId
            );
        }
        return filters;
    }

    /**
     * 导出只读快照供测试与诊断使用。
     */
    public Map<String, BinanceSymbolFilters> snapshot(String traceId) {
        refreshIfDue(traceId);
        return Collections.unmodifiableMap(new LinkedHashMap<>(exchangeSymbolIndex));
    }

    /**
     * 手动立即刷新 cache。
     */
    public synchronized void refreshNow(String traceId) {
        Map<String, BinanceSymbolFilters> refreshed = exchangeInfoClient.fetchSpotExchangeInfo(traceId);
        Map<String, BinanceSymbolFilters> internalIndex = new LinkedHashMap<>();
        for (BinanceSymbolFilters filters : refreshed.values()) {
            internalIndex.put(normalize(filters.internalSymbol()), filters);
        }
        exchangeSymbolIndex.clear();
        exchangeSymbolIndex.putAll(refreshed);
        internalSymbolIndex.clear();
        internalSymbolIndex.putAll(internalIndex);
        lastRefreshAt = Instant.now(clock);
    }

    private void refreshIfDue(String traceId) {
        if (!exchangeSymbolIndex.isEmpty() && lastRefreshAt.plus(refreshInterval).isAfter(Instant.now(clock))) {
            return;
        }
        refreshNow(traceId);
    }

    private BinanceSymbolFilters lookup(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        BinanceSymbolFilters filters = exchangeSymbolIndex.get(normalize(symbol));
        if (filters != null) {
            return filters;
        }
        return internalSymbolIndex.get(normalize(symbol));
    }

    private String normalize(String symbol) {
        return symbol == null ? null : symbol.replace("-", "").replace("_", "").trim().toUpperCase(Locale.ROOT);
    }
}
