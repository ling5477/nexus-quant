package com.guidinglight.nexusquant.research.infra.paper.jdbc;

import com.guidinglight.nexusquant.research.domain.paper.PaperRiskCheckResult;
import com.guidinglight.nexusquant.research.domain.paper.RiskCheckSeverity;
import com.guidinglight.nexusquant.research.domain.paper.RiskCheckStatus;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRiskCheckResultRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPaperRiskCheckResultRepository implements PaperRiskCheckResultRepository {

    private static final RowMapper<PaperRiskCheckResult> ROW_MAPPER = JdbcPaperRiskCheckResultRepository::mapRow;
    private final JdbcTemplate jdbcTemplate;

    public JdbcPaperRiskCheckResultRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insert(PaperRiskCheckResult result) {
        jdbcTemplate.update("""
                INSERT INTO paper_risk_check_results (
                    risk_result_id, paper_run_id, check_type, status, severity, message,
                    input_snapshot_json, result_snapshot_json, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, CAST(? AS JSONB), CAST(? AS JSONB), ?)
                """,
                result.riskResultId(), result.paperRunId(), result.checkType(),
                result.status().name(), result.severity().name(), result.message(),
                result.inputSnapshotJson(), result.resultSnapshotJson(),
                Timestamp.from(result.createdAt()));
    }

    @Override
    public List<PaperRiskCheckResult> listByRunId(String paperRunId) {
        return jdbcTemplate.query("""
                SELECT risk_result_id, paper_run_id, check_type, status, severity, message,
                       input_snapshot_json::text AS input_snapshot_json,
                       result_snapshot_json::text AS result_snapshot_json, created_at
                FROM paper_risk_check_results WHERE paper_run_id = ? ORDER BY created_at DESC
                """, ROW_MAPPER, paperRunId);
    }

    private static PaperRiskCheckResult mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new PaperRiskCheckResult(
                rs.getString("risk_result_id"), rs.getString("paper_run_id"),
                rs.getString("check_type"), RiskCheckStatus.valueOf(rs.getString("status")),
                RiskCheckSeverity.valueOf(rs.getString("severity")), rs.getString("message"),
                rs.getString("input_snapshot_json"), rs.getString("result_snapshot_json"),
                rs.getTimestamp("created_at").toInstant());
    }
}
