package com.guidinglight.nexusquant.research.infra.backtest.jdbc;

import com.guidinglight.nexusquant.research.domain.backtest.SimPnlSnapshot;
import com.guidinglight.nexusquant.research.domain.backtest.port.SimPnlSnapshotRepository;
import com.guidinglight.nexusquant.research.domain.eval.port.SimPnlSnapshotQueryRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JdbcSimPnlSnapshotRepository 是 sim_pnl_snapshots 表的 JDBC 实现。
 */
@Repository
public class JdbcSimPnlSnapshotRepository implements SimPnlSnapshotRepository, SimPnlSnapshotQueryRepository {

    private static final RowMapper<SimPnlSnapshot> ROW_MAPPER = JdbcSimPnlSnapshotRepository::mapRow;

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
                            realized_pnl, unrealized_pnl, total_fee, total_slippage, equity, net_pnl, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                Timestamp.from(simPnlSnapshot.createdAt())
        );
    }

    @Override
    public List<SimPnlSnapshot> listByBacktestRunId(String backtestRunId) {
        return jdbcTemplate.query(
                """
                        SELECT sim_pnl_snapshot_id, backtest_run_id, snapshot_time, cash_balance, position_market_value,
                               realized_pnl, unrealized_pnl, total_fee, total_slippage, equity, net_pnl, created_at
                        FROM sim_pnl_snapshots
                        WHERE backtest_run_id = ?
                        ORDER BY snapshot_time ASC, sim_pnl_snapshot_id ASC
                        """,
                ROW_MAPPER,
                backtestRunId
        );
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
                resultSet.getTimestamp("created_at").toInstant()
        );
    }
}


