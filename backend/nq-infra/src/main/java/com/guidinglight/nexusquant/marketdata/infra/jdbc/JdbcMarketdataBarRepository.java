package com.guidinglight.nexusquant.marketdata.infra.jdbc;

import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataBarUpsertStats;
import com.guidinglight.nexusquant.marketdata.domain.port.MarketdataBarRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JdbcMarketdataBarRepository 提供 `marketdata_bars` 的幂等 upsert 实现。
 * <p>
 * Why:
 * RC1-5 首版需要明确固定 `(exchange_code, symbol, interval, open_time)` 的唯一键语义，
 * 因此写侧直接用 Postgres `ON CONFLICT DO UPDATE` 收口插入/更新分支，并返回统计结果。
 */
@Repository
public class JdbcMarketdataBarRepository implements MarketdataBarRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcMarketdataBarRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    public MarketdataBarUpsertStats upsertBars(List<HistoricalBar> bars, String source, Instant ingestedAt) {
        Objects.requireNonNull(bars, "bars must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(ingestedAt, "ingestedAt must not be null");
        int insertedCount = 0;
        int updatedCount = 0;
        for (HistoricalBar bar : bars) {
            Boolean inserted = jdbcTemplate.queryForObject(
                    """
                            INSERT INTO marketdata_bars (
                                exchange_code,
                                market_type,
                                symbol,
                                "interval",
                                open_time,
                                close_time,
                                open_price,
                                high_price,
                                low_price,
                                close_price,
                                volume,
                                quote_volume,
                                trade_count,
                                source,
                                quality_status,
                                raw_payload_json,
                                ingested_at
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                            ON CONFLICT (exchange_code, market_type, symbol, "interval", open_time) DO UPDATE
                            SET close_time = EXCLUDED.close_time,
                                open_price = EXCLUDED.open_price,
                                high_price = EXCLUDED.high_price,
                                low_price = EXCLUDED.low_price,
                                close_price = EXCLUDED.close_price,
                                volume = EXCLUDED.volume,
                                quote_volume = EXCLUDED.quote_volume,
                                trade_count = EXCLUDED.trade_count,
                                source = EXCLUDED.source,
                                quality_status = EXCLUDED.quality_status,
                                raw_payload_json = EXCLUDED.raw_payload_json,
                                ingested_at = EXCLUDED.ingested_at
                            RETURNING xmax = 0
                            """,
                    Boolean.class,
                    bar.exchangeCode(),
                    bar.marketType(),
                    bar.symbol(),
                    bar.interval().wireValue(),
                    Timestamp.from(bar.openTime()),
                    Timestamp.from(bar.closeTime()),
                    bar.openPrice(),
                    bar.highPrice(),
                    bar.lowPrice(),
                    bar.closePrice(),
                    bar.volume(),
                    bar.quoteVolume(),
                    bar.tradeCount(),
                    source,
                    bar.qualityStatus(),
                    bar.rawPayloadJson() == null || bar.rawPayloadJson().isBlank() ? "{}" : bar.rawPayloadJson(),
                    Timestamp.from(ingestedAt)
            );
            if (Boolean.TRUE.equals(inserted)) {
                insertedCount++;
            } else {
                updatedCount++;
            }
        }
        return new MarketdataBarUpsertStats(insertedCount, updatedCount);
    }
}
