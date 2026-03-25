package com.guidinglight.nexusquant.infra.backtest;

import com.guidinglight.nexusquant.backtest.model.SimTrade;
import com.guidinglight.nexusquant.backtest.port.SimTradeRepository;
import com.guidinglight.nexusquant.eval.port.SimTradeQueryRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JdbcSimTradeRepository 是 sim_trades 表的 JDBC 实现。
 */
@Repository
public class JdbcSimTradeRepository implements SimTradeRepository, SimTradeQueryRepository {

    private static final RowMapper<SimTrade> ROW_MAPPER = JdbcSimTradeRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public JdbcSimTradeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insert(SimTrade simTrade) {
        jdbcTemplate.update(
                """
                        INSERT INTO sim_trades (
                            sim_trade_id, sim_order_id, backtest_run_id, symbol, side, quantity, trade_price,
                            fee_amount, slippage_amount, traded_at, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                simTrade.simTradeId(),
                simTrade.simOrderId(),
                simTrade.backtestRunId(),
                simTrade.symbol(),
                simTrade.side(),
                simTrade.quantity(),
                simTrade.tradePrice(),
                simTrade.feeAmount(),
                simTrade.slippageAmount(),
                Timestamp.from(simTrade.tradedAt()),
                Timestamp.from(simTrade.createdAt()),
                Timestamp.from(simTrade.updatedAt())
        );
    }

    @Override
    public List<SimTrade> listByBacktestRunId(String backtestRunId) {
        return jdbcTemplate.query(
                """
                        SELECT sim_trade_id, sim_order_id, backtest_run_id, symbol, side, quantity, trade_price,
                               fee_amount, slippage_amount, traded_at, created_at, updated_at
                        FROM sim_trades
                        WHERE backtest_run_id = ?
                        ORDER BY traded_at ASC, sim_trade_id ASC
                        """,
                ROW_MAPPER,
                backtestRunId
        );
    }

    private static SimTrade mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new SimTrade(
                resultSet.getString("sim_trade_id"),
                resultSet.getString("sim_order_id"),
                resultSet.getString("backtest_run_id"),
                resultSet.getString("symbol"),
                resultSet.getString("side"),
                resultSet.getBigDecimal("quantity"),
                resultSet.getBigDecimal("trade_price"),
                resultSet.getBigDecimal("fee_amount"),
                resultSet.getBigDecimal("slippage_amount"),
                resultSet.getTimestamp("traded_at").toInstant(),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant()
        );
    }
}
