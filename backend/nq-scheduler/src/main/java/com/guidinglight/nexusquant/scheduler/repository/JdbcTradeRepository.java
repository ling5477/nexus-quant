package com.guidinglight.nexusquant.scheduler.repository;

import com.guidinglight.nexusquant.scheduler.model.PaperTradeRecord;
import com.guidinglight.nexusquant.scheduler.service.port.TradeRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JdbcTradeRepository 是 trades 表的 JDBC 访问实现。
 */
@Repository
public class JdbcTradeRepository implements TradeRepository {

    private static final RowMapper<PaperTradeRecord> TRADE_ROW_MAPPER = JdbcTradeRepository::mapTrade;

    private final JdbcTemplate jdbcTemplate;

    public JdbcTradeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<PaperTradeRecord> findByOrderId(String orderId) {
        List<PaperTradeRecord> results = jdbcTemplate.query(
                """
                        SELECT trade_id, order_id, account_id, symbol, exchange, external_order_id, exchange_trade_id, price, qty, fee,
                               fee_currency, trace_id, ts
                        FROM trades
                        WHERE order_id = ?
                        ORDER BY ts DESC
                        LIMIT 1
                        """,
                TRADE_ROW_MAPPER,
                orderId
        );
        if (results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(results.getFirst());
    }

    @Override
    public Optional<PaperTradeRecord> findByExchangeAndExchangeTradeId(String exchange, String exchangeTradeId) {
        List<PaperTradeRecord> results = jdbcTemplate.query(
                """
                        SELECT trade_id, order_id, account_id, symbol, exchange, external_order_id, exchange_trade_id, price, qty, fee,
                               fee_currency, trace_id, ts
                        FROM trades
                        WHERE exchange = ? AND exchange_trade_id = ?
                        ORDER BY ts DESC
                        LIMIT 1
                        """,
                TRADE_ROW_MAPPER,
                exchange,
                exchangeTradeId
        );
        if (results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(results.getFirst());
    }

    @Override
    public void insert(PaperTradeRecord trade) {
        jdbcTemplate.update(
                """
                        INSERT INTO trades (
                            trade_id, order_id, account_id, symbol, exchange, external_order_id, exchange_trade_id,
                            price, qty, fee, fee_currency, trace_id, ts
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                trade.tradeId(),
                trade.orderId(),
                trade.accountId(),
                trade.symbol(),
                trade.exchange(),
                trade.externalOrderId(),
                trade.exchangeTradeId(),
                trade.price(),
                trade.qty(),
                trade.fee(),
                trade.feeCurrency(),
                trade.traceId(),
                Timestamp.from(trade.ts())
        );
    }

    private static PaperTradeRecord mapTrade(ResultSet resultSet, int rowNum) throws SQLException {
        return new PaperTradeRecord(
                resultSet.getString("trade_id"),
                resultSet.getString("order_id"),
                resultSet.getLong("account_id"),
                resultSet.getString("symbol"),
                resultSet.getString("exchange"),
                resultSet.getString("external_order_id"),
                resultSet.getString("exchange_trade_id"),
                resultSet.getBigDecimal("price"),
                resultSet.getBigDecimal("qty"),
                resultSet.getBigDecimal("fee"),
                resultSet.getString("fee_currency"),
                resultSet.getString("trace_id"),
                resultSet.getTimestamp("ts").toInstant()
        );
    }
}
