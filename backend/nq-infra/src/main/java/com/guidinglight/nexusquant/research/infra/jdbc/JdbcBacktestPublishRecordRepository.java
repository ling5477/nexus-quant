package com.guidinglight.nexusquant.research.infra.jdbc;

import com.guidinglight.nexusquant.research.domain.BacktestPublishRecord;
import com.guidinglight.nexusquant.research.domain.PublishStatus;
import com.guidinglight.nexusquant.research.domain.port.BacktestPublishRecordRepository;

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

    public JdbcBacktestPublishRecordRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void upsert(BacktestPublishRecord record) {
        jdbcTemplate.update(
                """
                        INSERT INTO backtest_publish_records (
                            publish_record_id, backtest_run_id, research_config_id, backtest_config_id, source_strategy_id,
                            eval_report_id, target_strategy_definition_id, publish_status, publish_name,
                            publish_snapshot_json, evaluation_summary_json, failure_code, failure_message,
                            published_at, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), CAST(? AS JSONB), ?, ?, ?, ?, ?)
                        ON CONFLICT (backtest_run_id) DO UPDATE
                        SET publish_record_id = EXCLUDED.publish_record_id,
                            research_config_id = EXCLUDED.research_config_id,
                            backtest_config_id = EXCLUDED.backtest_config_id,
                            source_strategy_id = EXCLUDED.source_strategy_id,
                            eval_report_id = EXCLUDED.eval_report_id,
                            target_strategy_definition_id = EXCLUDED.target_strategy_definition_id,
                            publish_status = EXCLUDED.publish_status,
                            publish_name = EXCLUDED.publish_name,
                            publish_snapshot_json = EXCLUDED.publish_snapshot_json,
                            evaluation_summary_json = EXCLUDED.evaluation_summary_json,
                            failure_code = EXCLUDED.failure_code,
                            failure_message = EXCLUDED.failure_message,
                            published_at = EXCLUDED.published_at,
                            updated_at = EXCLUDED.updated_at
                        """,
                record.publishRecordId(),
                record.backtestRunId(),
                record.researchConfigId(),
                record.backtestConfigId(),
                record.sourceStrategyId(),
                record.evalReportId(),
                record.targetStrategyDefinitionId(),
                record.publishStatus().name(),
                record.publishName(),
                record.publishSnapshotJson(),
                record.evaluationSummaryJson(),
                record.failureCode(),
                record.failureMessage(),
                toTimestamp(record.publishedAt()),
                Timestamp.from(record.createdAt()),
                Timestamp.from(record.updatedAt())
        );
    }

    @Override
    public Optional<BacktestPublishRecord> findByBacktestRunId(String backtestRunId) {
        List<BacktestPublishRecord> rows = jdbcTemplate.query(
                """
                        SELECT publish_record_id, backtest_run_id, research_config_id, backtest_config_id, source_strategy_id,
                               eval_report_id, target_strategy_definition_id, publish_status, publish_name,
                               publish_snapshot_json::text AS publish_snapshot_json,
                               evaluation_summary_json::text AS evaluation_summary_json,
                               failure_code, failure_message, published_at, created_at, updated_at
                        FROM backtest_publish_records
                        WHERE backtest_run_id = ?
                        """,
                ROW_MAPPER,
                backtestRunId
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
                PublishStatus.valueOf(resultSet.getString("publish_status")),
                resultSet.getString("publish_name"),
                resultSet.getString("publish_snapshot_json"),
                resultSet.getString("evaluation_summary_json"),
                resultSet.getString("failure_code"),
                resultSet.getString("failure_message"),
                publishedAt == null ? null : publishedAt.toInstant(),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant()
        );
    }

    private static Timestamp toTimestamp(java.time.Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}


