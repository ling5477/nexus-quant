package com.guidinglight.nexusquant.marketdata.infra.jdbc;

import com.guidinglight.nexusquant.marketdata.application.instrument.InstrumentCatalogUpsertStats;
import com.guidinglight.nexusquant.marketdata.domain.instrument.InstrumentCatalogItem;
import com.guidinglight.nexusquant.marketdata.domain.instrument.port.InstrumentCatalogRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.StringJoiner;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
                                   max_limit_quantity, max_market_size, max_market_size_unit,
                                   max_limit_notional_usd, max_market_notional_usd,
                                   source, source_schema_version, observed_at, synced_at,
                                   next_rule_effective_at, rule_checksum, created_at, updated_at
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
                               max_limit_quantity, max_market_size, max_market_size_unit,
                               max_limit_notional_usd, max_market_notional_usd,
                               source, source_schema_version, observed_at, synced_at,
                               next_rule_effective_at, rule_checksum, created_at, updated_at
                        FROM instrument_catalog
                        WHERE exchange_code = ?
                        ORDER BY internal_symbol
                        """,
                ITEM_ROW_MAPPER,
                normalizedExchangeCode
        );
    }

    @Override
    public List<InstrumentCatalogItem> findByExchangeAndSymbols(
            String exchangeCode,
            List<String> exchangeSymbols
    ) {
        String normalizedExchangeCode = requireNormalized(exchangeCode, "exchangeCode");
        List<String> normalizedSymbols = normalizeBoundedSymbols(exchangeSymbols);
        StringJoiner placeholders = new StringJoiner(", ");
        normalizedSymbols.forEach(ignored -> placeholders.add("?"));
        List<Object> arguments = new ArrayList<>();
        arguments.add(normalizedExchangeCode);
        arguments.addAll(normalizedSymbols);
        return jdbcTemplate.query(
                """
                        SELECT instrument_id, exchange_code, instrument_type, exchange_symbol, internal_symbol,
                               base_asset, quote_asset, status, tick_size, step_size, min_quantity,
                               max_limit_quantity, max_market_size, max_market_size_unit,
                               max_limit_notional_usd, max_market_notional_usd,
                               source, source_schema_version, observed_at, synced_at,
                               next_rule_effective_at, rule_checksum, created_at, updated_at
                        FROM instrument_catalog
                        WHERE exchange_code = ?
                          AND exchange_symbol IN (%s)
                        ORDER BY exchange_symbol
                        """.formatted(placeholders),
                ITEM_ROW_MAPPER,
                arguments.toArray()
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
            int affected = jdbcTemplate.update(
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
                              AND source_schema_version IS NULL
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
            updated += affected;
        }
        return new InstrumentCatalogUpsertStats(inserted, updated);
    }

    @Override
    @Transactional
    public InstrumentCatalogUpsertStats upsertVenueRuleFacts(
            List<InstrumentCatalogItem> items,
            Instant syncedAt
    ) {
        Objects.requireNonNull(items, "items must not be null");
        Objects.requireNonNull(syncedAt, "syncedAt must not be null");
        if (items.isEmpty() || items.size() > 3) {
            throw new IllegalArgumentException("venue-rule UPSERT requires 1..3 items");
        }
        for (InstrumentCatalogItem item : items) {
            if (!"OKX".equals(item.exchangeCode()) || !"SPOT".equals(item.instrumentType())) {
                throw new IllegalArgumentException("venue-rule UPSERT only accepts OKX SPOT items");
            }
            if (item.observedAt() == null || item.ruleChecksum() == null || item.sourceSchemaVersion() == null) {
                throw new IllegalArgumentException("venue-rule UPSERT requires observation, schema version and checksum");
            }
            if (item.observedAt().isAfter(syncedAt)) {
                throw new IllegalArgumentException("observedAt must not be after syncedAt");
            }
        }
        List<String> symbols = items.stream().map(InstrumentCatalogItem::exchangeSymbol).toList();
        int existingCount = findByExchangeAndSymbols("OKX", symbols).size();
        Timestamp syncedTimestamp = Timestamp.from(syncedAt);
        String sql = """
                INSERT INTO instrument_catalog (
                    exchange_code, instrument_type, exchange_symbol, internal_symbol,
                    base_asset, quote_asset, status, tick_size, step_size, min_quantity,
                    max_limit_quantity, max_market_size, max_market_size_unit,
                    max_limit_notional_usd, max_market_notional_usd,
                    source, source_schema_version, observed_at, synced_at,
                    next_rule_effective_at, rule_checksum, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (exchange_code, exchange_symbol) DO UPDATE
                SET instrument_type = EXCLUDED.instrument_type,
                    internal_symbol = EXCLUDED.internal_symbol,
                    base_asset = EXCLUDED.base_asset,
                    quote_asset = EXCLUDED.quote_asset,
                    status = EXCLUDED.status,
                    tick_size = EXCLUDED.tick_size,
                    step_size = EXCLUDED.step_size,
                    min_quantity = EXCLUDED.min_quantity,
                    max_limit_quantity = EXCLUDED.max_limit_quantity,
                    max_market_size = EXCLUDED.max_market_size,
                    max_market_size_unit = EXCLUDED.max_market_size_unit,
                    max_limit_notional_usd = EXCLUDED.max_limit_notional_usd,
                    max_market_notional_usd = EXCLUDED.max_market_notional_usd,
                    source = EXCLUDED.source,
                    source_schema_version = EXCLUDED.source_schema_version,
                    observed_at = EXCLUDED.observed_at,
                    synced_at = EXCLUDED.synced_at,
                    next_rule_effective_at = EXCLUDED.next_rule_effective_at,
                    rule_checksum = EXCLUDED.rule_checksum,
                    updated_at = EXCLUDED.updated_at
                """;
        jdbcTemplate.batchUpdate(sql, items, items.size(), (statement, item) -> {
            int index = 1;
            statement.setString(index++, item.exchangeCode());
            statement.setString(index++, item.instrumentType());
            statement.setString(index++, item.exchangeSymbol());
            statement.setString(index++, item.internalSymbol());
            statement.setString(index++, item.baseAsset());
            statement.setString(index++, item.quoteAsset());
            statement.setString(index++, item.status());
            statement.setBigDecimal(index++, item.tickSize());
            statement.setBigDecimal(index++, item.stepSize());
            statement.setBigDecimal(index++, item.minQuantity());
            statement.setBigDecimal(index++, item.maxLimitQuantity());
            statement.setBigDecimal(index++, item.maxMarketSize());
            statement.setString(index++, item.maxMarketSizeUnit());
            statement.setBigDecimal(index++, item.maxLimitNotionalUsd());
            statement.setBigDecimal(index++, item.maxMarketNotionalUsd());
            statement.setString(index++, item.source());
            statement.setString(index++, item.sourceSchemaVersion());
            statement.setTimestamp(index++, Timestamp.from(item.observedAt()));
            statement.setTimestamp(index++, syncedTimestamp);
            statement.setTimestamp(index++, toTimestamp(item.nextRuleEffectiveAt()));
            statement.setString(index++, item.ruleChecksum());
            statement.setTimestamp(index++, syncedTimestamp);
            statement.setTimestamp(index, syncedTimestamp);
        });
        return new InstrumentCatalogUpsertStats(items.size() - existingCount, existingCount);
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
                resultSet.getBigDecimal("max_limit_quantity"),
                resultSet.getBigDecimal("max_market_size"),
                resultSet.getString("max_market_size_unit"),
                resultSet.getBigDecimal("max_limit_notional_usd"),
                resultSet.getBigDecimal("max_market_notional_usd"),
                resultSet.getString("source"),
                resultSet.getString("source_schema_version"),
                toInstant(resultSet.getTimestamp("observed_at")),
                toInstant(resultSet.getTimestamp("synced_at")),
                toInstant(resultSet.getTimestamp("next_rule_effective_at")),
                resultSet.getString("rule_checksum"),
                toInstant(resultSet.getTimestamp("created_at")),
                toInstant(resultSet.getTimestamp("updated_at"))
        );
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static List<String> normalizeBoundedSymbols(List<String> symbols) {
        Objects.requireNonNull(symbols, "exchangeSymbols must not be null");
        if (symbols.isEmpty() || symbols.size() > 3) {
            throw new IllegalArgumentException("exchangeSymbols must contain 1..3 values");
        }
        List<String> normalized = symbols.stream()
                .map(symbol -> requireNormalized(symbol, "exchangeSymbol"))
                .distinct()
                .toList();
        if (normalized.size() != symbols.size()) {
            throw new IllegalArgumentException("exchangeSymbols must not contain duplicates");
        }
        return normalized;
    }

    private static String requireNormalized(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
