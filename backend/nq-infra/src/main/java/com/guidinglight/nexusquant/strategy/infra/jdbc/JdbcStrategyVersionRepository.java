package com.guidinglight.nexusquant.strategy.infra.jdbc;

import com.guidinglight.nexusquant.strategy.domain.StrategyVersion;
import com.guidinglight.nexusquant.strategy.domain.StrategyVersionStatus;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyVersionRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JdbcStrategyVersionRepository 是 `strategy_versions` 表的 JDBC adapter。
 *
 * Why:
 * GateI-1 要求 `nq-core` 不依赖 JDBC，因此所有 SQL、JSONB cast 和查询排序都留在 infra 层。
 */
@Repository
public class JdbcStrategyVersionRepository implements StrategyVersionRepository {

    private static final String BASE_SELECT = """
            SELECT strategy_version_id, strategy_code, version, version_name, status,
                   param_snapshot_json::text AS param_snapshot_json,
                   config_snapshot_json::text AS config_snapshot_json,
                   source_snapshot_json::text AS source_snapshot_json,
                   checksum, created_by, created_at, updated_at
            FROM strategy_versions
            """;

    private static final RowMapper<StrategyVersion> ROW_MAPPER = JdbcStrategyVersionRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public JdbcStrategyVersionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 插入策略版本。
     *
     * @param strategyVersion 已由 application service 校验过的版本事实
     */
    @Override
    public void insert(StrategyVersion strategyVersion) {
        jdbcTemplate.update(
                """
                        INSERT INTO strategy_versions (
                            strategy_version_id, strategy_code, version, version_name, status,
                            param_snapshot_json, config_snapshot_json, source_snapshot_json,
                            checksum, created_by, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, CAST(? AS JSONB), CAST(? AS JSONB), CAST(? AS JSONB), ?, ?, ?, ?)
                        """,
                strategyVersion.strategyVersionId(),
                strategyVersion.strategyCode(),
                strategyVersion.version(),
                strategyVersion.versionName(),
                strategyVersion.status().name(),
                strategyVersion.paramSnapshotJson(),
                strategyVersion.configSnapshotJson(),
                strategyVersion.sourceSnapshotJson(),
                strategyVersion.checksum(),
                strategyVersion.createdBy(),
                Timestamp.from(strategyVersion.createdAt()),
                Timestamp.from(strategyVersion.updatedAt())
        );
    }

    /**
     * 按策略编码查询版本列表。
     *
     * @param strategyCode 策略编码
     * @return 版本号倒序列表
     */
    @Override
    public List<StrategyVersion> listByStrategyCode(String strategyCode) {
        return jdbcTemplate.query(
                BASE_SELECT + " WHERE strategy_code = ? ORDER BY version DESC, created_at DESC",
                ROW_MAPPER,
                strategyCode
        );
    }

    /**
     * 按策略版本 ID 查询详情。
     *
     * @param strategyVersionId 策略版本 ID
     * @return 版本详情
     */
    @Override
    public Optional<StrategyVersion> findById(String strategyVersionId) {
        List<StrategyVersion> rows = jdbcTemplate.query(
                BASE_SELECT + " WHERE strategy_version_id = ?",
                ROW_MAPPER,
                strategyVersionId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    /**
     * 查询策略当前最大版本号。
     *
     * @param strategyCode 策略编码
     * @return 最大版本号；无历史版本时返回 0
     */
    @Override
    public int maxVersion(String strategyCode) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(version), 0) FROM strategy_versions WHERE strategy_code = ?",
                Integer.class,
                strategyCode
        );
        return value == null ? 0 : value;
    }

    private static StrategyVersion mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new StrategyVersion(
                resultSet.getString("strategy_version_id"),
                resultSet.getString("strategy_code"),
                resultSet.getInt("version"),
                resultSet.getString("version_name"),
                StrategyVersionStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("param_snapshot_json"),
                resultSet.getString("config_snapshot_json"),
                resultSet.getString("source_snapshot_json"),
                resultSet.getString("checksum"),
                resultSet.getString("created_by"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant()
        );
    }
}
