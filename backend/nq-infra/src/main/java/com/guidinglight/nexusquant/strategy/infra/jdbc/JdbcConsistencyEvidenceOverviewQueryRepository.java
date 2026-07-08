package com.guidinglight.nexusquant.strategy.infra.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.guidinglight.nexusquant.strategy.domain.port.ConsistencyEvidenceOverviewFacts;
import com.guidinglight.nexusquant.strategy.domain.port.ConsistencyEvidenceOverviewFacts.ConsistencyReportFact;
import com.guidinglight.nexusquant.strategy.domain.port.ConsistencyEvidenceOverviewQueryPort;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JdbcConsistencyEvidenceOverviewQueryRepository 是 GateT-2 consistency evidence overview 的 JDBC read adapter。
 *
 * <p>职责：只通过 SELECT 读取 consistency report、Shadow Run、latest snapshot metadata 和 latest event metadata。
 * 该 adapter 不提供 create/update/delete/review/acknowledge 方法，不读取 credential/account/live order/ledger/
 * private trading 表，不读取 `shadow_run_snapshots.payload`，也不调用 runner、scheduler 或交易所 adapter。
 */
@Repository
public class JdbcConsistencyEvidenceOverviewQueryRepository implements ConsistencyEvidenceOverviewQueryPort {

    private static final String EVIDENCE_SQL = """
            WITH latest_snapshot AS (
                SELECT shadow_run_id,
                       id::text AS snapshot_id,
                       snapshot_type,
                       schema_version,
                       checksum,
                       captured_at,
                       ROW_NUMBER() OVER (
                           PARTITION BY shadow_run_id
                           ORDER BY captured_at DESC, created_at DESC, id DESC
                       ) AS rn
                FROM shadow_run_snapshots
            ),
            latest_event AS (
                SELECT shadow_run_id,
                       id::text AS event_id,
                       event_type,
                       reason_code,
                       created_at,
                       ROW_NUMBER() OVER (
                           PARTITION BY shadow_run_id
                           ORDER BY created_at DESC, id DESC
                       ) AS rn
                FROM shadow_run_events
            )
            SELECT scr.id AS consistency_report_id,
                   scr.shadow_run_id,
                   COALESCE(scr.paper_run_id, sr.paper_run_id) AS paper_run_id,
                   sr.strategy_version_id,
                   sr.dataset_id,
                   scr.comparison_status,
                   scr.metric_delta::text AS metric_delta,
                   scr.divergence_reasons::text AS divergence_reasons,
                   scr.limitations::text AS limitations,
                   scr.generated_at,
                   scr.trace_id,
                   ls.snapshot_id,
                   ls.snapshot_type,
                   ls.schema_version,
                   ls.checksum,
                   ls.captured_at AS latest_snapshot_at,
                   le.event_id,
                   le.event_type,
                   le.reason_code,
                   le.created_at AS latest_event_at
            FROM shadow_consistency_reports scr
            JOIN shadow_runs sr ON sr.id = scr.shadow_run_id
            LEFT JOIN latest_snapshot ls ON ls.shadow_run_id = scr.shadow_run_id AND ls.rn = 1
            LEFT JOIN latest_event le ON le.shadow_run_id = scr.shadow_run_id AND le.rn = 1
            ORDER BY scr.generated_at DESC, scr.created_at DESC, scr.id DESC
            LIMIT 50
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public JdbcConsistencyEvidenceOverviewQueryRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * 加载 bounded consistency evidence facts。
     *
     * <p>查询策略：按 latest report 倒序读取最多 50 条 evidence；只读取 JSONB 字段文本供 service 摘要化，
     * 不读取 snapshot payload，不读取 credential/account/order/ledger/private provider 表，不执行写 SQL。
     *
     * @return SELECT-only overview facts；没有本地 report 时返回空集合
     */
    @Override
    public ConsistencyEvidenceOverviewFacts loadOverviewFacts() {
        List<ConsistencyReportFact> reports = jdbcTemplate.query(EVIDENCE_SQL, this::mapEvidence, new Object[0]);
        return new ConsistencyEvidenceOverviewFacts(reports);
    }

    private ConsistencyReportFact mapEvidence(ResultSet rs, int rowNum) throws SQLException {
        return new ConsistencyReportFact(
                rs.getObject("consistency_report_id", UUID.class),
                rs.getObject("shadow_run_id", UUID.class),
                rs.getString("paper_run_id"),
                rs.getString("strategy_version_id"),
                rs.getObject("dataset_id", UUID.class),
                rs.getString("comparison_status"),
                readJson(rs.getString("metric_delta"), JsonNodeFactory.instance.objectNode()),
                readJson(rs.getString("divergence_reasons"), JsonNodeFactory.instance.arrayNode()),
                readJson(rs.getString("limitations"), JsonNodeFactory.instance.arrayNode()),
                toInstant(rs.getTimestamp("generated_at")),
                rs.getString("trace_id"),
                rs.getString("snapshot_id"),
                rs.getString("snapshot_type"),
                rs.getString("schema_version"),
                rs.getString("checksum"),
                toInstant(rs.getTimestamp("latest_snapshot_at")),
                rs.getString("event_id"),
                rs.getString("event_type"),
                rs.getString("reason_code"),
                toInstant(rs.getTimestamp("latest_event_at"))
        );
    }

    private JsonNode readJson(String value, JsonNode fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to parse consistency evidence JSONB field", ex);
        }
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
