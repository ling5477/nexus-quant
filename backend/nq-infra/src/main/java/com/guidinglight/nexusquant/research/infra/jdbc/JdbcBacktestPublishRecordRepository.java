package com.guidinglight.nexusquant.research.infra.jdbc;

import com.guidinglight.nexusquant.research.domain.BacktestPublishRecord;
import com.guidinglight.nexusquant.research.domain.PublishStatus;
import com.guidinglight.nexusquant.research.domain.ResearchResultPage;
import com.guidinglight.nexusquant.research.domain.port.BacktestPublishListQueryPort;
import com.guidinglight.nexusquant.research.domain.port.BacktestPublishRecordRepository;
import com.guidinglight.nexusquant.research.domain.port.PublishListQuery;
import com.guidinglight.nexusquant.research.domain.port.ResearchFeeSourcePort;
import com.guidinglight.nexusquant.research.infra.ResearchFeeReadScope;
import com.guidinglight.nexusquant.strategy.strategyrelease.application.port.AdmissionMutationCoordinator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * JdbcBacktestPublishRecordRepository 是 backtest_publish_records 表的 JDBC 实现。
 */
@Repository
public class JdbcBacktestPublishRecordRepository
        implements BacktestPublishRecordRepository, BacktestPublishListQueryPort {

    private static final RowMapper<BacktestPublishRecord> ROW_MAPPER =
            JdbcBacktestPublishRecordRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;
    private ResearchFeeReadScope feeScope =
            new ResearchFeeReadScope(ResearchFeeSourcePort.disabled());

    /** 手工构造默认拒绝私有来源；正式装配在SQL中统一隔离详情及列表。 */
    @Autowired
    private void bindFeeReadScope(ResearchFeeReadScope scope) {
        this.feeScope = scope;
    }

    private final AdmissionMutationCoordinator admissionMutationCoordinator;

    public JdbcBacktestPublishRecordRepository(
            JdbcTemplate jdbcTemplate, AdmissionMutationCoordinator admissionMutationCoordinator) {
        this.jdbcTemplate = jdbcTemplate;
        this.admissionMutationCoordinator = admissionMutationCoordinator;
    }

    @Override
    public void upsert(BacktestPublishRecord record) {
        List<String> existingPublishIds =
                jdbcTemplate.query(
                        "SELECT publish_record_id FROM (SELECT tr.* FROM backtest_publish_records tr WHERE nq_entity_tenant('backtest',tr.backtest_run_id)=nq_current_tenant_id()) backtest_publish_records WHERE backtest_run_id = ?",
                        (resultSet, rowNum) -> resultSet.getString("publish_record_id"),
                        record.backtestRunId());
        int updated =
                admissionMutationCoordinator.withLockedAdmissionStates(
                        existingPublishIds,
                        () ->
                                jdbcTemplate.update(
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
                                        record.manifestStorageKey()));
        if (updated != 1) {
            throw new IllegalStateException("backtest publish artifact locator conflict");
        }
    }

    /** 显式owner不能退回线程上下文；只载入窗口实际引用且费用来源可见的发布，不产生逐运行查询。 */
    @Override
    public List<BacktestPublishRecord> listVisibleByIds(long owner, Collection<String> publishIds) {
        if (owner <= 0) throw new IllegalArgumentException("INVALID_LEGACY_ANALYSIS_OWNER");
        if (publishIds == null || publishIds.isEmpty()) return List.of();
        var ids = List.copyOf(new LinkedHashSet<>(publishIds));
        if (ids.size() > 200 || ids.stream().anyMatch(id -> id.isBlank() || id.length() > 128))
            throw new IllegalArgumentException("INVALID_ANALYSIS_PUBLISH_WINDOW");
        var reader = new JdbcTemplate(jdbcTemplate.getDataSource());
        reader.setQueryTimeout(5);
        return reader.query(
                "SELECT * FROM (SELECT tr.* FROM backtest_publish_records tr WHERE nq_entity_tenant('backtest',tr.backtest_run_id)=nq_current_tenant_id()) backtest_publish_records WHERE publish_record_id IN ("
                        + String.join(",", Collections.nCopies(ids.size(), "?"))
                        + ") AND "
                        + feeScope.backtest("backtest_publish_records.backtest_run_id", owner)
                        + " ORDER BY publish_record_id",
                ROW_MAPPER,
                ids.toArray());
    }

    @Override
    public Optional<BacktestPublishRecord> findByBacktestRunId(String backtestRunId) {
        List<BacktestPublishRecord> rows =
                jdbcTemplate.query(
                        """
                        SELECT publish_record_id, backtest_run_id, research_config_id, backtest_config_id, source_strategy_id,
                               eval_report_id, target_strategy_definition_id, strategy_version_id, publish_status, publish_name,
                               publish_snapshot_json::text AS publish_snapshot_json,
                               version_snapshot_json::text AS version_snapshot_json,
                               evaluation_summary_json::text AS evaluation_summary_json,
                               failure_code, failure_message, published_at, created_at, updated_at,
                               artifact_storage_key, manifest_storage_key
                        FROM (SELECT tr.* FROM backtest_publish_records tr WHERE nq_entity_tenant('backtest',tr.backtest_run_id)=nq_current_tenant_id()) backtest_publish_records
                        WHERE backtest_run_id = ?
                        """
                                + " AND "
                                + feeScope.backtest("backtest_publish_records.backtest_run_id"),
                        ROW_MAPPER,
                        backtestRunId);
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
                        FROM (SELECT tr.* FROM backtest_publish_records tr WHERE nq_entity_tenant('backtest',tr.backtest_run_id)=nq_current_tenant_id()) backtest_publish_records
                        """
                        + " WHERE "
                        + feeScope.backtest("backtest_publish_records.backtest_run_id")
                        + " ORDER BY updated_at DESC, publish_record_id DESC",
                ROW_MAPPER);
    }

    @Override
    public Optional<BacktestPublishRecord> findByPublishRecordId(String publishRecordId) {
        List<BacktestPublishRecord> rows =
                jdbcTemplate.query(
                        """
                        SELECT publish_record_id, backtest_run_id, research_config_id, backtest_config_id, source_strategy_id,
                               eval_report_id, target_strategy_definition_id, strategy_version_id, publish_status, publish_name,
                               publish_snapshot_json::text AS publish_snapshot_json,
                               version_snapshot_json::text AS version_snapshot_json,
                               evaluation_summary_json::text AS evaluation_summary_json,
                               failure_code, failure_message, published_at, created_at, updated_at,
                               artifact_storage_key, manifest_storage_key
                        FROM (SELECT tr.* FROM backtest_publish_records tr WHERE nq_entity_tenant('backtest',tr.backtest_run_id)=nq_current_tenant_id()) backtest_publish_records
                        WHERE publish_record_id = ?
                        """
                                + " AND "
                                + feeScope.backtest("backtest_publish_records.backtest_run_id"),
                        ROW_MAPPER,
                        publishRecordId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    /** 通过正式来源运行限制COUNT和页记录，已冻结发布快照不会因分页重新生成。 */
    @Override
    public ResearchResultPage<BacktestPublishRecord> readPage(PublishListQuery query) {
        String from = " FROM (SELECT tr.* FROM backtest_publish_records tr WHERE nq_entity_tenant('backtest',tr.backtest_run_id)=nq_current_tenant_id()) p";
        StringBuilder where =
                new StringBuilder(" WHERE ").append(feeScope.backtest("p.backtest_run_id"));
        List<Object> args = new ArrayList<>();
        if (query.researchConfigId() != null) {
            where.append(" AND p.research_config_id ILIKE ? ESCAPE '!'");
            args.add(literalContains(query.researchConfigId()));
        }
        if (query.backtestConfigId() != null) {
            where.append(" AND p.backtest_config_id ILIKE ? ESCAPE '!'");
            args.add(literalContains(query.backtestConfigId()));
        }
        if (query.sourceStrategyId() != null) {
            where.append(" AND p.source_strategy_id ILIKE ? ESCAPE '!'");
            args.add(literalContains(query.sourceStrategyId()));
        }
        if (query.strategyVersionId() != null) {
            where.append(" AND p.strategy_version_id = ?");
            args.add(query.strategyVersionId());
        }
        if (query.publishStatus() != null) {
            where.append(" AND p.publish_status = ?");
            args.add(query.publishStatus());
        }
        Long total =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*)" + from + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.limit());
        pageArgs.add(query.offset());
        List<BacktestPublishRecord> items =
                jdbcTemplate.query(
                        """
                SELECT p.publish_record_id, p.backtest_run_id, p.research_config_id, p.backtest_config_id, p.source_strategy_id,
                       p.eval_report_id, p.target_strategy_definition_id, p.strategy_version_id, p.publish_status, p.publish_name,
                       p.publish_snapshot_json::text AS publish_snapshot_json,
                       p.version_snapshot_json::text AS version_snapshot_json,
                       p.evaluation_summary_json::text AS evaluation_summary_json,
                       p.failure_code, p.failure_message, p.published_at, p.created_at, p.updated_at,
                       p.artifact_storage_key, p.manifest_storage_key
                """
                                + from
                                + where
                                + " ORDER BY p.updated_at DESC, p.publish_record_id DESC LIMIT ? OFFSET ?",
                        ROW_MAPPER,
                        pageArgs.toArray());
        return new ResearchResultPage<>(
                items, total == null ? 0 : total, query.limit(), query.offset());
    }

    private static String literalContains(String value) {
        return "%" + value.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    private static BacktestPublishRecord mapRow(ResultSet resultSet, int rowNum)
            throws SQLException {
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
                resultSet.getString("manifest_storage_key"));
    }

    private static Timestamp toTimestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}
