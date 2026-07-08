package com.guidinglight.nexusquant.strategy.infra.jdbc;

import com.guidinglight.nexusquant.strategy.domain.port.StrategyValidationOverviewFacts;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyValidationOverviewFacts.LatestDecisionFact;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyValidationOverviewFacts.OverviewCounts;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyValidationOverviewQueryPort;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JdbcStrategyValidationOverviewQueryRepository 是 GateS-3 Strategy Validation overview 的 JDBC read adapter。
 *
 * <p>职责：只通过 SELECT 聚合 strategy_versions、backtest_eval_reports、backtest_publish_records、
 * paper_trading_runs、shadow_runs 和 shadow_consistency_reports 的本地事实。该 adapter 不提供 create /
 * update / delete 方法，不调用 runner、scheduler、adapter、credential、order、account 或 ledger 服务。
 */
@Repository
public class JdbcStrategyValidationOverviewQueryRepository implements StrategyValidationOverviewQueryPort {

    private static final String CLASSIFIED_FACTS_CTE = """
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
                       ROW_NUMBER() OVER (
                           PARTITION BY sr.strategy_version_id
                           ORDER BY sr.updated_at DESC, sr.created_at DESC, sr.id DESC
                       ) AS rn
                FROM shadow_runs sr
                WHERE sr.strategy_version_id IS NOT NULL
            ),
            latest_consistency AS (
                SELECT sr.strategy_version_id,
                       scr.comparison_status,
                       scr.generated_at,
                       ROW_NUMBER() OVER (
                           PARTITION BY sr.strategy_version_id
                           ORDER BY scr.generated_at DESC, scr.created_at DESC, scr.id DESC
                       ) AS rn
                FROM shadow_runs sr
                JOIN shadow_consistency_reports scr ON scr.shadow_run_id = sr.id
                WHERE sr.strategy_version_id IS NOT NULL
            ),
            classified AS (
                SELECT sv.strategy_version_id,
                       ls.dataset_id,
                       le.eval_report_id,
                       lp.publish_record_id,
                       lpr.paper_run_id,
                       ls.shadow_run_id,
                       sv.status AS strategy_version_status,
                       le.evaluation_status,
                       lp.publish_status,
                       lpr.paper_run_status,
                       lpr.trade_env,
                       ls.shadow_run_status,
                       lc.comparison_status AS consistency_status,
                       COALESCE(
                           lc.generated_at,
                           ls.shadow_updated_at,
                           lpr.paper_updated_at,
                           lp.published_at,
                           le.evaluated_at,
                           sv.updated_at,
                           sv.created_at
                       ) AS evidence_updated_at,
                       CASE
                           WHEN le.eval_report_id IS NULL THEN 'NO_EVIDENCE'
                           WHEN UPPER(COALESCE(sv.status, '')) <> 'ACTIVE' THEN 'BLOCKED'
                           WHEN UPPER(COALESCE(le.evaluation_status, '')) IN ('FAILED', 'FAILURE', 'ERROR') THEN 'REJECTED'
                           WHEN UPPER(COALESCE(le.evaluation_status, '')) <> 'SUCCEEDED' THEN 'NEEDS_REVIEW'
                           WHEN lp.publish_record_id IS NULL OR UPPER(COALESCE(lp.publish_status, '')) <> 'SUCCEEDED' THEN 'NEEDS_REVIEW'
                           WHEN lpr.paper_run_id IS NULL
                                OR UPPER(COALESCE(lpr.trade_env, '')) <> 'SIM'
                                OR UPPER(COALESCE(lpr.paper_run_status, '')) NOT IN ('RUNNING', 'STOPPED') THEN 'NEEDS_REVIEW'
                           WHEN UPPER(COALESCE(ls.shadow_run_status, '')) IN ('BLOCKED', 'FAILED') THEN 'BLOCKED'
                           WHEN ls.shadow_run_id IS NOT NULL AND lc.comparison_status IS NULL THEN 'STALE_EVIDENCE'
                           WHEN UPPER(COALESCE(lc.comparison_status, '')) = 'FAILED' THEN 'BLOCKED'
                           WHEN UPPER(COALESCE(lc.comparison_status, '')) IN ('DIVERGED', 'NOT_COMPARABLE', 'PARTIAL') THEN 'NEEDS_REVIEW'
                           ELSE 'APPROVED'
                       END AS validation_decision
                FROM strategy_versions sv
                LEFT JOIN latest_eval le ON le.strategy_version_id = sv.strategy_version_id AND le.rn = 1
                LEFT JOIN latest_publish lp ON lp.strategy_version_id = sv.strategy_version_id AND lp.rn = 1
                LEFT JOIN latest_paper lpr ON lpr.strategy_version_id = sv.strategy_version_id AND lpr.rn = 1
                LEFT JOIN latest_shadow ls ON ls.strategy_version_id = sv.strategy_version_id AND ls.rn = 1
                LEFT JOIN latest_consistency lc ON lc.strategy_version_id = sv.strategy_version_id AND lc.rn = 1
            )
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcStrategyValidationOverviewQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    /**
     * 加载 GateS-3 validation overview facts。
     *
     * <p>查询策略：counts 使用聚合 SQL 计算，不把所有 strategy version 拉到 Java；latest decision 只按最新
     * evidence 取一条锚点。所有 SQL 都是 SELECT，不写库、不外联、不读取 credential/account/order/ledger。
     *
     * @return overview facts；空表返回稳定空结构
     */
    @Override
    public StrategyValidationOverviewFacts loadOverviewFacts() {
        OverviewCounts counts = queryOptional(
                CLASSIFIED_FACTS_CTE + """
                        SELECT COUNT(*) AS total_strategy_versions,
                               COUNT(eval_report_id) AS evaluated_strategy_versions,
                               COALESCE(SUM(CASE WHEN validation_decision = 'APPROVED' THEN 1 ELSE 0 END), 0) AS approved_for_validation,
                               COALESCE(SUM(CASE WHEN validation_decision = 'REJECTED' THEN 1 ELSE 0 END), 0) AS rejected_for_validation,
                               COALESCE(SUM(CASE WHEN validation_decision IN ('NEEDS_REVIEW', 'NO_EVIDENCE', 'STALE_EVIDENCE') THEN 1 ELSE 0 END), 0) AS needs_review,
                               COALESCE(SUM(CASE WHEN validation_decision = 'BLOCKED' THEN 1 ELSE 0 END), 0) AS blocked
                        FROM classified
                        """,
                this::mapCounts
        ).orElseGet(OverviewCounts::empty);
        Optional<LatestDecisionFact> latestDecision = queryOptional(
                CLASSIFIED_FACTS_CTE + """
                        SELECT strategy_version_id,
                               dataset_id,
                               eval_report_id,
                               publish_record_id,
                               paper_run_id,
                               shadow_run_id,
                               strategy_version_status,
                               evaluation_status,
                               publish_status,
                               paper_run_status,
                               trade_env,
                               shadow_run_status,
                               consistency_status,
                               evidence_updated_at
                        FROM classified
                        ORDER BY evidence_updated_at DESC NULLS LAST, strategy_version_id DESC
                        LIMIT 1
                        """,
                this::mapLatestDecision
        );
        return new StrategyValidationOverviewFacts(
                counts.totalStrategyVersions(),
                counts.evaluatedStrategyVersions(),
                counts.approvedForValidation(),
                counts.rejectedForValidation(),
                counts.needsReview(),
                counts.blocked(),
                latestDecision
        );
    }

    private OverviewCounts mapCounts(ResultSet rs, int rowNum) throws SQLException {
        return new OverviewCounts(
                rs.getLong("total_strategy_versions"),
                rs.getLong("evaluated_strategy_versions"),
                rs.getLong("approved_for_validation"),
                rs.getLong("rejected_for_validation"),
                rs.getLong("needs_review"),
                rs.getLong("blocked")
        );
    }

    private LatestDecisionFact mapLatestDecision(ResultSet rs, int rowNum) throws SQLException {
        Instant evidenceUpdatedAt = toInstant(rs.getTimestamp("evidence_updated_at"));
        Instant generatedAt = evidenceUpdatedAt == null ? Instant.EPOCH : evidenceUpdatedAt;
        return new LatestDecisionFact(
                rs.getString("strategy_version_id"),
                rs.getObject("dataset_id", UUID.class),
                rs.getString("eval_report_id"),
                rs.getString("publish_record_id"),
                rs.getString("paper_run_id"),
                rs.getObject("shadow_run_id", UUID.class),
                rs.getString("strategy_version_status"),
                rs.getString("evaluation_status"),
                rs.getString("publish_status"),
                rs.getString("paper_run_status"),
                rs.getString("trade_env"),
                rs.getString("shadow_run_status"),
                rs.getString("consistency_status"),
                generatedAt,
                evidenceUpdatedAt
        );
    }

    private <T> Optional<T> queryOptional(String sql, RowMapper<T> rowMapper) {
        List<T> rows = jdbcTemplate.query(sql, rowMapper, new Object[0]);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

}
