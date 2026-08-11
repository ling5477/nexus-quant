package com.guidinglight.nexusquant.strategy.infra.jdbc;

import com.guidinglight.nexusquant.strategy.domain.port.StrategyValidationOverviewFacts.LatestDecisionFact;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunAuthorizationBoundary;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.ShadowRunCreationPlan;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseAdmissionPreviewFacts;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseAdmissionPreviewFactsRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Release admission preview 的 JDBC SELECT-only adapter。
 *
 * <p>一次 publish 主键查询只读取其不可变 backtest window、strategy/evaluation/publish 状态，以及明确绑定
 * 同一 publish 的最新 SIM Paper、Shadow 和 consistency 事实。查询有主键/索引边界，不做全表装载或 N+1；
 * 不提供写方法，不创建/启动 Shadow Run，不访问 credential、private endpoint、order、account 或 ledger。
 */
@Repository
public class JdbcStrategyReleaseAdmissionPreviewFactsRepository
        implements StrategyReleaseAdmissionPreviewFactsRepository {

    private static final ShadowRunCreationPlan.SideEffectPolicy DIAGNOSTIC_NO_SIDE_EFFECTS =
            new ShadowRunCreationPlan.SideEffectPolicy(true, true, true, true, true, true);

    private static final String SELECT_FACTS = """
            WITH selected_publish AS (
                SELECT p.publish_record_id,
                       p.strategy_version_id,
                       p.eval_report_id,
                       p.publish_status,
                       p.published_at,
                       p.updated_at AS publish_updated_at,
                       r.dataset_snapshot_json ->> 'datasetId' AS dataset_id,
                       r.config_snapshot_json ->> 'startTime' AS window_start,
                       r.config_snapshot_json ->> 'endTime' AS window_end,
                       sv.status AS strategy_version_status,
                       e.evaluation_status,
                       COALESCE(e.evaluated_at, e.updated_at, e.created_at) AS evaluated_at
                FROM backtest_publish_records p
                LEFT JOIN backtest_runs r ON r.backtest_run_id = p.backtest_run_id
                LEFT JOIN strategy_versions sv ON sv.strategy_version_id = p.strategy_version_id
                LEFT JOIN backtest_eval_reports e ON e.eval_report_id = p.eval_report_id
                WHERE p.publish_record_id = ?
            ),
            latest_paper AS (
                SELECT pr.paper_run_id,
                       pr.status AS paper_run_status,
                       pr.trade_env,
                       pr.updated_at
                FROM paper_trading_runs pr
                WHERE pr.publish_id = ?
                ORDER BY pr.updated_at DESC, pr.paper_run_id DESC
                LIMIT 1
            ),
            latest_shadow AS (
                SELECT sr.id,
                       sr.status AS shadow_run_status,
                       sr.updated_at
                FROM shadow_runs sr
                JOIN selected_publish sp
                  ON sp.strategy_version_id = sr.strategy_version_id
                 AND sp.publish_record_id = sr.publish_id
                ORDER BY sr.updated_at DESC, sr.created_at DESC, sr.id DESC
                LIMIT 1
            ),
            latest_consistency AS (
                SELECT scr.comparison_status,
                       scr.generated_at
                FROM shadow_consistency_reports scr
                JOIN latest_shadow ls ON ls.id = scr.shadow_run_id
                ORDER BY scr.generated_at DESC, scr.created_at DESC, scr.id DESC
                LIMIT 1
            )
            SELECT sp.strategy_version_id,
                   sp.dataset_id,
                   sp.eval_report_id,
                   sp.publish_record_id,
                   lp.paper_run_id,
                   ls.id AS shadow_run_id,
                   sp.strategy_version_status,
                   sp.evaluation_status,
                   sp.publish_status,
                   lp.paper_run_status,
                   lp.trade_env,
                   ls.shadow_run_status,
                   lc.comparison_status AS consistency_status,
                   sp.window_start,
                   sp.window_end,
                   COALESCE(
                       lc.generated_at,
                       ls.updated_at,
                       lp.updated_at,
                       sp.published_at,
                       sp.evaluated_at,
                       sp.publish_updated_at
                   ) AS evidence_updated_at
            FROM selected_publish sp
            LEFT JOIN latest_paper lp ON TRUE
            LEFT JOIN latest_shadow ls ON TRUE
            LEFT JOIN latest_consistency lc ON TRUE
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcStrategyReleaseAdmissionPreviewFactsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    /**
     * 按唯一 publish anchor 读取 admission facts。
     *
     * <p>事务由上层只读 query service 控制；缺行或 snapshot 时间格式异常均返回 missing/null facts，交由
     * canonical admission fail-closed。生产安全边界来自既有 Shadow schema/domain invariant，而非客户端输入。
     */
    @Override
    public StrategyReleaseAdmissionPreviewFacts loadByPublishRecordId(String publishRecordId) {
        List<StrategyReleaseAdmissionPreviewFacts> rows = jdbcTemplate.query(
                SELECT_FACTS,
                JdbcStrategyReleaseAdmissionPreviewFactsRepository::mapRow,
                publishRecordId,
                publishRecordId
        );
        return rows.stream().findFirst().orElseGet(StrategyReleaseAdmissionPreviewFacts::missing);
    }

    private static StrategyReleaseAdmissionPreviewFacts mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        Instant evidenceUpdatedAt = toInstant(resultSet.getTimestamp("evidence_updated_at"));
        Instant generatedAt = evidenceUpdatedAt == null ? Instant.EPOCH : evidenceUpdatedAt;
        LatestDecisionFact validationFact = new LatestDecisionFact(
                resultSet.getString("strategy_version_id"),
                parseUuid(resultSet.getString("dataset_id")),
                resultSet.getString("eval_report_id"),
                resultSet.getString("publish_record_id"),
                resultSet.getString("paper_run_id"),
                resultSet.getObject("shadow_run_id", UUID.class),
                normalizeStatus(resultSet.getString("strategy_version_status")),
                normalizeStatus(resultSet.getString("evaluation_status")),
                normalizeStatus(resultSet.getString("publish_status")),
                normalizeStatus(resultSet.getString("paper_run_status")),
                normalizeStatus(resultSet.getString("trade_env")),
                normalizeStatus(resultSet.getString("shadow_run_status")),
                normalizeStatus(resultSet.getString("consistency_status")),
                generatedAt,
                evidenceUpdatedAt
        );
        return new StrategyReleaseAdmissionPreviewFacts(
                validationFact,
                parseInstant(resultSet.getString("window_start")),
                parseInstant(resultSet.getString("window_end")),
                ShadowRunAuthorizationBoundary.DIAGNOSTIC_ONLY,
                DIAGNOSTIC_NO_SIDE_EFFECTS
        );
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String normalizeStatus(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static Instant toInstant(java.sql.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
