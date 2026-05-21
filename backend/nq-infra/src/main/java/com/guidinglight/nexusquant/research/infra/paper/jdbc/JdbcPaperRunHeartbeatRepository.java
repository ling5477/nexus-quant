package com.guidinglight.nexusquant.research.infra.paper.jdbc;

import com.guidinglight.nexusquant.research.domain.paper.PaperRunHeartbeat;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunHeartbeatStatus;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunHeartbeatRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPaperRunHeartbeatRepository implements PaperRunHeartbeatRepository {

    private static final RowMapper<PaperRunHeartbeat> ROW_MAPPER = JdbcPaperRunHeartbeatRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public JdbcPaperRunHeartbeatRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insert(PaperRunHeartbeat heartbeat) {
        jdbcTemplate.update("""
                INSERT INTO paper_run_heartbeats (
                    heartbeat_id, paper_run_id, heartbeat_time, status,
                    last_event_time, last_order_time, last_trade_time,
                    lag_seconds, summary_json, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), ?)
                """,
                heartbeat.heartbeatId(), heartbeat.paperRunId(),
                Timestamp.from(heartbeat.heartbeatTime()), heartbeat.status().name(),
                toTimestamp(heartbeat.lastEventTime()), toTimestamp(heartbeat.lastOrderTime()),
                toTimestamp(heartbeat.lastTradeTime()), heartbeat.lagSeconds(),
                heartbeat.summaryJson(), Timestamp.from(heartbeat.createdAt()));
    }

    @Override
    public List<PaperRunHeartbeat> listByRunId(String paperRunId) {
        return jdbcTemplate.query("""
                SELECT heartbeat_id, paper_run_id, heartbeat_time, status,
                       last_event_time, last_order_time, last_trade_time,
                       lag_seconds, summary_json::text AS summary_json, created_at
                FROM paper_run_heartbeats WHERE paper_run_id = ? ORDER BY heartbeat_time DESC
                """, ROW_MAPPER, paperRunId);
    }

    private static PaperRunHeartbeat mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp lastEventTime = rs.getTimestamp("last_event_time");
        Timestamp lastOrderTime = rs.getTimestamp("last_order_time");
        Timestamp lastTradeTime = rs.getTimestamp("last_trade_time");
        long lagSeconds = rs.getLong("lag_seconds");
        return new PaperRunHeartbeat(
                rs.getString("heartbeat_id"),
                rs.getString("paper_run_id"),
                rs.getTimestamp("heartbeat_time").toInstant(),
                PaperRunHeartbeatStatus.valueOf(rs.getString("status")),
                lastEventTime == null ? null : lastEventTime.toInstant(),
                lastOrderTime == null ? null : lastOrderTime.toInstant(),
                lastTradeTime == null ? null : lastTradeTime.toInstant(),
                rs.wasNull() ? null : lagSeconds,
                rs.getString("summary_json"),
                rs.getTimestamp("created_at").toInstant());
    }

    private static Timestamp toTimestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
