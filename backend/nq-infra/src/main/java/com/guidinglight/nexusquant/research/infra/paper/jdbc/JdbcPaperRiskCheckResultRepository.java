package com.guidinglight.nexusquant.research.infra.paper.jdbc;

import com.guidinglight.nexusquant.research.domain.paper.PaperRiskCheckResult;
import com.guidinglight.nexusquant.research.domain.paper.RiskCheckSeverity;
import com.guidinglight.nexusquant.research.domain.paper.RiskCheckStatus;
import com.guidinglight.nexusquant.research.domain.paper.port.PaperRiskCheckResultRepository;

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
public class JdbcPaperRiskCheckResultRepository implements PaperRiskCheckResultRepository {

    private static final RowMapper<PaperRiskCheckResult> ROW_MAPPER = JdbcPaperRiskCheckResultRepository::mapRow;
    private static final String SELECT_COLUMNS = """
            SELECT risk_result_id, paper_run_id, check_type, status, severity, message,
                   input_snapshot_json::text AS input_snapshot_json,
                   result_snapshot_json::text AS result_snapshot_json, created_at
            FROM paper_risk_check_results
            """;

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    public JdbcPaperRiskCheckResultRepository(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, new NamedParameterJdbcTemplate(jdbcTemplate));
    }

    JdbcPaperRiskCheckResultRepository(JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
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
        return jdbcTemplate.query(
                SELECT_COLUMNS + " WHERE paper_run_id = ? ORDER BY created_at DESC",
                ROW_MAPPER, paperRunId);
    }

    @Override
    public Map<String, List<PaperRiskCheckResult>> listByRunIds(Collection<String> runIds) {
        if (runIds == null || runIds.isEmpty()) {
            return Map.of();
        }
        // 去重后以命名参数绑定 IN 列表（参数化，杜绝 SQL 拼接注入）；按 created_at DESC 与单 run 口径一致。
        MapSqlParameterSource params = new MapSqlParameterSource(
                "runIds", new ArrayList<>(new LinkedHashSet<>(runIds)));
        List<PaperRiskCheckResult> rows = namedParameterJdbcTemplate.query(
                SELECT_COLUMNS + " WHERE paper_run_id IN (:runIds) ORDER BY created_at DESC",
                params, ROW_MAPPER);
        // groupingBy 保留全局有序结果在各分组内的相对顺序，单 run 列表口径仍为 created_at DESC。
        return rows.stream().collect(Collectors.groupingBy(
                PaperRiskCheckResult::paperRunId, LinkedHashMap::new, Collectors.toList()));
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
