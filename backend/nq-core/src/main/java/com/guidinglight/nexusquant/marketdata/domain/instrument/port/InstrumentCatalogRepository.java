package com.guidinglight.nexusquant.marketdata.domain.instrument.port;

import com.guidinglight.nexusquant.marketdata.application.instrument.InstrumentCatalogUpsertStats;
import com.guidinglight.nexusquant.marketdata.domain.instrument.InstrumentCatalogItem;

import java.time.Instant;
import java.util.List;

/**
 * InstrumentCatalogRepository 抽象 instrument/symbol 主数据的正式持久化能力。
 */
public interface InstrumentCatalogRepository extends InstrumentCatalogReadPort {

    /**
     * 按交易所列出可见 instrument；exchangeCode 为空时返回全部。
     */
    List<InstrumentCatalogItem> list(String exchangeCode);

    /**
     * 批量写入最新 instrument 快照。
     */
    InstrumentCatalogUpsertStats upsertAll(List<InstrumentCatalogItem> items, Instant syncedAt);

    /**
     * 以 PostgreSQL UPSERT 持久化 1..3 条 GateW venue-rule current facts。
     *
     * <p>相同 checksum 仅刷新 observation/write timestamps；不同 checksum 覆盖当前 facts。
     * 该接口不保存 history，不接受全量交易所扫描。</p>
     */
    InstrumentCatalogUpsertStats upsertVenueRuleFacts(List<InstrumentCatalogItem> items, Instant syncedAt);
}
