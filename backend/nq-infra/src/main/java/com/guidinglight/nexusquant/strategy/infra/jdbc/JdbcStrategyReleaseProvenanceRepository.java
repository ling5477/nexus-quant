package com.guidinglight.nexusquant.strategy.infra.jdbc;

import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseProvenanceFacts;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.StrategyReleaseProvenanceRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Strategy Release provenance 的 JDBC 只读 adapter。
 *
 * <p>单次主键查询复用既有 backtest publish/run/evaluation/version/dataset 事实，避免 N+1；
 * dataset identity 只读取 run 创建时固化的 {@code dataset_snapshot_json.datasetId}，不会使用可能已重绑的
 * backtest config 当前值。该 adapter 不写库、不读取 snapshot payload 内容、不创建 Shadow、不访问外部服务或 credential。
 */
@Repository
public class JdbcStrategyReleaseProvenanceRepository implements StrategyReleaseProvenanceRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcStrategyReleaseProvenanceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    /**
     * 按 publish_record_id 读取唯一 provenance chain；不存在时返回 missing fact。
     */
    @Override
    public StrategyReleaseProvenanceFacts loadByPublishRecordId(String publishRecordId) {
        List<StrategyReleaseProvenanceFacts> rows = jdbcTemplate.query(
                """
                        SELECT p.publish_record_id,
                               p.backtest_run_id,
                               p.strategy_version_id AS publish_strategy_version_id,
                               r.strategy_version_id AS run_strategy_version_id,
                               r.dataset_snapshot_json ->> 'datasetId' AS dataset_id,
                               p.eval_report_id AS evaluation_id,
                               e.backtest_run_id AS evaluation_backtest_run_id,
                               p.publish_status,
                               e.evaluation_status,
                               (sv.strategy_version_id IS NOT NULL) AS strategy_version_present,
                               (d.dataset_id IS NOT NULL) AS dataset_present,
                               p.created_at,
                               p.published_at,
                               p.artifact_storage_key,
                               p.manifest_storage_key
                        FROM backtest_publish_records p
                        LEFT JOIN backtest_runs r ON r.backtest_run_id = p.backtest_run_id
                        LEFT JOIN backtest_eval_reports e ON e.eval_report_id = p.eval_report_id
                        LEFT JOIN strategy_versions sv ON sv.strategy_version_id = p.strategy_version_id
                        LEFT JOIN marketdata_datasets d
                               ON d.dataset_id::text = NULLIF(r.dataset_snapshot_json ->> 'datasetId', '')
                        WHERE p.publish_record_id = ?
                        """,
                JdbcStrategyReleaseProvenanceRepository::mapRow,
                publishRecordId
        );
        return rows.stream().findFirst()
                .orElseGet(() -> StrategyReleaseProvenanceFacts.missing(publishRecordId));
    }

    private static StrategyReleaseProvenanceFacts mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new StrategyReleaseProvenanceFacts(
                true,
                resultSet.getString("publish_record_id"),
                resultSet.getString("backtest_run_id"),
                resultSet.getString("publish_strategy_version_id"),
                resultSet.getString("run_strategy_version_id"),
                parseUuid(resultSet.getString("dataset_id")),
                resultSet.getString("evaluation_id"),
                resultSet.getString("evaluation_backtest_run_id"),
                normalizeStatus(resultSet.getString("publish_status")),
                normalizeStatus(resultSet.getString("evaluation_status")),
                resultSet.getBoolean("strategy_version_present"),
                resultSet.getBoolean("dataset_present"),
                toInstant(resultSet.getTimestamp("created_at")),
                toInstant(resultSet.getTimestamp("published_at")),
                resultSet.getString("artifact_storage_key"),
                resultSet.getString("manifest_storage_key")
        );
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

    private static java.time.Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
