package com.guidinglight.nexusquant.strategy.infra.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.strategy.domain.port.ShadowRunOverviewEvidenceFact;
import com.guidinglight.nexusquant.strategy.domain.port.ShadowRunOverviewFacts;
import com.guidinglight.nexusquant.strategy.domain.port.ShadowRunOverviewQueryPort;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowConsistencyComparisonStatus;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowConsistencyReport;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRun;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunAuthorizationBoundary;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStatus;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JdbcShadowRunOverviewQueryRepository 是 GateS-1 Shadow Run overview 的 JDBC read adapter。
 *
 * <p>职责：只通过 SELECT 聚合 `shadow_runs`、`shadow_run_events`、`shadow_run_snapshots`
 * 和 `shadow_consistency_reports` 的本地事实。该 adapter 不提供 create/update/delete 方法，不调用
 * runner、scheduler、adapter、credential、order、account 或 ledger 服务。JSONB 字段复用 domain
 * guard 在 record 构造阶段校验，避免敏感字段进入 read model。
 */
@Repository
public class JdbcShadowRunOverviewQueryRepository implements ShadowRunOverviewQueryPort {

    private static final String RUN_SELECT = """
            SELECT id, strategy_version_id, dataset_id, evaluation_id, publish_id, artifact_digest, paper_run_id,
                   status, window_start, window_end, side_effect_policy::text AS side_effect_policy,
                   no_order_submission, no_credential_access, no_private_endpoint, no_ledger_mutation,
                   no_account_mutation, no_external_private_io, authorization_boundary, request_id,
                   idempotency_key, trace_id, blockers::text AS blockers, warnings::text AS warnings,
                   next_steps::text AS next_steps, version, created_at, updated_at, started_at,
                   stopped_at, completed_at
            FROM shadow_runs
            """;

    private static final String REPORT_SELECT = """
            SELECT id, shadow_run_id, paper_run_id, comparison_status, metric_delta::text AS metric_delta,
                   divergence_reasons::text AS divergence_reasons, limitations::text AS limitations,
                   generated_at, trace_id, created_at
            FROM shadow_consistency_reports
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public JdbcShadowRunOverviewQueryRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * 加载 overview facts。
     *
     * <p>查询语义：counts 只按 `shadow_runs.status` 统计；latestRun 按 `updated_at` 优先选择；
     * latestConsistency 按 `generated_at` 选择；staleRuns 第一版定义为存在 run 但缺 snapshot 或
     * consistency report。所有 SQL 均为 SELECT，不修改任何本地事实或交易状态。
     *
     * @return overview facts；空表返回稳定空结构
     */
    @Override
    public ShadowRunOverviewFacts loadOverviewFacts() {
        return new ShadowRunOverviewFacts(
                count("SELECT COUNT(*) FROM shadow_runs"),
                count("SELECT COUNT(*) FROM shadow_runs WHERE status = 'RUNNING'"),
                count("SELECT COUNT(*) FROM shadow_runs WHERE status = 'BLOCKED'"),
                count("SELECT COUNT(*) FROM shadow_runs WHERE status = 'FAILED'"),
                count("SELECT COUNT(*) FROM shadow_runs WHERE status = 'COMPLETED'"),
                count("""
                        SELECT COUNT(*)
                        FROM shadow_runs sr
                        WHERE NOT EXISTS (
                            SELECT 1 FROM shadow_run_snapshots ss WHERE ss.shadow_run_id = sr.id
                        )
                           OR NOT EXISTS (
                            SELECT 1 FROM shadow_consistency_reports scr WHERE scr.shadow_run_id = sr.id
                        )
                        """),
                queryOptional(RUN_SELECT + " ORDER BY updated_at DESC, created_at DESC, id DESC LIMIT 1", runRowMapper()),
                queryOptional(REPORT_SELECT + " ORDER BY generated_at DESC, created_at DESC, id DESC LIMIT 1", reportRowMapper()),
                queryOptional("""
                        SELECT id::text AS source_id, event_type AS source_version, created_at AS source_timestamp
                        FROM shadow_run_events
                        ORDER BY created_at DESC, id DESC
                        LIMIT 1
                        """, evidenceRowMapper("SHADOW_EVENT", "source_version", null)),
                queryOptional("""
                        SELECT id::text AS source_id, schema_version AS source_version, captured_at AS source_timestamp,
                               checksum
                        FROM shadow_run_snapshots
                        ORDER BY captured_at DESC, created_at DESC, id DESC
                        LIMIT 1
                        """, evidenceRowMapper("SHADOW_SNAPSHOT", "source_version", "checksum"))
        );
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, new Object[0]);
        return value == null ? 0L : value;
    }

    private <T> Optional<T> queryOptional(String sql, RowMapper<T> rowMapper) {
        List<T> rows = jdbcTemplate.query(sql, rowMapper, new Object[0]);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    private RowMapper<ShadowRun> runRowMapper() {
        return this::mapRun;
    }

    private RowMapper<ShadowConsistencyReport> reportRowMapper() {
        return this::mapReport;
    }

    private RowMapper<ShadowRunOverviewEvidenceFact> evidenceRowMapper(
            String sourceType,
            String versionColumn,
            String checksumColumn
    ) {
        return (rs, rowNum) -> new ShadowRunOverviewEvidenceFact(
                sourceType,
                rs.getString("source_id"),
                rs.getString(versionColumn),
                toInstant(rs.getTimestamp("source_timestamp")),
                checksumColumn == null ? null : rs.getString(checksumColumn)
        );
    }

    private ShadowRun mapRun(ResultSet rs, int rowNum) throws SQLException {
        return new ShadowRun(
                rs.getObject("id", UUID.class),
                rs.getString("strategy_version_id"),
                rs.getObject("dataset_id", UUID.class),
                rs.getString("evaluation_id"),
                rs.getString("publish_id"),
                rs.getString("artifact_digest"),
                rs.getString("paper_run_id"),
                ShadowRunStatus.fromDatabase(rs.getString("status")),
                toInstant(rs.getTimestamp("window_start")),
                toInstant(rs.getTimestamp("window_end")),
                readJson(rs.getString("side_effect_policy")),
                rs.getBoolean("no_order_submission"),
                rs.getBoolean("no_credential_access"),
                rs.getBoolean("no_private_endpoint"),
                rs.getBoolean("no_ledger_mutation"),
                rs.getBoolean("no_account_mutation"),
                rs.getBoolean("no_external_private_io"),
                ShadowRunAuthorizationBoundary.valueOf(rs.getString("authorization_boundary")),
                rs.getString("request_id"),
                rs.getString("idempotency_key"),
                rs.getString("trace_id"),
                readJson(rs.getString("blockers")),
                readJson(rs.getString("warnings")),
                readJson(rs.getString("next_steps")),
                rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                toInstant(rs.getTimestamp("started_at")),
                toInstant(rs.getTimestamp("stopped_at")),
                toInstant(rs.getTimestamp("completed_at"))
        );
    }

    private ShadowConsistencyReport mapReport(ResultSet rs, int rowNum) throws SQLException {
        return new ShadowConsistencyReport(
                rs.getObject("id", UUID.class),
                rs.getObject("shadow_run_id", UUID.class),
                rs.getString("paper_run_id"),
                ShadowConsistencyComparisonStatus.valueOf(rs.getString("comparison_status")),
                readJson(rs.getString("metric_delta")),
                readJson(rs.getString("divergence_reasons")),
                readJson(rs.getString("limitations")),
                rs.getTimestamp("generated_at").toInstant(),
                rs.getString("trace_id"),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value == null || value.isBlank() ? "{}" : value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to parse shadow run overview JSONB field", ex);
        }
    }

    private static Instant toInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
