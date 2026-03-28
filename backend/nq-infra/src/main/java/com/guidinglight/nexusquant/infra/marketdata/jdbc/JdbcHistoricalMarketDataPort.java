package com.guidinglight.nexusquant.infra.marketdata.jdbc;

import com.guidinglight.nexusquant.backtest.model.BarInterval;
import com.guidinglight.nexusquant.backtest.model.HistoricalBar;
import com.guidinglight.nexusquant.backtest.model.HistoricalMarketDataQuery;
import com.guidinglight.nexusquant.backtest.port.HistoricalMarketDataPort;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * JdbcHistoricalMarketDataPort 提供 DB-backed historical bars 查询。
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
                        SELECT symbol,
                               interval,
                               open_time,
                               close_time,
                               open_price,
                               high_price,
                               low_price,
                               close_price,
                               volume
                        FROM marketdata_bars
                        WHERE symbol = ?
                          AND interval = ?
                          AND open_time >= ?
                          AND close_time <= ?
                        ORDER BY open_time
                        """,
                (resultSet, rowNum) -> new HistoricalBar(
                        resultSet.getString("symbol"),
                        BarInterval.fromWireValue(resultSet.getString("interval")),
                        resultSet.getTimestamp("open_time").toInstant(),
                        resultSet.getTimestamp("close_time").toInstant(),
                        resultSet.getBigDecimal("open_price"),
                        resultSet.getBigDecimal("high_price"),
                        resultSet.getBigDecimal("low_price"),
                        resultSet.getBigDecimal("close_price"),
                        resultSet.getBigDecimal("volume")
                ),
                query.symbol(),
                query.interval().wireValue(),
                query.startTime(),
                query.endTime()
        );
    }
}
