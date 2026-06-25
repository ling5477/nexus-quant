package com.guidinglight.nexusquant.research.infra.paper.jdbc;

import com.guidinglight.nexusquant.research.domain.paper.EquityCurveSnapshot;
import com.guidinglight.nexusquant.research.domain.paper.port.EquityCurveSnapshotRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcEquityCurveSnapshotRepository implements EquityCurveSnapshotRepository {

    private static final RowMapper<EquityCurveSnapshot> ROW_MAPPER = JdbcEquityCurveSnapshotRepository::mapRow;
    private static final String SELECT_COLUMNS = """
            SELECT equity_snapshot_id, paper_run_id, snapshot_time, total_equity, cash_balance,
                   position_value, unrealized_pnl, realized_pnl, drawdown, source, created_at
            FROM equity_curve_snapshots
            """;

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    public JdbcEquityCurveSnapshotRepository(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, new NamedParameterJdbcTemplate(jdbcTemplate));
    }

    JdbcEquityCurveSnapshotRepository(JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
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
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE paper_run_id = ? ORDER BY snapshot_time DESC",
                ROW_MAPPER, paperRunId);
    }

    @Override
    public Map<String, List<EquityCurveSnapshot>> listByRunIds(Collection<String> runIds) {
        if (runIds == null || runIds.isEmpty()) {
            return Map.of();
        }
        // 去重后以命名参数绑定 IN 列表（参数化，杜绝 SQL 拼接注入）；按 snapshot_time DESC 与单 run 口径一致。
        MapSqlParameterSource params = new MapSqlParameterSource(
                "runIds", new ArrayList<>(new LinkedHashSet<>(runIds)));
        List<EquityCurveSnapshot> rows = namedParameterJdbcTemplate.query(
                SELECT_COLUMNS + " WHERE paper_run_id IN (:runIds) ORDER BY snapshot_time DESC",
                params, ROW_MAPPER);
        // groupingBy 保留全局有序结果在各分组内的相对顺序，单 run 列表口径仍为 snapshot_time DESC。
        return rows.stream().collect(Collectors.groupingBy(
                EquityCurveSnapshot::paperRunId, LinkedHashMap::new, Collectors.toList()));
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
