package com.guidinglight.nexusquant.research.infra.jdbc;

import com.guidinglight.nexusquant.research.domain.BacktestPublishRecord;
import com.guidinglight.nexusquant.research.domain.PublishStatus;
import com.guidinglight.nexusquant.research.domain.port.BacktestPublishRecordRepository;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.AdmissionMutationCoordinator;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JdbcBacktestPublishRecordRepository 是 backtest_publish_records 表的 JDBC 实现。
 */
@Repository
public class JdbcBacktestPublishRecordRepository implements BacktestPublishRecordRepository {

    private static final RowMapper<BacktestPublishRecord> ROW_MAPPER = JdbcBacktestPublishRecordRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;
    private final AdmissionMutationCoordinator admissionMutationCoordinator;

    public JdbcBacktestPublishRecordRepository(
            JdbcTemplate jdbcTemplate,
            AdmissionMutationCoordinator admissionMutationCoordinator
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.admissionMutationCoordinator = admissionMutationCoordinator;
    }

    @Override
    public void upsert(BacktestPublishRecord record) {
        List<String> existingPublishIds = jdbcTemplate.query(
                "SELECT publish_record_id FROM backtest_publish_records WHERE backtest_run_id = ?",
                (resultSet, rowNum) -> resultSet.getString("publish_record_id"),
                record.backtestRunId()
        );
        int updated = admissionMutationCoordinator.withLockedAdmissionStates(existingPublishIds, () -> jdbcTemplate.update(
                """
                        INSERT INTO backtest_publish_records (
                            publish_record_id, backtest_run_id, research_config_id, backtest_config_id, source_strategy_id,
                            eval_report_id, target_strategy_definition_id, strategy_version_id, publish_status, publish_name,
                            publish_snapshot_json, version_snapshot_json, evaluation_summary_json, failure_code, failure_message,
                            published_at, created_at, updated_at, artifact_storage_key, manifest_storage_key
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), CAST(? AS JSONB), CAST(? AS JSONB), ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (backtest_run_id) DO UPDATE
                        SET publish_record_id = EXCLUDED.publish_record_id,
                            research_config_id = EXCLUDED.research_config_id,
                            backtest_config_id = EXCLUDED.backtest_config_id,
                            source_strategy_id = EXCLUDED.source_strategy_id,
                            eval_report_id = EXCLUDED.eval_report_id,
                            target_strategy_definition_id = EXCLUDED.target_strategy_definition_id,
                            strategy_version_id = EXCLUDED.strategy_version_id,
                            publish_status = EXCLUDED.publish_status,
                            publish_name = EXCLUDED.publish_name,
                            publish_snapshot_json = EXCLUDED.publish_snapshot_json,
                            version_snapshot_json = EXCLUDED.version_snapshot_json,
                            evaluation_summary_json = EXCLUDED.evaluation_summary_json,
                            failure_code = EXCLUDED.failure_code,
                            failure_message = EXCLUDED.failure_message,
                            published_at = EXCLUDED.published_at,
                            updated_at = EXCLUDED.updated_at,
                            artifact_storage_key = CASE
                                WHEN backtest_publish_records.artifact_storage_key IS NULL
                                    THEN EXCLUDED.artifact_storage_key
                                ELSE backtest_publish_records.artifact_storage_key
                            END,
                            manifest_storage_key = CASE
                                WHEN backtest_publish_records.manifest_storage_key IS NULL
                                    THEN EXCLUDED.manifest_storage_key
                                ELSE backtest_publish_records.manifest_storage_key
                            END
                        WHERE (
                            backtest_publish_records.artifact_storage_key IS NULL
                            AND backtest_publish_records.manifest_storage_key IS NULL
                        ) OR (
                            backtest_publish_records.artifact_storage_key IS NOT DISTINCT FROM EXCLUDED.artifact_storage_key
                            AND backtest_publish_records.manifest_storage_key IS NOT DISTINCT FROM EXCLUDED.manifest_storage_key
                        )
                        """,
                record.publishRecordId(),
                record.backtestRunId(),
                record.researchConfigId(),
                record.backtestConfigId(),
                record.sourceStrategyId(),
                record.evalReportId(),
                record.targetStrategyDefinitionId(),
                record.strategyVersionId(),
                record.publishStatus().name(),
                record.publishName(),
                record.publishSnapshotJson(),
                record.versionSnapshotJson(),
                record.evaluationSummaryJson(),
                record.failureCode(),
                record.failureMessage(),
                toTimestamp(record.publishedAt()),
                Timestamp.from(record.createdAt()),
                Timestamp.from(record.updatedAt()),
                record.artifactStorageKey(),
                record.manifestStorageKey()
        ));
        if (updated != 1) {
            throw new IllegalStateException("backtest publish artifact locator conflict");
        }
    }

    @Override
    public Optional<BacktestPublishRecord> findByBacktestRunId(String backtestRunId) {
        List<BacktestPublishRecord> rows = jdbcTemplate.query(
                """
                        SELECT publish_record_id, backtest_run_id, research_config_id, backtest_config_id, source_strategy_id,
                               eval_report_id, target_strategy_definition_id, strategy_version_id, publish_status, publish_name,
                               publish_snapshot_json::text AS publish_snapshot_json,
                               version_snapshot_json::text AS version_snapshot_json,
                               evaluation_summary_json::text AS evaluation_summary_json,
                               failure_code, failure_message, published_at, created_at, updated_at,
                               artifact_storage_key, manifest_storage_key
                        FROM backtest_publish_records
                        WHERE backtest_run_id = ?
                        """,
                ROW_MAPPER,
                backtestRunId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public List<BacktestPublishRecord> listAll() {
        return jdbcTemplate.query(
                """
                        SELECT publish_record_id, backtest_run_id, research_config_id, backtest_config_id, source_strategy_id,
                               eval_report_id, target_strategy_definition_id, strategy_version_id, publish_status, publish_name,
                               publish_snapshot_json::text AS publish_snapshot_json,
                               version_snapshot_json::text AS version_snapshot_json,
                               evaluation_summary_json::text AS evaluation_summary_json,
                               failure_code, failure_message, published_at, created_at, updated_at,
                               artifact_storage_key, manifest_storage_key
                        FROM backtest_publish_records
                        ORDER BY updated_at DESC, publish_record_id DESC
                        """,
                ROW_MAPPER
        );
    }

    @Override
    public Optional<BacktestPublishRecord> findByPublishRecordId(String publishRecordId) {
        List<BacktestPublishRecord> rows = jdbcTemplate.query(
                """
                        SELECT publish_record_id, backtest_run_id, research_config_id, backtest_config_id, source_strategy_id,
                               eval_report_id, target_strategy_definition_id, strategy_version_id, publish_status, publish_name,
                               publish_snapshot_json::text AS publish_snapshot_json,
                               version_snapshot_json::text AS version_snapshot_json,
                               evaluation_summary_json::text AS evaluation_summary_json,
                               failure_code, failure_message, published_at, created_at, updated_at,
                               artifact_storage_key, manifest_storage_key
                        FROM backtest_publish_records
                        WHERE publish_record_id = ?
                        """,
                ROW_MAPPER,
                publishRecordId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    private static BacktestPublishRecord mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        Timestamp publishedAt = resultSet.getTimestamp("published_at");
        return new BacktestPublishRecord(
                resultSet.getString("publish_record_id"),
                resultSet.getString("backtest_run_id"),
                resultSet.getString("research_config_id"),
                resultSet.getString("backtest_config_id"),
                resultSet.getString("source_strategy_id"),
                resultSet.getString("eval_report_id"),
                resultSet.getString("target_strategy_definition_id"),
                resultSet.getString("strategy_version_id"),
                PublishStatus.valueOf(resultSet.getString("publish_status")),
                resultSet.getString("publish_name"),
                resultSet.getString("publish_snapshot_json"),
                resultSet.getString("version_snapshot_json"),
                resultSet.getString("evaluation_summary_json"),
                resultSet.getString("failure_code"),
                resultSet.getString("failure_message"),
                publishedAt == null ? null : publishedAt.toInstant(),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(),
                resultSet.getString("artifact_storage_key"),
                resultSet.getString("manifest_storage_key")
        );
    }

    private static Timestamp toTimestamp(java.time.Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}

