package com.guidinglight.nexusquant.adapter.binance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.guidinglight.nexusquant.adapter.binance.model.BinanceSymbolFilters;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.binance.model.BinanceApiCredentials;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * BinanceFiltersCacheTest 验证 cache 的双索引与 TTL 刷新行为。
 */
class BinanceFiltersCacheTest {

    @Test
    void shouldResolveBothExchangeAndInternalSymbolsAndRefreshWhenStale() {
        AtomicInteger fetchCount = new AtomicInteger();
        MutableClock clock = new MutableClock(Instant.parse("2026-03-06T09:00:00Z"));
        BinanceSymbolFilters filters = new BinanceSymbolFilters(
                "BTCUSDT",
                "BTC-USDT",
                "TRADING",
                new BigDecimal("0.01"),
                new BigDecimal("0.01"),
                new BigDecimal("1000000"),
                new BigDecimal("0.00001"),
                new BigDecimal("0.00001"),
                new BigDecimal("9000"),
                null,
                null,
                null,
                new BigDecimal("5"),
                null,
                true,
                false
        );
        BinanceHttpClient dummyHttpClient = new BinanceHttpClient(
                HttpClient.newHttpClient(),
                new ObjectMapper(),
                "http://127.0.0.1:65535",
                Duration.ofSeconds(1),
                new BinanceRequestSigner(),
                () -> 1_700_000_000_000L,
                new BinanceApiCredentials("", "")
        );
        BinanceExchangeInfoClient client = new BinanceExchangeInfoClient(dummyHttpClient) {
            @Override
            public Map<String, BinanceSymbolFilters> fetchSpotExchangeInfo(String traceId) {
                fetchCount.incrementAndGet();
                return Map.of("BTCUSDT", filters);
            }
        };

        BinanceFiltersCache cache = new BinanceFiltersCache(client, clock, Duration.ofSeconds(30));
        BinanceSymbolFilters byInternal = cache.getRequired("BTC-USDT", "trc-cache-internal");
        BinanceSymbolFilters byExchange = cache.getRequired("BTCUSDT", "trc-cache-exchange");

        assertSame(filters, byInternal);
        assertSame(filters, byExchange);
        assertEquals(1, fetchCount.get());

        clock.advance(Duration.ofSeconds(31));
        cache.getRequired("BTCUSDT", "trc-cache-refresh");
        assertEquals(2, fetchCount.get());
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
