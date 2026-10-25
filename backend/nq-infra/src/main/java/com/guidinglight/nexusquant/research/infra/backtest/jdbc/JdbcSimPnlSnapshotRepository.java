package com.guidinglight.nexusquant.research.infra.backtest.jdbc;

import com.guidinglight.nexusquant.research.domain.backtest.SimPnlSnapshot;
import com.guidinglight.nexusquant.research.domain.backtest.port.SimPnlSnapshotRepository;
import com.guidinglight.nexusquant.research.domain.eval.port.SimPnlSnapshotQueryRepository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * JdbcSimPnlSnapshotRepository 是 sim_pnl_snapshots 表的 JDBC 实现。
 */
@Repository
public class JdbcSimPnlSnapshotRepository
        implements SimPnlSnapshotRepository, SimPnlSnapshotQueryRepository {

    private static final RowMapper<SimPnlSnapshot> ROW_MAPPER =
            JdbcSimPnlSnapshotRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public JdbcSimPnlSnapshotRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insert(SimPnlSnapshot simPnlSnapshot) {
        jdbcTemplate.update(
                """
                        INSERT INTO sim_pnl_snapshots (
                            sim_pnl_snapshot_id, backtest_run_id, snapshot_time, cash_balance, position_market_value,
                            realized_pnl, unrealized_pnl, total_fee, total_slippage, equity, net_pnl, created_at,
                            discount_asset_currency, discount_asset_balance, discount_asset_quote_value, discount_conversion_price,
                            discount_price_observed_at, discount_price_visible_at, discount_price_source_sha256, discount_price_source_json, valuation_sequence
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                simPnlSnapshot.simPnlSnapshotId(),
                simPnlSnapshot.backtestRunId(),
                Timestamp.from(simPnlSnapshot.snapshotTime()),
                simPnlSnapshot.cashBalance(),
                simPnlSnapshot.positionMarketValue(),
                simPnlSnapshot.realizedPnl(),
                simPnlSnapshot.unrealizedPnl(),
                simPnlSnapshot.totalFee(),
                simPnlSnapshot.totalSlippage(),
                simPnlSnapshot.equity(),
                simPnlSnapshot.netPnl(),
                Timestamp.from(simPnlSnapshot.createdAt()),
                simPnlSnapshot.discountAssetCurrency(),
                simPnlSnapshot.discountAssetBalance(),
                simPnlSnapshot.discountAssetQuoteValue(),
                simPnlSnapshot.discountConversionPrice(),
                timestamp(simPnlSnapshot.discountPriceObservedAt()),
                timestamp(simPnlSnapshot.discountPriceVisibleAt()),
                simPnlSnapshot.discountPriceSourceSha256(),
                simPnlSnapshot.discountPriceSourceJson(),
                simPnlSnapshot.valuationSequence());
    }

    @Override
    public List<SimPnlSnapshot> listByBacktestRunId(String backtestRunId) {
        return jdbcTemplate.query(
                """
                        SELECT sim_pnl_snapshot_id, backtest_run_id, snapshot_time, cash_balance, position_market_value,
                               realized_pnl, unrealized_pnl, total_fee, total_slippage, equity, net_pnl, created_at,
                               discount_asset_currency, discount_asset_balance, discount_asset_quote_value, discount_conversion_price,
                               discount_price_observed_at, discount_price_visible_at, discount_price_source_sha256,
                               discount_price_source_json, valuation_sequence
                        FROM sim_pnl_snapshots
                        WHERE backtest_run_id = ?
                        ORDER BY snapshot_time ASC, valuation_sequence ASC NULLS LAST
                        """,
                ROW_MAPPER,
                backtestRunId);
    }

    private static SimPnlSnapshot mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new SimPnlSnapshot(
                resultSet.getString("sim_pnl_snapshot_id"),
                resultSet.getString("backtest_run_id"),
                resultSet.getTimestamp("snapshot_time").toInstant(),
                resultSet.getBigDecimal("cash_balance"),
                resultSet.getBigDecimal("position_market_value"),
                resultSet.getBigDecimal("realized_pnl"),
                resultSet.getBigDecimal("unrealized_pnl"),
                resultSet.getBigDecimal("total_fee"),
                resultSet.getBigDecimal("total_slippage"),
                resultSet.getBigDecimal("equity"),
                resultSet.getBigDecimal("net_pnl"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getString("discount_asset_currency"),
                resultSet.getBigDecimal("discount_asset_balance"),
                resultSet.getBigDecimal("discount_asset_quote_value"),
                resultSet.getBigDecimal("discount_conversion_price"),
                instant(resultSet.getTimestamp("discount_price_observed_at")),
                instant(resultSet.getTimestamp("discount_price_visible_at")),
                resultSet.getString("discount_price_source_sha256"),
                resultSet.getString("discount_price_source_json"),
                resultSet.getObject("valuation_sequence", Long.class));
    }

    private static Timestamp timestamp(Instant at) {
        return at == null ? null : Timestamp.from(at);
    }

    private static Instant instant(Timestamp at) {
        return at == null ? null : at.toInstant();
    }
}
