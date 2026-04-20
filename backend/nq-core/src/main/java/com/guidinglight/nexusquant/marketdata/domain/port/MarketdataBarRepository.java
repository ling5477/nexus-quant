package com.guidinglight.nexusquant.marketdata.domain.port;

import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataBarUpsertStats;

import java.time.Instant;
import java.util.List;

/**
 * MarketdataBarRepository 定义 RC1-5 marketdata_bars 写侧端口。
 * <p>
 * Why:
 * `HistoricalMarketDataPort` 只负责读，fixture ingest 需要一个显式写口来承接幂等 upsert，
 * 否则 controller 或 application service 会被迫直接写 SQL，破坏 marketdata 域边界。
 */
public interface MarketdataBarRepository {

    /**
     * 幂等写入一批历史 K 线。
     *
     * @param bars 待写入的 bars，必须已经带上 canonical exchangeCode / symbol / interval
     * @param source 数据来源标识，例如 `FIXTURE_SYNC`
     * @param ingestedAt 本次导入操作时间戳，用于回写 `ingested_at`
     * @return 本次写入的插入/更新统计
     */
    MarketdataBarUpsertStats upsertBars(List<HistoricalBar> bars, String source, Instant ingestedAt);
}
