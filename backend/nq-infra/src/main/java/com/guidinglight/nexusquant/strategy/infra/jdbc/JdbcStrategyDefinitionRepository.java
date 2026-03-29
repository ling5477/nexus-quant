package com.guidinglight.nexusquant.strategy.infra.jdbc;

import com.guidinglight.nexusquant.strategy.domain.StrategyDefinition;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyDefinitionRepository;

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
 * JdbcStrategyDefinitionRepository 是 strategy_definitions 表的 JDBC 实现。
 */
@Repository
public class JdbcStrategyDefinitionRepository implements StrategyDefinitionRepository {

    private static final String BASE_SELECT = """
            SELECT strategy_id, strategy_code, strategy_name, strategy_type, exchange_code, account_id, trade_env,
                   enabled, config_snapshot::text AS config_snapshot, version, created_at, updated_at
            FROM strategy_definitions
            """;

    private static final RowMapper<StrategyDefinition> ROW_MAPPER = JdbcStrategyDefinitionRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public JdbcStrategyDefinitionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insert(StrategyDefinition definition) {
        jdbcTemplate.update(
                """
                        INSERT INTO strategy_definitions (
                            strategy_id, strategy_code, strategy_name, strategy_type, exchange_code, account_id, trade_env,
                            enabled, config_snapshot, version, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), ?, ?, ?)
                        """,
                definition.strategyId(),
                definition.strategyCode(),
                definition.strategyName(),
                definition.strategyType(),
                definition.exchangeCode(),
                definition.accountId(),
                definition.tradeEnv(),
                definition.enabled(),
                definition.configSnapshot(),
                definition.version(),
                Timestamp.from(definition.createdAt()),
                Timestamp.from(definition.updatedAt())
        );
    }

    @Override
    public Optional<StrategyDefinition> findByStrategyId(String strategyId) {
        List<StrategyDefinition> rows = jdbcTemplate.query(
                BASE_SELECT + " WHERE strategy_id = ?",
                ROW_MAPPER,
                strategyId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public Optional<StrategyDefinition> findByStrategyCode(String strategyCode) {
        List<StrategyDefinition> rows = jdbcTemplate.query(
                BASE_SELECT + " WHERE strategy_code = ?",
                ROW_MAPPER,
                strategyCode
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public List<StrategyDefinition> listAll() {
        return jdbcTemplate.query(
                BASE_SELECT + " ORDER BY created_at DESC, strategy_id DESC",
                ROW_MAPPER
        );
    }

    @Override
    public boolean updateEnabled(String strategyId, boolean enabled, Instant updatedAt) {
        return jdbcTemplate.update(
                "UPDATE strategy_definitions SET enabled = ?, updated_at = ? WHERE strategy_id = ?",
                enabled,
                Timestamp.from(updatedAt),
                strategyId
        ) > 0;
    }

    private static StrategyDefinition mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new StrategyDefinition(
                resultSet.getString("strategy_id"),
                resultSet.getString("strategy_code"),
                resultSet.getString("strategy_name"),
                resultSet.getString("strategy_type"),
                resultSet.getString("exchange_code"),
                resultSet.getLong("account_id"),
                resultSet.getString("trade_env"),
                resultSet.getBoolean("enabled"),
                resultSet.getString("config_snapshot"),
                resultSet.getInt("version"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant()
        );
    }
}


