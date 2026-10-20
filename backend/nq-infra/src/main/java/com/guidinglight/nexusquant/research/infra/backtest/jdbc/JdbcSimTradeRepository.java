package com.guidinglight.nexusquant.research.infra.backtest.jdbc;

import com.guidinglight.nexusquant.research.domain.backtest.SimTrade;
import com.guidinglight.nexusquant.research.domain.backtest.port.SimTradeRepository;
import com.guidinglight.nexusquant.research.domain.eval.port.SimTradeQueryRepository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

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
                            fee_amount, slippage_amount, traded_at, created_at, updated_at,
                            fee_currency, fee_native_amount, fee_payment_status, fee_valuation_scale,
                            fee_conversion_price, fee_price_observed_at, fee_price_visible_at,
                            fee_price_source_sha256, fee_price_source_json, execution_sequence
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                Timestamp.from(simTrade.updatedAt()),
                simTrade.feeCurrency(),
                simTrade.feeNativeAmount(),
                simTrade.feePaymentStatus(),
                simTrade.feeValuationScale(),
                simTrade.feeConversionPrice(),
                timestamp(simTrade.feePriceObservedAt()),
                timestamp(simTrade.feePriceVisibleAt()),
                simTrade.feePriceSourceSha256(),
                simTrade.feePriceSourceJson(),
                simTrade.executionSequence());
    }

    @Override
    public List<SimTrade> listByBacktestRunId(String backtestRunId) {
        return jdbcTemplate.query(
                """
                        SELECT sim_trade_id, sim_order_id, backtest_run_id, symbol, side, quantity, trade_price,
                               fee_amount, slippage_amount, traded_at, created_at, updated_at,
                               fee_currency, fee_native_amount, fee_payment_status, fee_valuation_scale,
                               fee_conversion_price, fee_price_observed_at, fee_price_visible_at,
                               fee_price_source_sha256, fee_price_source_json, execution_sequence
                        FROM sim_trades
                        WHERE backtest_run_id = ?
                        ORDER BY traded_at ASC, execution_sequence ASC NULLS LAST
                        """,
                ROW_MAPPER,
                backtestRunId);
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
                resultSet.getTimestamp("updated_at").toInstant(),
                resultSet.getString("fee_currency"),
                resultSet.getBigDecimal("fee_native_amount"),
                resultSet.getString("fee_payment_status"),
                resultSet.getObject("fee_valuation_scale", Integer.class),
                resultSet.getBigDecimal("fee_conversion_price"),
                instant(resultSet.getTimestamp("fee_price_observed_at")),
                instant(resultSet.getTimestamp("fee_price_visible_at")),
                resultSet.getString("fee_price_source_sha256"),
                resultSet.getString("fee_price_source_json"),
                resultSet.getObject("execution_sequence", Long.class));
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
