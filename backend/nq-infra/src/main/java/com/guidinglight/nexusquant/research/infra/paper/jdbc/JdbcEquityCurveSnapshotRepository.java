package com.guidinglight.nexusquant.research.infra.paper.jdbc;

import com.guidinglight.nexusquant.research.domain.paper.EquityCurveSnapshot;
import com.guidinglight.nexusquant.research.domain.paper.port.EquityCurveSnapshotRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcEquityCurveSnapshotRepository implements EquityCurveSnapshotRepository {

    private static final RowMapper<EquityCurveSnapshot> ROW_MAPPER = JdbcEquityCurveSnapshotRepository::mapRow;
    private final JdbcTemplate jdbcTemplate;

    public JdbcEquityCurveSnapshotRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insert(EquityCurveSnapshot snapshot) {
        jdbcTemplate.update("""
                INSERT INTO equity_curve_snapshots (
                    equity_snapshot_id, paper_run_id, snapshot_time, total_equity, cash_balance,
                    position_value, unrealized_pnl, realized_pnl, drawdown, source, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                snapshot.equitySnapshotId(), snapshot.paperRunId(),
                Timestamp.from(snapshot.snapshotTime()), snapshot.totalEquity(), snapshot.cashBalance(),
                snapshot.positionValue(), snapshot.unrealizedPnl(), snapshot.realizedPnl(),
                snapshot.drawdown(), snapshot.source(), Timestamp.from(snapshot.createdAt()));
    }

    @Override
    public List<EquityCurveSnapshot> listByRunId(String paperRunId) {
        return jdbcTemplate.query("""
                SELECT equity_snapshot_id, paper_run_id, snapshot_time, total_equity, cash_balance,
                       position_value, unrealized_pnl, realized_pnl, drawdown, source, created_at
                FROM equity_curve_snapshots WHERE paper_run_id = ? ORDER BY snapshot_time DESC
                """, ROW_MAPPER, paperRunId);
    }

    private static EquityCurveSnapshot mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new EquityCurveSnapshot(
                rs.getString("equity_snapshot_id"), rs.getString("paper_run_id"),
                rs.getTimestamp("snapshot_time").toInstant(), rs.getBigDecimal("total_equity"),
                rs.getBigDecimal("cash_balance"), rs.getBigDecimal("position_value"),
                rs.getBigDecimal("unrealized_pnl"), rs.getBigDecimal("realized_pnl"),
                rs.getBigDecimal("drawdown"), rs.getString("source"),
                rs.getTimestamp("created_at").toInstant());
    }
}
