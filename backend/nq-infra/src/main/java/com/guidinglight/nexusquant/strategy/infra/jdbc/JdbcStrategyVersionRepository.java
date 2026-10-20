package com.guidinglight.nexusquant.strategy.infra.jdbc;

import com.guidinglight.nexusquant.strategy.domain.StrategyVersion;
import com.guidinglight.nexusquant.strategy.domain.StrategyVersionListItem;
import com.guidinglight.nexusquant.strategy.domain.StrategyVersionPage;
import com.guidinglight.nexusquant.strategy.domain.StrategyVersionStatus;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyVersionListQuery;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyVersionListQueryPort;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyVersionRepository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 策略版本的 JDBC 适配器；领域不依赖持久化技术，SQL、JSONB 转换与稳定排序统一留在基础设施层。
 */
@Repository
public class JdbcStrategyVersionRepository
        implements StrategyVersionRepository, StrategyVersionListQueryPort {

    private static final String BASE_SELECT =
            """
            SELECT strategy_version_id, strategy_code, version, version_name, status,
                   param_snapshot_json::text AS param_snapshot_json,
                   config_snapshot_json::text AS config_snapshot_json,
                   source_snapshot_json::text AS source_snapshot_json,
                   checksum, created_by, created_at, updated_at
            FROM strategy_versions
            """;

    private static final RowMapper<StrategyVersion> ROW_MAPPER =
            JdbcStrategyVersionRepository::mapRow;

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
                Timestamp.from(strategyVersion.updatedAt()));
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
                strategyCode);
    }

    /**
     * 按策略版本 ID 查询详情。
     *
     * @param strategyVersionId 策略版本 ID
     * @return 版本详情
     */
    @Override
    public Optional<StrategyVersion> findById(String strategyVersionId) {
        List<StrategyVersion> rows =
                jdbcTemplate.query(
                        BASE_SELECT + " WHERE strategy_version_id = ?",
                        ROW_MAPPER,
                        strategyVersionId);
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
        Integer value =
                jdbcTemplate.queryForObject(
                        "SELECT COALESCE(MAX(version), 0) FROM strategy_versions WHERE strategy_code = ?",
                        Integer.class,
                        strategyCode);
        return value == null ? 0 : value;
    }

    @Override
    public StrategyVersionPage readPage(StrategyVersionListQuery query) {
        var where = new StringBuilder(" WHERE strategy_code = ?");
        var values = new ArrayList<Object>();
        values.add(query.strategyCode());
        if (query.strategyVersionId() != null) {
            where.append(" AND strategy_version_id = ?");
            values.add(query.strategyVersionId());
        }
        if (query.versionName() != null) {
            // 列名固定、值始终绑定；SQL通配符按普通名称文本处理。
            where.append(" AND version_name ILIKE ? ESCAPE '!'");
            values.add(
                    "%"
                            + query.versionName()
                                    .replace("!", "!!")
                                    .replace("%", "!%")
                                    .replace("_", "!_")
                            + "%");
        }
        if (query.version() != null) {
            where.append(" AND version = ?");
            values.add(query.version());
        }
        if (query.status() != null) {
            where.append(" AND status = ?");
            values.add(query.status());
        }
        long total =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM strategy_versions" + where,
                        Long.class,
                        values.toArray());
        values.add(query.limit());
        values.add(query.offset());
        // 元信息窗口不投影三份JSON；完整快照只走既有按ID读取，不增加N+1。
        var items =
                jdbcTemplate.query(
                        "SELECT strategy_version_id,strategy_code,version,version_name,status,checksum,created_by,created_at,updated_at FROM strategy_versions"
                                + where
                                + " ORDER BY version DESC, created_at DESC, strategy_version_id DESC LIMIT ? OFFSET ?",
                        (rs, row) ->
                                new StrategyVersionListItem(
                                        rs.getString("strategy_version_id"),
                                        rs.getString("strategy_code"),
                                        rs.getInt("version"),
                                        rs.getString("version_name"),
                                        StrategyVersionStatus.valueOf(rs.getString("status")),
                                        rs.getString("checksum"),
                                        rs.getString("created_by"),
                                        rs.getTimestamp("created_at").toInstant(),
                                        rs.getTimestamp("updated_at").toInstant()),
                        values.toArray());
        return new StrategyVersionPage(items, total, query.limit(), query.offset());
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
                resultSet.getTimestamp("updated_at").toInstant());
    }
}
