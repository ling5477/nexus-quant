package com.guidinglight.nexusquant.research.infra.paper.jdbc;

import com.guidinglight.nexusquant.research.domain.paper.PositionCurveSnapshot;
import com.guidinglight.nexusquant.research.domain.paper.port.PositionCurveSnapshotRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPositionCurveSnapshotRepository implements PositionCurveSnapshotRepository {

    private static final RowMapper<PositionCurveSnapshot> ROW_MAPPER = JdbcPositionCurveSnapshotRepository::mapRow;
    private final JdbcTemplate jdbcTemplate;

    public JdbcPositionCurveSnapshotRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insert(PositionCurveSnapshot snapshot) {
        jdbcTemplate.update("""
                INSERT INTO position_curve_snapshots (
                    position_snapshot_id, paper_run_id, symbol, snapshot_time, quantity, avg_price,
                    mark_price, position_value, unrealized_pnl, realized_pnl, source, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                snapshot.positionSnapshotId(), snapshot.paperRunId(), snapshot.symbol(),
                Timestamp.from(snapshot.snapshotTime()), snapshot.quantity(), snapshot.avgPrice(),
                snapshot.markPrice(), snapshot.positionValue(), snapshot.unrealizedPnl(),
                snapshot.realizedPnl(), snapshot.source(), Timestamp.from(snapshot.createdAt()));
    }

    @Override
    public List<PositionCurveSnapshot> listByRunId(String paperRunId) {
        return jdbcTemplate.query("""
                SELECT position_snapshot_id, paper_run_id, symbol, snapshot_time, quantity, avg_price,
                       mark_price, position_value, unrealized_pnl, realized_pnl, source, created_at
                FROM position_curve_snapshots WHERE paper_run_id = ? ORDER BY snapshot_time DESC
                """, ROW_MAPPER, paperRunId);
    }

    private static PositionCurveSnapshot mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new PositionCurveSnapshot(
                rs.getString("position_snapshot_id"), rs.getString("paper_run_id"),
                rs.getString("symbol"), rs.getTimestamp("snapshot_time").toInstant(),
                rs.getBigDecimal("quantity"), rs.getBigDecimal("avg_price"),
                rs.getBigDecimal("mark_price"), rs.getBigDecimal("position_value"),
                rs.getBigDecimal("unrealized_pnl"), rs.getBigDecimal("realized_pnl"),
                rs.getString("source"), rs.getTimestamp("created_at").toInstant());
    }
}
