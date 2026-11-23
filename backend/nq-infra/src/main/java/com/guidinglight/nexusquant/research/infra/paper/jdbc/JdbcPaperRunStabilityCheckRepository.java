package com.guidinglight.nexusquant.research.infra.paper.jdbc;

import com.guidinglight.nexusquant.research.domain.paper.PaperRunStabilityCheck;
import com.guidinglight.nexusquant.research.domain.paper.PaperRunStabilityCheckStatus;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRunStabilityCheckRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPaperRunStabilityCheckRepository implements PaperRunStabilityCheckRepository {

    private static final RowMapper<PaperRunStabilityCheck> ROW_MAPPER = JdbcPaperRunStabilityCheckRepository::mapRow;
    private static final String BASE_SELECT = """
            SELECT stability_check_id, paper_run_id, check_window_start, check_window_end,
                   status, uptime_ratio, heartbeat_count, alert_count, failed_fire_count,
                   recovery_count, report_count, summary_json::text AS summary_json, created_at
            FROM paper_run_stability_checks
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcPaperRunStabilityCheckRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void upsert(PaperRunStabilityCheck check) {
        jdbcTemplate.update("""
                INSERT INTO paper_run_stability_checks (
                    stability_check_id, paper_run_id, check_window_start, check_window_end,
                    status, uptime_ratio, heartbeat_count, alert_count, failed_fire_count,
                    recovery_count, report_count, summary_json, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), ?)
                ON CONFLICT (paper_run_id, check_window_start, check_window_end) DO UPDATE SET
                    status = EXCLUDED.status,
                    uptime_ratio = EXCLUDED.uptime_ratio,
                    heartbeat_count = EXCLUDED.heartbeat_count,
                    alert_count = EXCLUDED.alert_count,
                    failed_fire_count = EXCLUDED.failed_fire_count,
                    recovery_count = EXCLUDED.recovery_count,
                    report_count = EXCLUDED.report_count,
                    summary_json = EXCLUDED.summary_json
                """,
                check.stabilityCheckId(), check.paperRunId(),
                Timestamp.from(check.checkWindowStart()), Timestamp.from(check.checkWindowEnd()),
                check.status().name(), check.uptimeRatio(),
                check.heartbeatCount(), check.alertCount(), check.failedFireCount(),
                check.recoveryCount(), check.reportCount(),
                check.summaryJson(), Timestamp.from(check.createdAt()));
    }

    @Override
    public Optional<PaperRunStabilityCheck> findById(String stabilityCheckId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    BASE_SELECT + " WHERE stability_check_id = ?", ROW_MAPPER, stabilityCheckId));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<PaperRunStabilityCheck> findByRunIdAndWindow(String paperRunId, Instant windowStart, Instant windowEnd) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    BASE_SELECT + " WHERE paper_run_id = ? AND check_window_start = ? AND check_window_end = ?",
                    ROW_MAPPER, paperRunId, Timestamp.from(windowStart), Timestamp.from(windowEnd)));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public List<PaperRunStabilityCheck> listByRunId(String paperRunId, String status) {
        StringBuilder sql = new StringBuilder(BASE_SELECT).append(" WHERE paper_run_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(paperRunId);
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            params.add(status);
        }
        sql.append(" ORDER BY created_at DESC");
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
    }

    private static PaperRunStabilityCheck mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new PaperRunStabilityCheck(
                rs.getString("stability_check_id"),
                rs.getString("paper_run_id"),
                rs.getTimestamp("check_window_start").toInstant(),
                rs.getTimestamp("check_window_end").toInstant(),
                PaperRunStabilityCheckStatus.valueOf(rs.getString("status")),
                rs.getBigDecimal("uptime_ratio"),
                rs.getInt("heartbeat_count"),
                rs.getInt("alert_count"),
                rs.getInt("failed_fire_count"),
                rs.getInt("recovery_count"),
                rs.getInt("report_count"),
                rs.getString("summary_json"),
                rs.getTimestamp("created_at").toInstant());
    }
}
