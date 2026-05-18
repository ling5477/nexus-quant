package com.guidinglight.nexusquant.research.infra.jdbc;

import com.guidinglight.nexusquant.research.domain.StrategyVersionSnapshotView;
import com.guidinglight.nexusquant.research.domain.port.StrategyVersionSnapshotQueryPort;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JdbcStrategyVersionSnapshotQueryPort 从 `strategy_versions` 表读取发布所需快照。
 *
 * Why:
 * GateI-1 发布链路需要把 version snapshot 固化到 `backtest_publish_records`，
 * 但 research 模块不直接依赖 core 策略版本服务，因此由 infra adapter 提供只读桥接。
 */
@Repository
public class JdbcStrategyVersionSnapshotQueryPort implements StrategyVersionSnapshotQueryPort {

    private static final RowMapper<StrategyVersionSnapshotView> ROW_MAPPER = JdbcStrategyVersionSnapshotQueryPort::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public JdbcStrategyVersionSnapshotQueryPort(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 按策略版本 ID 读取发布快照。
     *
     * @param strategyVersionId 策略版本 ID
     * @return 发布快照；不存在时为空
     */
    @Override
    public Optional<StrategyVersionSnapshotView> findById(String strategyVersionId) {
        List<StrategyVersionSnapshotView> rows = jdbcTemplate.query(
                """
                        SELECT strategy_version_id, strategy_code, version, version_name, status,
                               param_snapshot_json::text AS param_snapshot_json,
                               config_snapshot_json::text AS config_snapshot_json,
                               source_snapshot_json::text AS source_snapshot_json,
                               checksum
                        FROM strategy_versions
                        WHERE strategy_version_id = ?
                        """,
                ROW_MAPPER,
                strategyVersionId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    private static StrategyVersionSnapshotView mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new StrategyVersionSnapshotView(
                resultSet.getString("strategy_version_id"),
                resultSet.getString("strategy_code"),
                resultSet.getInt("version"),
                resultSet.getString("version_name"),
                resultSet.getString("status"),
                resultSet.getString("param_snapshot_json"),
                resultSet.getString("config_snapshot_json"),
                resultSet.getString("source_snapshot_json"),
                resultSet.getString("checksum")
        );
    }
}
