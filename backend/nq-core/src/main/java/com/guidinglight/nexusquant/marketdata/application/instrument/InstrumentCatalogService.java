package com.guidinglight.nexusquant.marketdata.application.instrument;

import com.guidinglight.nexusquant.marketdata.domain.instrument.InstrumentCatalogItem;
import com.guidinglight.nexusquant.marketdata.domain.instrument.port.InstrumentCatalogRepository;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

/**
 * InstrumentCatalogService 提供 PRE-2 的 instrument catalog 读写编排。
 * <p>
 * Why:
 * instrument/symbol 主数据是后续 selector、trading precision 校验和 marketdata owner 的公共事实源，
 * 需要有一个正式应用服务把查询与同步写入收口起来，而不是继续散落在 adapter cache 或 controller 中。
 */
@Service
public class InstrumentCatalogService {

    private final InstrumentCatalogRepository instrumentCatalogRepository;

    public InstrumentCatalogService(InstrumentCatalogRepository instrumentCatalogRepository) {
        this.instrumentCatalogRepository = Objects.requireNonNull(
                instrumentCatalogRepository,
                "instrumentCatalogRepository must not be null"
        );
    }

    /**
     * 列出 instrument catalog。
     */
    public List<InstrumentCatalogItem> list(String exchangeCode) {
        return instrumentCatalogRepository.list(exchangeCode);
    }

    /**
     * 精确读取 1..3 个 exchange symbols，供 bounded venue-rule sync 比较旧 checksum。
     */
    public List<InstrumentCatalogItem> findByExchangeAndSymbols(
            String exchangeCode,
            List<String> exchangeSymbols
    ) {
        Objects.requireNonNull(exchangeSymbols, "exchangeSymbols must not be null");
        return instrumentCatalogRepository.findByExchangeAndSymbols(exchangeCode, exchangeSymbols);
    }

    /**
     * 批量写入最新 instrument 快照。
     */
    public InstrumentCatalogUpsertStats upsertCatalogItems(List<InstrumentCatalogItem> items, Instant syncedAt) {
        Objects.requireNonNull(items, "items must not be null");
        Objects.requireNonNull(syncedAt, "syncedAt must not be null");
        if (items.isEmpty()) {
            return new InstrumentCatalogUpsertStats(0, 0);
        }
        return instrumentCatalogRepository.upsertAll(items, syncedAt);
    }

    /**
     * 持久化 1..3 条已完成 parser/checksum 校验的 OKX Spot venue-rule facts。
     */
    public InstrumentCatalogUpsertStats upsertVenueRuleFacts(
            List<InstrumentCatalogItem> items,
            Instant syncedAt
    ) {
        Objects.requireNonNull(items, "items must not be null");
        Objects.requireNonNull(syncedAt, "syncedAt must not be null");
        return instrumentCatalogRepository.upsertVenueRuleFacts(items, syncedAt);
    }
}
