package com.guidinglight.nexusquant.marketdata.infra.jdbc;

import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalMarketDataQuery;
import com.guidinglight.nexusquant.marketdata.domain.port.HistoricalMarketDataPort;

import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * JdbcHistoricalMarketDataPort 提供 DB-backed historical bars 查询。
 * <p>
 * Why:
 * RC1-5 要把 `exchangeCode` 纳入 marketdata canonical 查询口径，因此 JDBC 查询必须同步收口
 * 到 `exchange_code + symbol + interval + range`，不能再依赖隐式默认交易所。
 */
public class JdbcHistoricalMarketDataPort implements HistoricalMarketDataPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcHistoricalMarketDataPort(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<HistoricalBar> loadBars(HistoricalMarketDataQuery query) {
        return jdbcTemplate.query(
                """
                        SELECT exchange_code,
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
                               quality_status,
                               raw_payload_json
                        FROM marketdata_bars
                        WHERE exchange_code = ?
                          AND market_type = ?
                          AND symbol = ?
                          AND "interval" = ?
                          AND open_time >= ?
                          AND close_time <= ?
                        ORDER BY open_time
                        LIMIT ?
                        OFFSET ?
                        """,
                (resultSet, rowNum) -> new HistoricalBar(
                        resultSet.getString("exchange_code"),
                        resultSet.getString("market_type"),
                        resultSet.getString("symbol"),
                        BarInterval.fromWireValue(resultSet.getString("interval")),
                        resultSet.getTimestamp("open_time").toInstant(),
                        resultSet.getTimestamp("close_time").toInstant(),
                        resultSet.getBigDecimal("open_price"),
                        resultSet.getBigDecimal("high_price"),
                        resultSet.getBigDecimal("low_price"),
                        resultSet.getBigDecimal("close_price"),
                        resultSet.getBigDecimal("volume"),
                        resultSet.getBigDecimal("quote_volume"),
                        resultSet.getObject("trade_count", Long.class),
                        resultSet.getString("quality_status"),
                        resultSet.getString("raw_payload_json")
                ),
                query.exchangeCode(),
                query.marketType(),
                query.symbol(),
                query.interval().wireValue(),
                Timestamp.from(query.startTime()),
                Timestamp.from(query.endTime()),
                Math.max(1, Math.min(query.size(), 500)),
                Math.max(0, query.page()) * Math.max(1, Math.min(query.size(), 500))
        );
    }
}
