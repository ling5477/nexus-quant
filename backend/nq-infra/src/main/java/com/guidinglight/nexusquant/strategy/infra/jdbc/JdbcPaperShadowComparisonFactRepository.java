package com.guidinglight.nexusquant.strategy.infra.jdbc;

import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonFactRepository;
import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonFacts;
import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonFacts.DatasetFact;
import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonFacts.EvaluationFact;
import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonFacts.PaperRunFact;
import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonFacts.PublishTraceFact;
import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonFacts.ShadowRunFact;
import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonFacts.StrategyVersionFact;
import com.guidinglight.nexusquant.strategy.application.papershadowcomparison.PaperShadowComparisonQuery;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JdbcPaperShadowComparisonFactRepository 聚合 GateQ-2 Paper vs Shadow 只读对照所需本地 DB 事实。
 *
 * <p>Why: 本 adapter 只能复用现有 strategy_versions、marketdata_datasets、backtest_eval_reports、
 * backtest_publish_records 和 paper_trading_runs。当前没有 shadow run 表或 runner，因此 shadow fact
 * 固定返回 NOT_IMPLEMENTED；本类只执行 SELECT，不写库、不触发 Paper/Shadow runner、不访问外部网络。
 */
@Repository
public class JdbcPaperShadowComparisonFactRepository implements PaperShadowComparisonFactRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcPaperShadowComparisonFactRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    /**
     * 读取 Paper vs Shadow 对照所需的最小事实集合。
     *
     * @param query 只读查询范围
     * @return 聚合事实；缺失项用 missing/notImplemented fact 表达，交由 core service fail-closed
     */
    @Override
    public PaperShadowComparisonFacts loadFacts(PaperShadowComparisonQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        StrategyVersionFact strategyVersion = loadStrategyVersion(query);
        DatasetFact dataset = loadDataset(query);
        EvaluationFact evaluation = loadEvaluation(query);
        PublishTraceFact publishTrace = loadPublishTrace(query, evaluation);
        PaperRunFact paperRun = loadPaperRun(query, publishTrace);
        ShadowRunFact shadowRun = loadShadowRun(query);
        return new PaperShadowComparisonFacts(strategyVersion, dataset, evaluation, publishTrace, paperRun, shadowRun);
    }

    private StrategyVersionFact loadStrategyVersion(PaperShadowComparisonQuery query) {
        if (query.strategyVersionId() == null) {
            return StrategyVersionFact.missing();
        }
        List<StrategyVersionFact> rows = jdbcTemplate.query(
                """
                        SELECT sv.strategy_version_id,
                               sv.strategy_code,
                               sd.strategy_id,
                               sv.status
                        FROM strategy_versions sv
                        LEFT JOIN strategy_definitions sd ON sd.strategy_code = sv.strategy_code
                        WHERE sv.strategy_version_id = ?
                        """,
                (rs, rowNum) -> {
                    String strategyId = rs.getString("strategy_id");
                    String strategyCode = rs.getString("strategy_code");
                    return new StrategyVersionFact(
                            true,
                            matchesRequestedStrategy(query.strategyId(), strategyId, strategyCode),
                            strategyId,
                            strategyCode,
                            rs.getString("strategy_version_id"),
                            normalizeStatus(rs.getString("status"))
                    );
                },
                query.strategyVersionId()
        );
        return rows.stream().findFirst().orElseGet(StrategyVersionFact::missing);
    }

    private DatasetFact loadDataset(PaperShadowComparisonQuery query) {
        if (query.datasetId() == null) {
            return DatasetFact.missing(null);
        }
        List<DatasetFact> rows = jdbcTemplate.query(
                """
                        SELECT d.dataset_id,
                               d.status AS dataset_status,
                               d.quality_status AS dataset_quality_status,
                               d.bar_count,
                               d.gap_count,
                               lc.quality_status AS coverage_quality_status,
                               lc.missing_bars,
                               lc.invalid_bars,
                               lc.duplicate_bars,
                               lc.created_at AS latest_coverage_at
                        FROM marketdata_datasets d
                        LEFT JOIN LATERAL (
                            SELECT c.quality_status,
                                   c.missing_bars,
                                   c.invalid_bars,
                                   c.duplicate_bars,
                                   c.created_at
                            FROM marketdata_dataset_coverage c
                            WHERE c.dataset_id = d.dataset_id
                            ORDER BY c.created_at DESC
                            LIMIT 1
                        ) lc ON TRUE
                        WHERE d.dataset_id = ?
                        """,
                (rs, rowNum) -> new DatasetFact(
                        true,
                        rs.getObject("dataset_id", UUID.class),
                        normalizeStatus(rs.getString("dataset_status")),
                        normalizeStatus(rs.getString("dataset_quality_status")),
                        normalizeStatus(rs.getString("coverage_quality_status")),
                        nullableLong(rs.getObject("bar_count")),
                        nullableLong(rs.getObject("gap_count")),
                        nullableLong(rs.getObject("missing_bars")),
                        nullableLong(rs.getObject("invalid_bars")),
                        nullableLong(rs.getObject("duplicate_bars")),
                        toInstant(rs.getTimestamp("latest_coverage_at"))
                ),
                query.datasetId()
        );
        return rows.stream().findFirst().orElseGet(() -> DatasetFact.missing(query.datasetId()));
    }

    private EvaluationFact loadEvaluation(PaperShadowComparisonQuery query) {
        if (query.evaluationId() != null) {
            return loadEvaluationBySql(
                    """
                            SELECT e.eval_report_id,
                                   e.backtest_run_id,
                                   e.evaluation_status,
                                   e.trade_count,
                                   e.metrics_json::text AS metrics_json,
                                   e.evaluated_at
                            FROM backtest_eval_reports e
                            JOIN backtest_runs r ON r.backtest_run_id = e.backtest_run_id
                            WHERE e.eval_report_id = ?
                              AND (? IS NULL OR r.strategy_version_id = ?)
                            """,
                    query.evaluationId(),
                    query.strategyVersionId(),
                    query.strategyVersionId()
            ).orElseGet(() -> EvaluationFact.missing(query.evaluationId()));
        }
        if (query.publishId() != null) {
            return loadEvaluationBySql(
                    """
                            SELECT e.eval_report_id,
                                   e.backtest_run_id,
                                   e.evaluation_status,
                                   e.trade_count,
                                   e.metrics_json::text AS metrics_json,
                                   e.evaluated_at
                            FROM backtest_publish_records p
                            JOIN backtest_eval_reports e ON e.eval_report_id = p.eval_report_id
                            JOIN backtest_runs r ON r.backtest_run_id = e.backtest_run_id
                            WHERE p.publish_record_id = ?
                              AND (? IS NULL OR p.strategy_version_id = ? OR r.strategy_version_id = ?)
                            """,
                    query.publishId(),
                    query.strategyVersionId(),
                    query.strategyVersionId(),
                    query.strategyVersionId()
            ).orElseGet(() -> EvaluationFact.missing(null));
        }
        return loadEvaluationBySql(
                """
                        SELECT e.eval_report_id,
                               e.backtest_run_id,
                               e.evaluation_status,
                               e.trade_count,
                               e.metrics_json::text AS metrics_json,
                               e.evaluated_at
                        FROM backtest_eval_reports e
                        JOIN backtest_runs r ON r.backtest_run_id = e.backtest_run_id
                        WHERE r.strategy_version_id = ?
                        ORDER BY COALESCE(e.evaluated_at, e.updated_at, e.created_at) DESC, e.eval_report_id DESC
                        LIMIT 1
                        """,
                query.strategyVersionId()
        ).orElseGet(() -> EvaluationFact.missing(null));
    }

    private Optional<EvaluationFact> loadEvaluationBySql(String sql, Object... args) {
        List<EvaluationFact> rows = jdbcTemplate.query(sql, JdbcPaperShadowComparisonFactRepository::mapEvaluation, args);
        return rows.stream().findFirst();
    }

    private static EvaluationFact mapEvaluation(ResultSet rs, int rowNum) throws SQLException {
        String metricsJson = rs.getString("metrics_json");
        return new EvaluationFact(
                true,
                rs.getString("eval_report_id"),
                rs.getString("backtest_run_id"),
                normalizeStatus(rs.getString("evaluation_status")),
                metricsComplete(rs.getObject("trade_count"), metricsJson),
                toInstant(rs.getTimestamp("evaluated_at"))
        );
    }

    private PublishTraceFact loadPublishTrace(PaperShadowComparisonQuery query, EvaluationFact evaluation) {
        if (query.publishId() != null) {
            return loadPublishBySql(
                    """
                            SELECT publish_record_id,
                                   backtest_run_id,
                                   eval_report_id,
                                   strategy_version_id,
                                   publish_status,
                                   published_at
                            FROM backtest_publish_records
                            WHERE publish_record_id = ?
                              AND (? IS NULL OR strategy_version_id = ?)
                            """,
                    query.publishId(),
                    query.strategyVersionId(),
                    query.strategyVersionId()
            ).orElseGet(() -> PublishTraceFact.missing(query.publishId()));
        }
        String evaluationId = query.evaluationId() == null ? evaluation.evaluationId() : query.evaluationId();
        if (evaluationId != null) {
            return loadPublishBySql(
                    """
                            SELECT publish_record_id,
                                   backtest_run_id,
                                   eval_report_id,
                                   strategy_version_id,
                                   publish_status,
                                   published_at
                            FROM backtest_publish_records
                            WHERE eval_report_id = ?
                              AND (? IS NULL OR strategy_version_id = ?)
                            ORDER BY COALESCE(published_at, updated_at, created_at) DESC, publish_record_id DESC
                            LIMIT 1
                            """,
                    evaluationId,
                    query.strategyVersionId(),
                    query.strategyVersionId()
            ).orElseGet(() -> PublishTraceFact.missing(null));
        }
        return loadPublishBySql(
                """
                        SELECT publish_record_id,
                               backtest_run_id,
                               eval_report_id,
                               strategy_version_id,
                               publish_status,
                               published_at
                        FROM backtest_publish_records
                        WHERE strategy_version_id = ?
                        ORDER BY COALESCE(published_at, updated_at, created_at) DESC, publish_record_id DESC
                        LIMIT 1
                        """,
                query.strategyVersionId()
        ).orElseGet(() -> PublishTraceFact.missing(null));
    }

    private Optional<PublishTraceFact> loadPublishBySql(String sql, Object... args) {
        List<PublishTraceFact> rows = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new PublishTraceFact(
                        true,
                        rs.getString("publish_record_id"),
                        rs.getString("backtest_run_id"),
                        rs.getString("eval_report_id"),
                        rs.getString("strategy_version_id"),
                        normalizeStatus(rs.getString("publish_status")),
                        toInstant(rs.getTimestamp("published_at"))
                ),
                args
        );
        return rows.stream().findFirst();
    }

    private PaperRunFact loadPaperRun(PaperShadowComparisonQuery query, PublishTraceFact publishTrace) {
        if (query.paperRunId() != null) {
            return loadPaperBySql(
                    """
                            SELECT paper_run_id,
                                   publish_id,
                                   strategy_version_id,
                                   status,
                                   trade_env,
                                   updated_at
                            FROM paper_trading_runs
                            WHERE paper_run_id = ?
                              AND (? IS NULL OR strategy_version_id = ?)
                            """,
                    query.paperRunId(),
                    query.strategyVersionId(),
                    query.strategyVersionId()
            ).orElseGet(() -> PaperRunFact.missing(query.paperRunId()));
        }
        String publishId = query.publishId() == null ? publishTrace.publishId() : query.publishId();
        if (publishId != null) {
            return loadPaperBySql(
                    """
                            SELECT paper_run_id,
                                   publish_id,
                                   strategy_version_id,
                                   status,
                                   trade_env,
                                   updated_at
                            FROM paper_trading_runs
                            WHERE publish_id = ?
                              AND trade_env = 'SIM'
                              AND (? IS NULL OR strategy_version_id = ?)
                            ORDER BY updated_at DESC, paper_run_id DESC
                            LIMIT 1
                            """,
                    publishId,
                    query.strategyVersionId(),
                    query.strategyVersionId()
            ).orElseGet(() -> PaperRunFact.missing(null));
        }
        return loadPaperBySql(
                """
                        SELECT paper_run_id,
                               publish_id,
                               strategy_version_id,
                               status,
                               trade_env,
                               updated_at
                        FROM paper_trading_runs
                        WHERE strategy_version_id = ?
                          AND trade_env = 'SIM'
                        ORDER BY updated_at DESC, paper_run_id DESC
                        LIMIT 1
                        """,
                query.strategyVersionId()
        ).orElseGet(() -> PaperRunFact.missing(null));
    }

    private Optional<PaperRunFact> loadPaperBySql(String sql, Object... args) {
        List<PaperRunFact> rows = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new PaperRunFact(
                        true,
                        rs.getString("paper_run_id"),
                        rs.getString("publish_id"),
                        rs.getString("strategy_version_id"),
                        normalizeStatus(rs.getString("status")),
                        normalizeStatus(rs.getString("trade_env")),
                        toInstant(rs.getTimestamp("updated_at"))
                ),
                args
        );
        return rows.stream().findFirst();
    }

    private ShadowRunFact loadShadowRun(PaperShadowComparisonQuery query) {
        return ShadowRunFact.notImplemented(query.shadowRunId());
    }

    private boolean matchesRequestedStrategy(String requestedStrategyId, String strategyId, String strategyCode) {
        if (requestedStrategyId == null || requestedStrategyId.isBlank()) {
            return true;
        }
        String normalized = requestedStrategyId.trim();
        return equalsIgnoreCase(normalized, strategyId) || equalsIgnoreCase(normalized, strategyCode);
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private static boolean metricsComplete(Object tradeCount, String metricsJson) {
        return tradeCount != null
                && metricsJson != null
                && !metricsJson.isBlank()
                && !"{}".equals(metricsJson.trim());
    }

    private static String normalizeStatus(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static Long nullableLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
