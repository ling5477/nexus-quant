package com.guidinglight.nexusquant.research.infra.backtest.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.research.domain.BacktestConfig;
import com.guidinglight.nexusquant.research.domain.port.BacktestConfigRepository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JdbcBacktestConfigRepository 是 backtest_configs 表的 JDBC 实现。
 * <p>
 * Why:
 * V28 后默认业务列表需要隐藏 ARCHIVED；按 ID 查询不加 status 过滤，
 * 保证 archived 配置仍能被历史 run、evaluation 和 publish 记录追溯。
 */
@Repository
public class JdbcBacktestConfigRepository implements BacktestConfigRepository {

    private static final String BASE_SELECT = """
            SELECT backtest_config_id, research_config_id, name, description,
                   config_json::text AS config_json, evaluation_spec_json::text AS evaluation_spec_json,
                   strategy_version_id,
                   strategy_version_snapshot_json::text AS strategy_version_snapshot_json,
                   param_snapshot_json::text AS param_snapshot_json,
                   config_snapshot_json::text AS config_snapshot_json,
                   dataset_id::text AS dataset_id, dataset_snapshot_json::text AS dataset_snapshot_json,
                   created_at, updated_at, status, archived_at, archived_by, archive_reason
            FROM backtest_configs
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcBacktestConfigRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void insert(BacktestConfig backtestConfig) {
        jdbcTemplate.update(
                """
                        INSERT INTO backtest_configs (
                            backtest_config_id, research_config_id, name, description, config_json, evaluation_spec_json,
                            param_snapshot_json, config_snapshot_json, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, CAST(? AS JSONB), CAST(? AS JSONB), CAST(? AS JSONB), CAST(? AS JSONB), ?, ?)
                        """,
                backtestConfig.backtestConfigId(),
                backtestConfig.researchConfigId(),
                backtestConfig.name(),
                backtestConfig.description(),
                backtestConfig.configSnapshot(),
                backtestConfig.evaluationSpec(),
                backtestConfig.paramSnapshotJson(),
                backtestConfig.configSnapshotJson(),
                Timestamp.from(backtestConfig.createdAt()),
                Timestamp.from(backtestConfig.updatedAt())
        );
    }

    @Override
    public Optional<BacktestConfig> findByBacktestConfigId(String backtestConfigId) {
        List<BacktestConfig> rows = jdbcTemplate.query(
                BASE_SELECT + " WHERE backtest_config_id = ?",
                rowMapper(),
                backtestConfigId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public boolean archive(
            String backtestConfigId,
            Instant archivedAt,
            String archivedBy,
            String archiveReason
    ) {
        return jdbcTemplate.update(
                """
                        UPDATE backtest_configs
                        SET status = 'ARCHIVED',
                            archived_at = ?,
                            archived_by = ?,
                            archive_reason = ?,
                            updated_at = ?
                        WHERE backtest_config_id = ?
                          AND status <> 'ARCHIVED'
                        """,
                Timestamp.from(archivedAt),
                archivedBy,
                archiveReason,
                Timestamp.from(archivedAt),
                backtestConfigId
        ) > 0;
    }

    @Override
    public List<BacktestConfig> listAll() {
        return jdbcTemplate.query(
                BASE_SELECT + " WHERE status <> 'ARCHIVED' ORDER BY created_at DESC, backtest_config_id DESC",
                rowMapper()
        );
    }

    @Override
    public List<BacktestConfig> listAllIncludingArchived() {
        return jdbcTemplate.query(
                BASE_SELECT + " ORDER BY created_at DESC, backtest_config_id DESC",
                rowMapper()
        );
    }

    @Override
    public List<BacktestConfig> listByResearchConfigId(String researchConfigId) {
        return jdbcTemplate.query(
                BASE_SELECT
                        + " WHERE research_config_id = ?"
                        + " AND status <> 'ARCHIVED'"
                        + " ORDER BY created_at DESC, backtest_config_id DESC",
                rowMapper(),
                researchConfigId
        );
    }

    @Override
    public List<BacktestConfig> listByResearchConfigIdIncludingArchived(String researchConfigId) {
        return jdbcTemplate.query(
                BASE_SELECT + " WHERE research_config_id = ? ORDER BY created_at DESC, backtest_config_id DESC",
                rowMapper(),
                researchConfigId
        );
    }

    @Override
    public boolean bindDataset(String backtestConfigId, String datasetId, String datasetSnapshotJson, Instant updatedAt) {
        return jdbcTemplate.update(
                """
                        UPDATE backtest_configs
                        SET dataset_id = CAST(? AS UUID),
                            dataset_snapshot_json = CAST(? AS JSONB),
                            updated_at = ?
                        WHERE backtest_config_id = ?
                        """,
                datasetId,
                datasetSnapshotJson,
                Timestamp.from(updatedAt),
                backtestConfigId
        ) > 0;
    }

    @Override
    public boolean bindStrategyVersion(
            String backtestConfigId,
            String strategyVersionId,
            String strategyVersionSnapshotJson,
            String paramSnapshotJson,
            Instant updatedAt
    ) {
        return jdbcTemplate.update(
                """
                        UPDATE backtest_configs
                        SET strategy_version_id = ?,
                            strategy_version_snapshot_json = CAST(? AS JSONB),
                            param_snapshot_json = CAST(? AS JSONB),
                            updated_at = ?
                        WHERE backtest_config_id = ?
                        """,
                strategyVersionId,
                strategyVersionSnapshotJson,
                paramSnapshotJson,
                Timestamp.from(updatedAt),
                backtestConfigId
        ) > 0;
    }

    private RowMapper<BacktestConfig> rowMapper() {
        return this::mapRow;
    }

    private BacktestConfig mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        JsonNode configJson = readJson(resultSet.getString("config_json"));
        return new BacktestConfig(
                resultSet.getString("backtest_config_id"),
                resultSet.getString("research_config_id"),
                resultSet.getString("name"),
                resultSet.getString("description"),
                Instant.parse(textField(configJson, "startTime")),
                Instant.parse(textField(configJson, "endTime")),
                new BigDecimal(textField(configJson, "initialCapital")),
                jsonField(configJson, "executionSpec"),
                resultSet.getString("evaluation_spec_json"),
                resultSet.getString("strategy_version_id"),
                resultSet.getString("strategy_version_snapshot_json"),
                resultSet.getString("param_snapshot_json"),
                resultSet.getString("config_snapshot_json"),
                resultSet.getString("dataset_id"),
                resultSet.getString("dataset_snapshot_json"),
                resultSet.getString("config_json"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(),
                resultSet.getString("status"),
                nullableInstant(resultSet, "archived_at"),
                resultSet.getString("archived_by"),
                resultSet.getString("archive_reason")
        );
    }

    private Instant nullableInstant(ResultSet resultSet, String columnName) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(columnName);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private JsonNode readJson(String rawJson) {
        try {
            return objectMapper.readTree(rawJson == null || rawJson.isBlank() ? "{}" : rawJson);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to parse repository json payload", ex);
        }
    }

    private String jsonField(JsonNode jsonNode, String fieldName) {
        JsonNode field = jsonNode.get(fieldName);
        return field == null || field.isNull() ? "{}" : field.toString();
    }

    private String textField(JsonNode jsonNode, String fieldName) {
        JsonNode field = jsonNode.get(fieldName);
        if (field == null || field.isNull() || field.asText().isBlank()) {
            throw new IllegalStateException("missing required field in config_json: " + fieldName);
        }
        return field.asText();
    }
}


