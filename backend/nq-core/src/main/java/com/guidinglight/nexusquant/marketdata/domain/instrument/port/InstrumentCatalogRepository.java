package com.guidinglight.nexusquant.marketdata.domain.instrument.port;

import com.guidinglight.nexusquant.marketdata.application.instrument.InstrumentCatalogUpsertStats;
import com.guidinglight.nexusquant.marketdata.domain.instrument.InstrumentCatalogItem;

import java.time.Instant;
import java.util.List;

/**
 * InstrumentCatalogRepository 抽象 instrument/symbol 主数据的正式持久化能力。
 */
public interface InstrumentCatalogRepository {

    /**
     * 按交易所列出可见 instrument；exchangeCode 为空时返回全部。
     */
    List<InstrumentCatalogItem> list(String exchangeCode);

    /**
     * 批量写入最新 instrument 快照。
     */
    InstrumentCatalogUpsertStats upsertAll(List<InstrumentCatalogItem> items, Instant syncedAt);
}
