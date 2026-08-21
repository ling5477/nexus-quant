package com.guidinglight.nexusquant.scheduler.service;

import com.guidinglight.nexusquant.adapter.binance.model.BinanceSymbolFilters;
import com.guidinglight.nexusquant.adapter.binance.service.BinanceApiException;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * AdapterInstrumentCatalogSyncService 把 adapter cache 中的 instrument 元数据同步到正式 catalog。
 * <p>
 * Why:
 * PRE-2 的目标不是让前端直接读取 adapter cache，而是先把交易所 instrument 元数据沉淀到统一 catalog，
 * 后续 selector、research dataset 和 trading precision 都围绕这份正式事实工作。
 */
@Component
@ConditionalOnProperty(
        prefix = "nq.runtime.trading-components",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class AdapterInstrumentCatalogSyncService implements InstrumentCatalogSyncService {

    private final InstrumentCatalogService instrumentCatalogService;
    private final OkxExchangeAdapter okxExchangeAdapter;
    private final BinanceExchangeAdapter binanceExchangeAdapter;
    private final Clock clock;
    private final boolean catalogSyncEnabled;

    @Autowired
    public AdapterInstrumentCatalogSyncService(
            InstrumentCatalogService instrumentCatalogService,
            OkxExchangeAdapter okxExchangeAdapter,
            BinanceExchangeAdapter binanceExchangeAdapter,
            @Value("${nq.instrument.catalog-sync.enabled:true}") boolean catalogSyncEnabled
    ) {
        this(instrumentCatalogService, okxExchangeAdapter, binanceExchangeAdapter, Clock.systemUTC(), catalogSyncEnabled);
    }

    AdapterInstrumentCatalogSyncService(
            InstrumentCatalogService instrumentCatalogService,
            OkxExchangeAdapter okxExchangeAdapter,
            BinanceExchangeAdapter binanceExchangeAdapter,
            Clock clock
    ) {
        this(instrumentCatalogService, okxExchangeAdapter, binanceExchangeAdapter, clock, true);
    }

    AdapterInstrumentCatalogSyncService(
            InstrumentCatalogService instrumentCatalogService,
            OkxExchangeAdapter okxExchangeAdapter,
            BinanceExchangeAdapter binanceExchangeAdapter,
            Clock clock,
            boolean catalogSyncEnabled
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
        this.catalogSyncEnabled = catalogSyncEnabled;
    }

    /**
     * 执行交易所 instrument catalog 同步。
     * <p>
     * Why:
     * GateJ-FREEZE 是稳定运行验收，不允许因为 Binance 地域限制、公网阻断或 exchangeInfo 临时失败
     * 把控制台操作升级成 500。`nq.instrument.catalog-sync.enabled=false` 时直接返回受控 409；
     * 外部 Binance 失败也转换为受控业务冲突，由 API 层统一输出稳定错误结构。
     *
     * @param exchangeCode 目标交易所；为空时同步当前支持的全部交易所
     * @param traceId      当前请求 trace id，仅用于 adapter/cache 诊断，不包含敏感信息
     * @return 同步读取与 upsert 统计；禁用或外部失败时不写库
     * @throws IllegalStateException 当前环境禁用同步，或外部交易所 catalog 同步暂不可用
     */
    @Override
    public InstrumentCatalogSyncResult sync(String exchangeCode, String traceId) {
        if (!catalogSyncEnabled) {
            throw new IllegalStateException("当前环境禁用外部交易所同步");
        }
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
        Map<String, BinanceSymbolFilters> snapshot;
        try {
            snapshot = binanceExchangeAdapter.filtersCache().snapshot(traceId);
        } catch (BinanceApiException ex) {
            // Why: exchangeInfo 属于外部公开接口，ECS/freeze 环境可能因地域或网络策略收到 451。
            // 这里转换为受控业务错误，避免 ApiExceptionHandler 把它记录为 api_unhandled_exception。
            throw new IllegalStateException("外部交易所 instrument catalog 同步暂不可用", ex);
        }
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
