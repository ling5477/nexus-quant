package com.guidinglight.nexusquant.scheduler.service;

import com.guidinglight.nexusquant.adapter.binance.model.BinanceSymbolFilters;
import com.guidinglight.nexusquant.adapter.binance.service.BinanceExchangeAdapter;
import com.guidinglight.nexusquant.adapter.okx.model.OkxInstrument;
import com.guidinglight.nexusquant.adapter.okx.service.OkxExchangeAdapter;
import com.guidinglight.nexusquant.marketdata.application.instrument.InstrumentCatalogService;
import com.guidinglight.nexusquant.marketdata.application.instrument.InstrumentCatalogSyncResult;
import com.guidinglight.nexusquant.marketdata.application.instrument.InstrumentCatalogSyncService;
import com.guidinglight.nexusquant.marketdata.application.instrument.InstrumentCatalogUpsertStats;
import com.guidinglight.nexusquant.marketdata.domain.instrument.InstrumentCatalogItem;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * AdapterInstrumentCatalogSyncService 把 adapter cache 中的 instrument 元数据同步到正式 catalog。
 * <p>
 * Why:
 * PRE-2 的目标不是让前端直接读取 adapter cache，而是先把交易所 instrument 元数据沉淀到统一 catalog，
 * 后续 selector、research dataset 和 trading precision 都围绕这份正式事实工作。
 */
@Component
public class AdapterInstrumentCatalogSyncService implements InstrumentCatalogSyncService {

    private final InstrumentCatalogService instrumentCatalogService;
    private final OkxExchangeAdapter okxExchangeAdapter;
    private final BinanceExchangeAdapter binanceExchangeAdapter;
    private final Clock clock;

    @Autowired
    public AdapterInstrumentCatalogSyncService(
            InstrumentCatalogService instrumentCatalogService,
            OkxExchangeAdapter okxExchangeAdapter,
            BinanceExchangeAdapter binanceExchangeAdapter
    ) {
        this(instrumentCatalogService, okxExchangeAdapter, binanceExchangeAdapter, Clock.systemUTC());
    }

    AdapterInstrumentCatalogSyncService(
            InstrumentCatalogService instrumentCatalogService,
            OkxExchangeAdapter okxExchangeAdapter,
            BinanceExchangeAdapter binanceExchangeAdapter,
            Clock clock
    ) {
        this.instrumentCatalogService = Objects.requireNonNull(
                instrumentCatalogService,
                "instrumentCatalogService must not be null"
        );
        this.okxExchangeAdapter = Objects.requireNonNull(okxExchangeAdapter, "okxExchangeAdapter must not be null");
        this.binanceExchangeAdapter = Objects.requireNonNull(
                binanceExchangeAdapter,
                "binanceExchangeAdapter must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public InstrumentCatalogSyncResult sync(String exchangeCode, String traceId) {
        Instant startedAt = Instant.now(clock);
        List<String> exchangeCodes = resolveExchangeCodes(exchangeCode);
        List<InstrumentCatalogItem> catalogItems = new ArrayList<>();
        for (String currentExchangeCode : exchangeCodes) {
            catalogItems.addAll(loadExchangeItems(currentExchangeCode, traceId));
        }
        InstrumentCatalogUpsertStats upsertStats = instrumentCatalogService.upsertCatalogItems(catalogItems, startedAt);
        return new InstrumentCatalogSyncResult(
                exchangeCodes,
                catalogItems.size(),
                upsertStats.insertedCount(),
                upsertStats.updatedCount(),
                startedAt,
                Instant.now(clock)
        );
    }

    private List<String> resolveExchangeCodes(String exchangeCode) {
        if (exchangeCode == null || exchangeCode.isBlank()) {
            return List.of("OKX", "BINANCE");
        }
        String normalized = exchangeCode.trim().toUpperCase(Locale.ROOT);
        if (!List.of("OKX", "BINANCE").contains(normalized)) {
            throw new IllegalArgumentException("unsupported exchangeCode: " + normalized);
        }
        return List.of(normalized);
    }

    private List<InstrumentCatalogItem> loadExchangeItems(String exchangeCode, String traceId) {
        return switch (exchangeCode) {
            case "OKX" -> loadOkxItems(traceId);
            case "BINANCE" -> loadBinanceItems(traceId);
            default -> throw new IllegalArgumentException("unsupported exchangeCode: " + exchangeCode);
        };
    }

    private List<InstrumentCatalogItem> loadOkxItems(String traceId) {
        Map<String, OkxInstrument> snapshot = okxExchangeAdapter.instrumentsCache().snapshot(traceId);
        List<InstrumentCatalogItem> items = new ArrayList<>();
        for (OkxInstrument instrument : snapshot.values()) {
            String[] assets = splitSymbol(instrument.instId());
            items.add(new InstrumentCatalogItem(
                    "OKX",
                    "SPOT",
                    instrument.instId(),
                    instrument.instId(),
                    assets[0],
                    assets[1],
                    instrument.state(),
                    instrument.tickSize(),
                    instrument.lotSize(),
                    instrument.minSize(),
                    "OKX_INSTRUMENTS_CACHE"
            ));
        }
        return items;
    }

    private List<InstrumentCatalogItem> loadBinanceItems(String traceId) {
        Map<String, BinanceSymbolFilters> snapshot = binanceExchangeAdapter.filtersCache().snapshot(traceId);
        List<InstrumentCatalogItem> items = new ArrayList<>();
        for (BinanceSymbolFilters filters : snapshot.values()) {
            String[] assets = splitSymbol(filters.internalSymbol());
            items.add(new InstrumentCatalogItem(
                    "BINANCE",
                    "SPOT",
                    filters.exchangeSymbol(),
                    filters.internalSymbol(),
                    assets[0],
                    assets[1],
                    filters.status(),
                    filters.tickSize(),
                    filters.stepSize(),
                    filters.minQty(),
                    "BINANCE_FILTERS_CACHE"
            ));
        }
        return items;
    }

    private String[] splitSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        if (symbol.contains("-")) {
            String[] parts = symbol.split("-", 2);
            return new String[]{parts[0].trim(), parts[1].trim()};
        }
        return new String[]{symbol.trim(), "UNKNOWN"};
    }
}
