package com.guidinglight.nexusquant.strategy.infra.jdbc;

import com.guidinglight.nexusquant.strategy.domain.port.ShadowValidationWorkflowOverviewFacts;
import com.guidinglight.nexusquant.strategy.domain.port.ShadowValidationWorkflowOverviewFacts.OperatorEvidenceFact;
import com.guidinglight.nexusquant.strategy.domain.port.ShadowValidationWorkflowOverviewQueryPort;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JdbcShadowValidationWorkflowOverviewQueryRepository 是 GateT-1 workflow overview 的 JDBC read adapter。
 *
 * <p>职责：只通过 SELECT bounded union 读取 GateS 已有本地事实表，包括 strategy/evaluation/publish/Paper、
 * Shadow、consistency 和 incident/replay 证据。该 adapter 不提供 create / update / delete / review /
 * acknowledge 方法，不读取 credential/account/live order/ledger/private trading 表，不调用 runner、scheduler
 * 或交易所 adapter。
 */
@Repository
public class JdbcShadowValidationWorkflowOverviewQueryRepository implements ShadowValidationWorkflowOverviewQueryPort {

    private static final String OPERATOR_EVIDENCE_SQL = """
            WITH latest_eval AS (
                SELECT r.strategy_version_id,
                       e.eval_report_id,
                       e.evaluation_status,
                       e.evaluated_at,
                       ROW_NUMBER() OVER (
                           PARTITION BY r.strategy_version_id
                           ORDER BY COALESCE(e.evaluated_at, e.updated_at, e.created_at) DESC, e.eval_report_id DESC
                       ) AS rn
                FROM backtest_runs r
                JOIN backtest_eval_reports e ON e.backtest_run_id = r.backtest_run_id
                WHERE r.strategy_version_id IS NOT NULL
            ),
            latest_publish AS (
                SELECT p.strategy_version_id,
                       p.publish_record_id,
                       p.publish_status,
                       p.published_at,
                       ROW_NUMBER() OVER (
                           PARTITION BY p.strategy_version_id
                           ORDER BY COALESCE(p.published_at, p.updated_at, p.created_at) DESC, p.publish_record_id DESC
                       ) AS rn
                FROM backtest_publish_records p
                WHERE p.strategy_version_id IS NOT NULL
            ),
            latest_paper AS (
                SELECT pr.strategy_version_id,
                       pr.paper_run_id,
                       pr.status AS paper_run_status,
                       pr.trade_env,
                       pr.updated_at AS paper_updated_at,
                       ROW_NUMBER() OVER (
                           PARTITION BY pr.strategy_version_id
                           ORDER BY pr.updated_at DESC, pr.paper_run_id DESC
                       ) AS rn
                FROM paper_trading_runs pr
                WHERE pr.strategy_version_id IS NOT NULL
            ),
            latest_shadow AS (
                SELECT sr.strategy_version_id,
                       sr.id AS shadow_run_id,
                       sr.dataset_id,
                       sr.status AS shadow_run_status,
                       sr.updated_at AS shadow_updated_at,
                       sr.trace_id AS shadow_trace_id,
                       ROW_NUMBER() OVER (
                           PARTITION BY sr.strategy_version_id
                           ORDER BY sr.updated_at DESC, sr.created_at DESC, sr.id DESC
                       ) AS rn
                FROM shadow_runs sr
                WHERE sr.strategy_version_id IS NOT NULL
            ),
            latest_consistency AS (
                SELECT sr.strategy_version_id,
                       scr.id AS consistency_report_id,
                       scr.comparison_status,
                       scr.generated_at,
                       scr.trace_id,
                       ROW_NUMBER() OVER (
                           PARTITION BY sr.strategy_version_id
                           ORDER BY scr.generated_at DESC, scr.created_at DESC, scr.id DESC
                       ) AS rn
                FROM shadow_runs sr
                JOIN shadow_consistency_reports scr ON scr.shadow_run_id = sr.id
                WHERE sr.strategy_version_id IS NOT NULL
            ),
            strategy_items AS (
                SELECT 'STRATEGY_VALIDATION' AS source_type,
                       sv.strategy_version_id AS source_id,
                       sv.strategy_version_id,
                       ls.dataset_id,
                       le.eval_report_id,
                       lpr.paper_run_id,
                       ls.shadow_run_id,
                       lc.consistency_report_id,
                       NULL::text AS incident_evidence_id,
                       sv.status AS strategy_version_status,
                       le.evaluation_status,
                       lp.publish_status,
                       lpr.paper_run_status,
                       lpr.trade_env AS paper_trade_env,
                       ls.shadow_run_status,
                       lc.comparison_status AS consistency_status,
                       NULL::text AS incident_status,
                       NULL::text AS incident_severity,
                       COALESCE(
                           lc.generated_at,
                           ls.shadow_updated_at,
                           lpr.paper_updated_at,
                           lp.published_at,
                           le.evaluated_at,
                           sv.updated_at,
                           sv.created_at
                       ) AS evidence_updated_at,
                       COALESCE(lc.trace_id, ls.shadow_trace_id) AS trace_id
                FROM strategy_versions sv
                LEFT JOIN latest_eval le ON le.strategy_version_id = sv.strategy_version_id AND le.rn = 1
                LEFT JOIN latest_publish lp ON lp.strategy_version_id = sv.strategy_version_id AND lp.rn = 1
                LEFT JOIN latest_paper lpr ON lpr.strategy_version_id = sv.strategy_version_id AND lpr.rn = 1
                LEFT JOIN latest_shadow ls ON ls.strategy_version_id = sv.strategy_version_id AND ls.rn = 1
                LEFT JOIN latest_consistency lc ON lc.strategy_version_id = sv.strategy_version_id AND lc.rn = 1
            ),
            consistency_items AS (
                SELECT 'CONSISTENCY_REPORT' AS source_type,
                       scr.id::text AS source_id,
                       sr.strategy_version_id,
                       sr.dataset_id,
                       sr.evaluation_id AS eval_report_id,
                       COALESCE(scr.paper_run_id, sr.paper_run_id) AS paper_run_id,
                       sr.id AS shadow_run_id,
                       scr.id AS consistency_report_id,
                       NULL::text AS incident_evidence_id,
                       NULL::text AS strategy_version_status,
                       NULL::text AS evaluation_status,
                       NULL::text AS publish_status,
                       NULL::text AS paper_run_status,
                       NULL::text AS paper_trade_env,
                       sr.status AS shadow_run_status,
                       scr.comparison_status AS consistency_status,
                       NULL::text AS incident_status,
                       CASE
                           WHEN scr.comparison_status = 'FAILED' THEN 'HIGH'
                           WHEN scr.comparison_status = 'DIVERGED' THEN 'HIGH'
                           WHEN scr.comparison_status IN ('PARTIAL', 'NOT_COMPARABLE') THEN 'WARNING'
                           ELSE 'INFO'
                       END AS incident_severity,
                       scr.generated_at AS evidence_updated_at,
                       scr.trace_id AS trace_id
                FROM shadow_consistency_reports scr
                JOIN shadow_runs sr ON sr.id = scr.shadow_run_id
                WHERE scr.comparison_status <> 'CONSISTENT'
            ),
            incident_items AS (
                SELECT 'INCIDENT_REPLAY' AS source_type,
                       e.id::text AS source_id,
                       sr.strategy_version_id,
                       sr.dataset_id,
                       sr.evaluation_id AS eval_report_id,
                       sr.paper_run_id,
                       e.shadow_run_id,
                       NULL::uuid AS consistency_report_id,
                       e.id::text AS incident_evidence_id,
                       NULL::text AS strategy_version_status,
                       NULL::text AS evaluation_status,
                       NULL::text AS publish_status,
                       NULL::text AS paper_run_status,
                       NULL::text AS paper_trade_env,
                       sr.status AS shadow_run_status,
                       NULL::text AS consistency_status,
                       e.event_type AS incident_status,
                       CASE
                           WHEN e.event_type IN ('FAILED', 'ILLEGAL_STATE_TRANSITION_ATTEMPT') THEN 'HIGH'
                           WHEN e.event_type IN ('PRECHECK_BLOCKED', 'CANCELLED') THEN 'WARNING'
                           ELSE 'INFO'
                       END AS incident_severity,
                       e.created_at AS evidence_updated_at,
                       e.trace_id AS trace_id
                FROM shadow_run_events e
                LEFT JOIN shadow_runs sr ON sr.id = e.shadow_run_id
                WHERE e.event_type IN ('FAILED', 'ILLEGAL_STATE_TRANSITION_ATTEMPT', 'PRECHECK_BLOCKED', 'CANCELLED')
                UNION ALL
                SELECT 'INCIDENT_REPLAY' AS source_type,
                       a.alert_id AS source_id,
                       pr.strategy_version_id,
                       NULL::uuid AS dataset_id,
                       NULL::text AS eval_report_id,
                       a.paper_run_id,
                       NULL::uuid AS shadow_run_id,
                       NULL::uuid AS consistency_report_id,
                       a.alert_id AS incident_evidence_id,
                       NULL::text AS strategy_version_status,
                       NULL::text AS evaluation_status,
                       NULL::text AS publish_status,
                       pr.status AS paper_run_status,
                       pr.trade_env AS paper_trade_env,
                       NULL::text AS shadow_run_status,
                       NULL::text AS consistency_status,
                       a.status AS incident_status,
                       a.severity AS incident_severity,
                       a.created_at AS evidence_updated_at,
                       NULL::text AS trace_id
                FROM paper_run_alerts a
                LEFT JOIN paper_trading_runs pr ON pr.paper_run_id = a.paper_run_id
                UNION ALL
                SELECT 'INCIDENT_REPLAY' AS source_type,
                       r.recovery_event_id AS source_id,
                       pr.strategy_version_id,
                       NULL::uuid AS dataset_id,
                       NULL::text AS eval_report_id,
                       r.paper_run_id,
                       NULL::uuid AS shadow_run_id,
                       NULL::uuid AS consistency_report_id,
                       r.recovery_event_id AS incident_evidence_id,
                       NULL::text AS strategy_version_status,
                       NULL::text AS evaluation_status,
                       NULL::text AS publish_status,
                       pr.status AS paper_run_status,
                       pr.trade_env AS paper_trade_env,
                       NULL::text AS shadow_run_status,
                       NULL::text AS consistency_status,
                       r.status AS incident_status,
                       CASE WHEN r.status = 'FAILED' THEN 'HIGH' ELSE 'INFO' END AS incident_severity,
                       r.created_at AS evidence_updated_at,
                       NULL::text AS trace_id
                FROM paper_run_recovery_events r
                LEFT JOIN paper_trading_runs pr ON pr.paper_run_id = r.paper_run_id
                UNION ALL
                SELECT 'INCIDENT_REPLAY' AS source_type,
                       tr.replay_record_id AS source_id,
                       pr.strategy_version_id,
                       NULL::uuid AS dataset_id,
                       NULL::text AS eval_report_id,
                       tr.paper_run_id,
                       NULL::uuid AS shadow_run_id,
                       NULL::uuid AS consistency_report_id,
                       tr.replay_record_id AS incident_evidence_id,
                       NULL::text AS strategy_version_status,
                       NULL::text AS evaluation_status,
                       NULL::text AS publish_status,
                       pr.status AS paper_run_status,
                       pr.trade_env AS paper_trade_env,
                       NULL::text AS shadow_run_status,
                       NULL::text AS consistency_status,
                       tr.event_type AS incident_status,
                       'INFO' AS incident_severity,
                       tr.replay_time AS evidence_updated_at,
                       NULL::text AS trace_id
                FROM trade_replay_records tr
                LEFT JOIN paper_trading_runs pr ON pr.paper_run_id = tr.paper_run_id
            )
            SELECT source_type,
                   source_id,
                   strategy_version_id,
                   dataset_id,
                   eval_report_id,
                   paper_run_id,
                   shadow_run_id,
                   consistency_report_id,
                   incident_evidence_id,
                   strategy_version_status,
                   evaluation_status,
                   publish_status,
                   paper_run_status,
                   paper_trade_env,
                   shadow_run_status,
                   consistency_status,
                   incident_status,
                   incident_severity,
                   evidence_updated_at,
                   trace_id
            FROM (
                SELECT * FROM strategy_items
                UNION ALL
                SELECT * FROM consistency_items
                UNION ALL
                SELECT * FROM incident_items
            ) operator_evidence
            WHERE source_id IS NOT NULL
            ORDER BY evidence_updated_at DESC NULLS LAST, source_type, source_id
            LIMIT 20
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcShadowValidationWorkflowOverviewQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    /**
     * 加载 GateT-1 operator evidence facts。
     *
     * <p>查询策略：以 bounded union 读取最新 20 条 operator evidence；不读取 raw JSONB payload，不读取
     * credential/account/live order/ledger/private provider 表，不执行 INSERT / UPDATE / DELETE。
     *
     * @return SELECT-only facts；没有本地 evidence 时返回空集合
     */
    @Override
    public ShadowValidationWorkflowOverviewFacts loadOverviewFacts() {
        List<OperatorEvidenceFact> evidence = jdbcTemplate.query(
                OPERATOR_EVIDENCE_SQL,
                this::mapOperatorEvidence,
                new Object[0]
        );
        return new ShadowValidationWorkflowOverviewFacts(evidence);
    }

    private OperatorEvidenceFact mapOperatorEvidence(ResultSet rs, int rowNum) throws SQLException {
        return new OperatorEvidenceFact(
                rs.getString("source_type"),
                rs.getString("source_id"),
                rs.getString("strategy_version_id"),
                rs.getObject("dataset_id", UUID.class),
                rs.getString("eval_report_id"),
                rs.getString("paper_run_id"),
                rs.getObject("shadow_run_id", UUID.class),
                rs.getObject("consistency_report_id", UUID.class),
                rs.getString("incident_evidence_id"),
                rs.getString("strategy_version_status"),
                rs.getString("evaluation_status"),
                rs.getString("publish_status"),
                rs.getString("paper_run_status"),
                rs.getString("paper_trade_env"),
                rs.getString("shadow_run_status"),
                rs.getString("consistency_status"),
                rs.getString("incident_status"),
                rs.getString("incident_severity"),
                toInstant(rs.getTimestamp("evidence_updated_at")),
                rs.getString("trace_id")
        );
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? Instant.EPOCH : timestamp.toInstant();
    }
}
