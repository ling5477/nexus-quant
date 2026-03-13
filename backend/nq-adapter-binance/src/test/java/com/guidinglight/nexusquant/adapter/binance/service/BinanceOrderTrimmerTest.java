package com.guidinglight.nexusquant.adapter.binance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderRequest;
import com.guidinglight.nexusquant.adapter.binance.model.BinanceSymbolFilters;
import com.guidinglight.nexusquant.adapter.binance.model.BinanceTrimResult;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * BinanceOrderTrimmerTest 覆盖 GateC-2 要求的核心 trim 与拒单场景。
 */
class BinanceOrderTrimmerTest {

    private final BinanceOrderTrimmer trimmer = new BinanceOrderTrimmer();

    @Test
    void shouldTrimPriceAndQtyByTickAndStepSize() {
        BinanceTrimResult result = trimmer.trimAndValidate(limitOrder("BTC-USDT", "123.4567", "0.123456"), tradingFilters());

        assertTrue(result.accepted());
        assertEquals(0, result.trimmedPrice().compareTo(new BigDecimal("123.45")));
        assertEquals(0, result.trimmedQty().compareTo(new BigDecimal("0.1234")));
    }

    @Test
    void shouldRejectWhenQtyBelowMinQtyAfterTrim() {
        BinanceTrimResult result = trimmer.trimAndValidate(limitOrder("BTC-USDT", "100.00", "0.00019"), tradingFilters());

        assertFalse(result.accepted());
        assertEquals("BINANCE_QTY_BELOW_MIN_QTY", result.rejectCode());
    }

    @Test
    void shouldRejectWhenNotionalBelowMinNotional() {
        BinanceTrimResult result = trimmer.trimAndValidate(limitOrder("BTC-USDT", "10.00", "0.1000"), tradingFilters());

        assertFalse(result.accepted());
        assertEquals("BINANCE_NOTIONAL_BELOW_MIN_NOTIONAL", result.rejectCode());
    }

    @Test
    void shouldRejectWhenSymbolIsNotTrading() {
        BinanceSymbolFilters halted = new BinanceSymbolFilters(
                "BTCUSDT",
                "BTC-USDT",
                "BREAK",
                new BigDecimal("0.01"),
                new BigDecimal("0.01"),
                new BigDecimal("1000000"),
                new BigDecimal("0.0001"),
                new BigDecimal("0.0010"),
                new BigDecimal("9000"),
                null,
                null,
                null,
                new BigDecimal("5.00"),
                null,
                true,
                false
        );

        BinanceTrimResult result = trimmer.trimAndValidate(limitOrder("BTC-USDT", "100.00", "0.1000"), halted);

        assertFalse(result.accepted());
        assertEquals("BINANCE_SYMBOL_NOT_TRADING", result.rejectCode());
    }

    @Test
    void shouldRejectWhenSymbolDoesNotExistInCache() {
        BinanceFiltersCache cache = new BinanceFiltersCache(
                new BinanceExchangeInfoClient(new BinanceHttpClient(
                        java.net.http.HttpClient.newHttpClient(),
                        new com.fasterxml.jackson.databind.ObjectMapper(),
                        "http://127.0.0.1:65535",
                        java.time.Duration.ofSeconds(1),
                        new BinanceRequestSigner(),
                        () -> 1_700_000_000_000L,
                        new com.guidinglight.nexusquant.adapter.binance.model.BinanceApiCredentials("", "")
                )) {
                    @Override
                    public java.util.Map<String, BinanceSymbolFilters> fetchSpotExchangeInfo(String traceId) {
                        return java.util.Map.of("BTCUSDT", tradingFilters());
                    }
                },
                java.time.Clock.fixed(Instant.parse("2026-03-06T09:00:00Z"), java.time.ZoneOffset.UTC),
                java.time.Duration.ofMinutes(5)
        );

        BinanceTrimResult result = trimmer.trimAndValidate(limitOrder("ETH-USDT", "100.00", "0.1000"), cache);

        assertFalse(result.accepted());
        assertEquals("BINANCE_SYMBOL_NOT_FOUND", result.rejectCode());
    }

    @Test
    void shouldFallbackToLotSizeWhenMarketLotStepSizeIsZero() {
        BinanceSymbolFilters filters = new BinanceSymbolFilters(
                "BTCUSDT",
                "BTC-USDT",
                "TRADING",
                new BigDecimal("0.01"),
                new BigDecimal("0.01"),
                new BigDecimal("1000000"),
                new BigDecimal("0.0001"),
                new BigDecimal("0.0001"),
                new BigDecimal("9000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("9000"),
                new BigDecimal("5.00"),
                null,
                true,
                false
        );

        BinanceTrimResult result = trimmer.trimAndValidate(marketOrder("BTC-USDT", "0.00019"), filters);

        assertTrue(result.accepted());
        assertEquals(0, result.trimmedQty().compareTo(new BigDecimal("0.0001")));
    }

    private AdapterOrderRequest limitOrder(String symbol, String price, String qty) {
        return new AdapterOrderRequest(
                "req-binance-test",
                "ord-binance-test",
                1L,
                "BINANCE",
                symbol,
                "cid-binance-test",
                "1:cid-binance-test",
                "BUY",
                "LIMIT",
                new BigDecimal(price),
                new BigDecimal(qty),
                null,
                "GTC",
                "strategy",
                "run-binance-test",
                "trc-binance-test"
        );
    }

    private AdapterOrderRequest marketOrder(String symbol, String qty) {
        return new AdapterOrderRequest(
                "req-binance-market-test",
                "ord-binance-market-test",
                1L,
                "BINANCE",
                symbol,
                "cid-binance-market-test",
                "1:cid-binance-market-test",
                "BUY",
                "MARKET",
                null,
                new BigDecimal(qty),
                null,
                "IOC",
                "strategy",
                "run-binance-test",
                "trc-binance-market-test"
        );
    }

    private BinanceSymbolFilters tradingFilters() {
        return new BinanceSymbolFilters(
                "BTCUSDT",
                "BTC-USDT",
                "TRADING",
                new BigDecimal("0.01"),
                new BigDecimal("0.01"),
                new BigDecimal("1000000"),
                new BigDecimal("0.0001"),
                new BigDecimal("0.0010"),
                new BigDecimal("9000"),
                null,
                null,
                null,
                new BigDecimal("5.00"),
                null,
                true,
                false
        );
    }
}
