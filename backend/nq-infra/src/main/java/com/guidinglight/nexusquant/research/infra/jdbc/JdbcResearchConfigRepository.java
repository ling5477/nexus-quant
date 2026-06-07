package com.guidinglight.nexusquant.research.infra.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.research.domain.ResearchConfig;
import com.guidinglight.nexusquant.research.domain.port.ResearchConfigRepository;

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
 * JdbcResearchConfigRepository 是 research_configs 表的 JDBC 实现。
 * <p>
 * Why:
 * V28 后默认业务列表需要隐藏 ARCHIVED；按 ID 查询不加 status 过滤，
 * 保证 archived 配置仍可被历史回测、评估和发布记录追溯。
 */
@Repository
public class JdbcResearchConfigRepository implements ResearchConfigRepository {

    private static final String BASE_SELECT = """
            SELECT research_config_id, source_strategy_id, name, description,
                   strategy_snapshot::text AS strategy_snapshot, config_json::text AS config_json,
                   created_at, updated_at, status, archived_at, archived_by, archive_reason
            FROM research_configs
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcResearchConfigRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void insert(ResearchConfig researchConfig) {
        jdbcTemplate.update(
                """
                        INSERT INTO research_configs (
                            research_config_id, source_strategy_id, name, description, strategy_snapshot, config_json,
                            created_at, updated_at
                        ) VALUES (?, ?, ?, ?, CAST(? AS JSONB), CAST(? AS JSONB), ?, ?)
                        """,
                researchConfig.researchConfigId(),
                researchConfig.sourceStrategyId(),
                researchConfig.name(),
                researchConfig.description(),
                researchConfig.strategySnapshot(),
                buildConfigJson(researchConfig),
                Timestamp.from(researchConfig.createdAt()),
                Timestamp.from(researchConfig.updatedAt())
        );
    }

    @Override
    public Optional<ResearchConfig> findByResearchConfigId(String researchConfigId) {
        List<ResearchConfig> rows = jdbcTemplate.query(
                BASE_SELECT + " WHERE research_config_id = ?",
                rowMapper(),
                researchConfigId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public List<ResearchConfig> listAll() {
        return jdbcTemplate.query(
                BASE_SELECT + " WHERE status <> 'ARCHIVED' ORDER BY created_at DESC, research_config_id DESC",
                rowMapper()
        );
    }

    @Override
    public List<ResearchConfig> listAllIncludingArchived() {
        return jdbcTemplate.query(
                BASE_SELECT + " ORDER BY created_at DESC, research_config_id DESC",
                rowMapper()
        );
    }

    @Override
    public List<ResearchConfig> list(String sourceStrategyId, boolean includeArchived) {
        if (sourceStrategyId == null || sourceStrategyId.isBlank()) {
            return includeArchived ? listAllIncludingArchived() : listAll();
        }
        String archiveClause = includeArchived ? "" : " AND status <> 'ARCHIVED'";
        return jdbcTemplate.query(
                BASE_SELECT
                        + " WHERE source_strategy_id = ?"
                        + archiveClause
                        + " ORDER BY created_at DESC, research_config_id DESC",
                rowMapper(),
                sourceStrategyId.trim()
        );
    }

    private RowMapper<ResearchConfig> rowMapper() {
        return this::mapRow;
    }

    private ResearchConfig mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        JsonNode configJson = readJson(resultSet.getString("config_json"));
        return new ResearchConfig(
                resultSet.getString("research_config_id"),
                resultSet.getString("source_strategy_id"),
                resultSet.getString("strategy_snapshot"),
                resultSet.getString("name"),
                resultSet.getString("description"),
                jsonField(configJson, "parameterSchema"),
                jsonField(configJson, "parameterDefaults"),
                jsonField(configJson, "datasetSpec"),
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

    private String buildConfigJson(ResearchConfig researchConfig) {
        com.fasterxml.jackson.databind.node.ObjectNode configJson = objectMapper.createObjectNode();
        configJson.set("parameterSchema", readJson(researchConfig.parameterSchema()));
        configJson.set("parameterDefaults", readJson(researchConfig.parameterDefaults()));
        configJson.set("datasetSpec", readJson(researchConfig.datasetSpec()));
        return configJson.toString();
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
}


