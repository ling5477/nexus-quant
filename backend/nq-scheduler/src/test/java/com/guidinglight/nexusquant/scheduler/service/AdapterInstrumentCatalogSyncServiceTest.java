package com.guidinglight.nexusquant.scheduler.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guidinglight.nexusquant.adapter.binance.service.BinanceApiException;
import com.guidinglight.nexusquant.adapter.binance.service.BinanceExchangeAdapter;
import com.guidinglight.nexusquant.adapter.binance.service.BinanceFiltersCache;
import com.guidinglight.nexusquant.adapter.okx.service.OkxExchangeAdapter;
import com.guidinglight.nexusquant.marketdata.application.instrument.InstrumentCatalogService;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class AdapterInstrumentCatalogSyncServiceTest {

    private static final Clock TEST_CLOCK = Clock.fixed(Instant.parse("2026-05-29T09:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldRejectSyncBeforeTouchingExternalAdaptersWhenCatalogSyncIsDisabled() {
        InstrumentCatalogService instrumentCatalogService = mock(InstrumentCatalogService.class);
        OkxExchangeAdapter okxExchangeAdapter = mock(OkxExchangeAdapter.class);
        BinanceExchangeAdapter binanceExchangeAdapter = mock(BinanceExchangeAdapter.class);
        AdapterInstrumentCatalogSyncService service = new AdapterInstrumentCatalogSyncService(
                instrumentCatalogService,
                okxExchangeAdapter,
                binanceExchangeAdapter,
                TEST_CLOCK,
                false
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.sync("BINANCE", "trc-freeze-catalog-disabled")
        );

        assertEquals("当前环境禁用外部交易所同步", exception.getMessage());
        verify(binanceExchangeAdapter, never()).filtersCache();
        verify(okxExchangeAdapter, never()).instrumentsCache();
    }

    @Test
    void shouldConvertBinanceCatalogFailureToControlledBusinessConflict() {
        InstrumentCatalogService instrumentCatalogService = mock(InstrumentCatalogService.class);
        OkxExchangeAdapter okxExchangeAdapter = mock(OkxExchangeAdapter.class);
        BinanceExchangeAdapter binanceExchangeAdapter = mock(BinanceExchangeAdapter.class);
        BinanceFiltersCache filtersCache = mock(BinanceFiltersCache.class);
        BinanceApiException binanceFailure = new BinanceApiException(
                "Binance request failed, status=451, endpoint=/api/v3/exchangeInfo",
                451,
                "/api/v3/exchangeInfo",
                "451",
                "Unavailable For Legal Reasons",
                "trc-binance-451"
        );
        when(binanceExchangeAdapter.filtersCache()).thenReturn(filtersCache);
        when(filtersCache.snapshot("trc-binance-451")).thenThrow(binanceFailure);
        AdapterInstrumentCatalogSyncService service = new AdapterInstrumentCatalogSyncService(
                instrumentCatalogService,
                okxExchangeAdapter,
                binanceExchangeAdapter,
                TEST_CLOCK,
                true
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.sync("BINANCE", "trc-binance-451")
        );

        assertEquals("外部交易所 instrument catalog 同步暂不可用", exception.getMessage());
        assertSame(binanceFailure, exception.getCause());
    }
}
