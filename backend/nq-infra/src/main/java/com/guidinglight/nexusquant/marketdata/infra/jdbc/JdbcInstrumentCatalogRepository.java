package com.guidinglight.nexusquant.marketdata.infra.jdbc;

import com.guidinglight.nexusquant.marketdata.application.instrument.InstrumentCatalogUpsertStats;
import com.guidinglight.nexusquant.marketdata.domain.instrument.InstrumentCatalogItem;
import com.guidinglight.nexusquant.marketdata.domain.instrument.port.InstrumentCatalogRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JdbcInstrumentCatalogRepository 提供 instrument_catalog 表的 JDBC 实现。
 * <p>
 * Why:
 * PRE-2 要把 instrument/symbol 主数据从 adapter cache 提升为正式持久化事实，
 * 这类 ownership 应明确落在 `nq-infra`，而不是继续停留在内存缓存里。
 */
@Repository
public class JdbcInstrumentCatalogRepository implements InstrumentCatalogRepository {

    private static final RowMapper<InstrumentCatalogItem> ITEM_ROW_MAPPER = JdbcInstrumentCatalogRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public JdbcInstrumentCatalogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<InstrumentCatalogItem> list(String exchangeCode) {
        String normalizedExchangeCode = normalizeOptional(exchangeCode);
        if (normalizedExchangeCode == null) {
            return jdbcTemplate.query(
                    """
                            SELECT instrument_id, exchange_code, instrument_type, exchange_symbol, internal_symbol,
                                   base_asset, quote_asset, status, tick_size, step_size, min_quantity,
                                   source, synced_at, created_at, updated_at
                            FROM instrument_catalog
                            ORDER BY exchange_code, internal_symbol
                            """,
                    ITEM_ROW_MAPPER
            );
        }
        return jdbcTemplate.query(
                """
                        SELECT instrument_id, exchange_code, instrument_type, exchange_symbol, internal_symbol,
                               base_asset, quote_asset, status, tick_size, step_size, min_quantity,
                               source, synced_at, created_at, updated_at
                        FROM instrument_catalog
                        WHERE exchange_code = ?
                        ORDER BY internal_symbol
                        """,
                ITEM_ROW_MAPPER,
                normalizedExchangeCode
        );
    }

    @Override
    public InstrumentCatalogUpsertStats upsertAll(List<InstrumentCatalogItem> items, Instant syncedAt) {
        int inserted = 0;
        int updated = 0;
        Timestamp syncedTimestamp = Timestamp.from(syncedAt);
        for (InstrumentCatalogItem item : items) {
            Long instrumentId = findId(item.exchangeCode(), item.exchangeSymbol());
            if (instrumentId == null) {
                jdbcTemplate.update(
                        """
                                INSERT INTO instrument_catalog (
                                    exchange_code, instrument_type, exchange_symbol, internal_symbol,
                                    base_asset, quote_asset, status, tick_size, step_size, min_quantity,
                                    source, synced_at, created_at, updated_at
                                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                                """,
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
                        syncedTimestamp,
                        syncedTimestamp,
                        syncedTimestamp
                );
                inserted++;
                continue;
            }
            jdbcTemplate.update(
                    """
                            UPDATE instrument_catalog
                            SET instrument_type = ?,
                                internal_symbol = ?,
                                base_asset = ?,
                                quote_asset = ?,
                                status = ?,
                                tick_size = ?,
                                step_size = ?,
                                min_quantity = ?,
                                source = ?,
                                synced_at = ?,
                                updated_at = ?
                            WHERE instrument_id = ?
                            """,
                    item.instrumentType(),
                    item.internalSymbol(),
                    item.baseAsset(),
                    item.quoteAsset(),
                    item.status(),
                    item.tickSize(),
                    item.stepSize(),
                    item.minQuantity(),
                    item.source(),
                    syncedTimestamp,
                    syncedTimestamp,
                    instrumentId
            );
            updated++;
        }
        return new InstrumentCatalogUpsertStats(inserted, updated);
    }

    private Long findId(String exchangeCode, String exchangeSymbol) {
        return jdbcTemplate.query(
                "SELECT instrument_id FROM instrument_catalog WHERE exchange_code = ? AND exchange_symbol = ?",
                (resultSet, rowNum) -> resultSet.getLong("instrument_id"),
                exchangeCode,
                exchangeSymbol
        ).stream().findFirst().orElse(null);
    }

    private static InstrumentCatalogItem mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new InstrumentCatalogItem(
                resultSet.getLong("instrument_id"),
                resultSet.getString("exchange_code"),
                resultSet.getString("instrument_type"),
                resultSet.getString("exchange_symbol"),
                resultSet.getString("internal_symbol"),
                resultSet.getString("base_asset"),
                resultSet.getString("quote_asset"),
                resultSet.getString("status"),
                resultSet.getBigDecimal("tick_size"),
                resultSet.getBigDecimal("step_size"),
                resultSet.getBigDecimal("min_quantity"),
                resultSet.getString("source"),
                toInstant(resultSet.getTimestamp("synced_at")),
                toInstant(resultSet.getTimestamp("created_at")),
                toInstant(resultSet.getTimestamp("updated_at"))
        );
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
