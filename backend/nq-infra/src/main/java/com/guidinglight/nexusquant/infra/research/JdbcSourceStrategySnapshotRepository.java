package com.guidinglight.nexusquant.infra.research;

import com.guidinglight.nexusquant.research.model.SourceStrategySnapshot;
import com.guidinglight.nexusquant.research.port.SourceStrategySnapshotRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JdbcSourceStrategySnapshotRepository 读取 GateF 允许消费的 strategy_definitions 快照。
 * <p>
 * Why:
 * 研究域只需要复制策略定义事实，不应直接依赖 GateE 的 service 包或运行语义。
 */
@Repository
public class JdbcSourceStrategySnapshotRepository implements SourceStrategySnapshotRepository {

    private static final RowMapper<SourceStrategySnapshot> ROW_MAPPER = JdbcSourceStrategySnapshotRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public JdbcSourceStrategySnapshotRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<SourceStrategySnapshot> findByStrategyId(String strategyId) {
        List<SourceStrategySnapshot> rows = jdbcTemplate.query(
                """
                        SELECT strategy_id, strategy_code, strategy_name, strategy_type, exchange_code, account_id,
                               trade_env, enabled, config_snapshot::text AS config_snapshot, version
                        FROM strategy_definitions
                        WHERE strategy_id = ?
                        """,
                ROW_MAPPER,
                strategyId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    private static SourceStrategySnapshot mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new SourceStrategySnapshot(
                resultSet.getString("strategy_id"),
                resultSet.getString("strategy_code"),
                resultSet.getString("strategy_name"),
                resultSet.getString("strategy_type"),
                resultSet.getString("exchange_code"),
                resultSet.getLong("account_id"),
                resultSet.getString("trade_env"),
                resultSet.getBoolean("enabled"),
                resultSet.getString("config_snapshot"),
                resultSet.getInt("version")
        );
    }
}
