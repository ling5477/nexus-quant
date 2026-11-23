package com.guidinglight.nexusquant.research.infra.paper.jdbc;

import com.guidinglight.nexusquant.research.domain.paper.TradeReplayRecord;
import com.guidinglight.nexusquant.research.domain.paper.port.TradeReplayRecordRepository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcTradeReplayRecordRepository implements TradeReplayRecordRepository {

    private static final RowMapper<TradeReplayRecord> ROW_MAPPER = JdbcTradeReplayRecordRepository::mapRow;
    private final JdbcTemplate jdbcTemplate;

    public JdbcTradeReplayRecordRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insert(TradeReplayRecord record) {
        jdbcTemplate.update("""
                INSERT INTO trade_replay_records (
                    replay_record_id, paper_run_id, paper_order_id, paper_trade_id, replay_time,
                    event_type, symbol, side, price, quantity, reason,
                    decision_snapshot_json, risk_snapshot_json, market_snapshot_json, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), CAST(? AS JSONB), CAST(? AS JSONB), ?)
                """,
                record.replayRecordId(), record.paperRunId(), record.paperOrderId(), record.paperTradeId(),
                Timestamp.from(record.replayTime()), record.eventType(), record.symbol(), record.side(),
                record.price(), record.quantity(), record.reason(),
                record.decisionSnapshotJson(), record.riskSnapshotJson(), record.marketSnapshotJson(),
                Timestamp.from(record.createdAt()));
    }

    @Override
    public List<TradeReplayRecord> listByRunId(String paperRunId) {
        return jdbcTemplate.query("""
                SELECT replay_record_id, paper_run_id, paper_order_id, paper_trade_id, replay_time,
                       event_type, symbol, side, price, quantity, reason,
                       decision_snapshot_json::text AS decision_snapshot_json,
                       risk_snapshot_json::text AS risk_snapshot_json,
                       market_snapshot_json::text AS market_snapshot_json, created_at
                FROM trade_replay_records WHERE paper_run_id = ? ORDER BY replay_time DESC
                """, ROW_MAPPER, paperRunId);
    }

    private static TradeReplayRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        BigDecimal price = rs.getBigDecimal("price");
        BigDecimal quantity = rs.getBigDecimal("quantity");
        return new TradeReplayRecord(
                rs.getString("replay_record_id"), rs.getString("paper_run_id"),
                rs.getString("paper_order_id"), rs.getString("paper_trade_id"),
                rs.getTimestamp("replay_time").toInstant(), rs.getString("event_type"),
                rs.getString("symbol"), rs.getString("side"), price, quantity,
                rs.getString("reason"), rs.getString("decision_snapshot_json"),
                rs.getString("risk_snapshot_json"), rs.getString("market_snapshot_json"),
                rs.getTimestamp("created_at").toInstant());
    }
}
