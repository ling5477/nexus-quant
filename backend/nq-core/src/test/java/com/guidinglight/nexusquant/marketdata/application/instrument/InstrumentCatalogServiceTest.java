package com.guidinglight.nexusquant.marketdata.application.instrument;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.guidinglight.nexusquant.marketdata.domain.instrument.InstrumentCatalogItem;
import com.guidinglight.nexusquant.marketdata.domain.instrument.port.InstrumentCatalogRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class InstrumentCatalogServiceTest {

    @Test
    void shouldUpsertAndFilterInstrumentCatalogByExchange() {
        InMemoryInstrumentCatalogRepository repository = new InMemoryInstrumentCatalogRepository();
        InstrumentCatalogService service = new InstrumentCatalogService(repository);
        Instant syncedAt = Instant.parse("2026-04-18T09:00:00Z");

        InstrumentCatalogUpsertStats firstSync = service.upsertCatalogItems(List.of(
                new InstrumentCatalogItem(
                        "BINANCE",
                        "SPOT",
                        "BTCUSDT",
                        "BTC-USDT",
                        "BTC",
                        "USDT",
                        "TRADING",
                        new BigDecimal("0.01"),
                        new BigDecimal("0.0001"),
                        new BigDecimal("0.001"),
                        "BINANCE_FILTERS_CACHE"
                ),
                new InstrumentCatalogItem(
                        "OKX",
                        "SPOT",
                        "BTC-USDT",
                        "BTC-USDT",
                        "BTC",
                        "USDT",
                        "LIVE",
                        new BigDecimal("0.01"),
                        new BigDecimal("0.0001"),
                        new BigDecimal("0.001"),
                        "OKX_INSTRUMENTS_CACHE"
                )
        ), syncedAt);

        InstrumentCatalogUpsertStats secondSync = service.upsertCatalogItems(List.of(
                new InstrumentCatalogItem(
                        "BINANCE",
                        "SPOT",
                        "BTCUSDT",
                        "BTC-USDT",
                        "BTC",
                        "USDT",
                        "TRADING",
                        new BigDecimal("0.1"),
                        new BigDecimal("0.0001"),
                        new BigDecimal("0.001"),
                        "BINANCE_FILTERS_CACHE"
                )
        ), syncedAt.plusSeconds(60));

        assertEquals(2, firstSync.insertedCount());
        assertEquals(0, firstSync.updatedCount());
        assertEquals(0, secondSync.insertedCount());
        assertEquals(1, secondSync.updatedCount());
        assertEquals(1, service.list("BINANCE").size());
        assertEquals(2, service.list(null).size());
        assertEquals(new BigDecimal("0.1"), service.list("BINANCE").getFirst().tickSize());
    }

    private static final class InMemoryInstrumentCatalogRepository implements InstrumentCatalogRepository {

        private final Map<String, InstrumentCatalogItem> storage = new LinkedHashMap<>();
        private long nextId = 1L;

        @Override
        public List<InstrumentCatalogItem> list(String exchangeCode) {
            return storage.values().stream()
                    .filter(item -> exchangeCode == null || item.exchangeCode().equalsIgnoreCase(exchangeCode))
                    .toList();
        }

        @Override
        public InstrumentCatalogUpsertStats upsertAll(List<InstrumentCatalogItem> items, Instant syncedAt) {
            int inserted = 0;
            int updated = 0;
            for (InstrumentCatalogItem item : items) {
                String key = item.exchangeCode() + ":" + item.exchangeSymbol();
                InstrumentCatalogItem existing = storage.get(key);
                if (existing == null) {
                    storage.put(key, new InstrumentCatalogItem(
                            nextId++,
                            item.exchangeCode(),
                            item.instrumentType(),
                            item.exchangeSymbol(),
                            item.internalSymbol(),
                            item.baseAsset(),
                            item.quoteAsset(),
                            item.status(),
                            item.tickSize(),
                            item.stepSize(),
                            item.minQuantity(),
                            item.source(),
                            syncedAt,
                            syncedAt,
                            syncedAt
                    ));
                    inserted++;
                    continue;
                }
                storage.put(key, new InstrumentCatalogItem(
                        existing.instrumentId(),
                        item.exchangeCode(),
                        item.instrumentType(),
                        item.exchangeSymbol(),
                        item.internalSymbol(),
                        item.baseAsset(),
                        item.quoteAsset(),
                        item.status(),
                        item.tickSize(),
                        item.stepSize(),
                        item.minQuantity(),
                        item.source(),
                        syncedAt,
                        existing.createdAt(),
                        syncedAt
                ));
                updated++;
            }
            return new InstrumentCatalogUpsertStats(inserted, updated);
        }
    }
}
