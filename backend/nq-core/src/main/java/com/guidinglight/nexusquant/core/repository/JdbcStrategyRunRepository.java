package com.guidinglight.nexusquant.core.repository;

import com.guidinglight.nexusquant.core.model.StrategyRun;
import com.guidinglight.nexusquant.core.model.StrategyRunStatus;
import com.guidinglight.nexusquant.core.service.port.StrategyRunRepository;

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
 * JdbcStrategyRunRepository 是 strategy_runs 表的最小 JDBC 实现。
 */
@Repository
public class JdbcStrategyRunRepository implements StrategyRunRepository {

    private static final RowMapper<StrategyRun> ROW_MAPPER = JdbcStrategyRunRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public JdbcStrategyRunRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insert(StrategyRun strategyRun) {
        jdbcTemplate.update(
                """
                        INSERT INTO strategy_runs (
                            strategy_run_id, strategy_id, account_id, status, trigger_type, exchange_code, trade_env,
                            config_snapshot, request_id, started_at, finished_at, error_message, trace_id
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB), ?, ?, ?, ?, ?)
                        """,
                strategyRun.strategyRunId(),
                strategyRun.strategyId(),
                strategyRun.accountId(),
                strategyRun.status().name(),
                strategyRun.triggerType(),
                strategyRun.exchangeCode(),
                strategyRun.tradeEnv(),
                strategyRun.configSnapshot(),
                strategyRun.requestId(),
                Timestamp.from(strategyRun.startedAt()),
                strategyRun.finishedAt() == null ? null : Timestamp.from(strategyRun.finishedAt()),
                strategyRun.errorMessage(),
                strategyRun.traceId()
        );
    }

    @Override
    public Optional<StrategyRun> findByStrategyRunId(String strategyRunId) {
        List<StrategyRun> rows = jdbcTemplate.query(
                """
                        SELECT strategy_run_id, strategy_id, account_id, exchange_code, trade_env, trigger_type, status,
                               config_snapshot::text AS config_snapshot, request_id, started_at, finished_at, error_message, trace_id
                        FROM strategy_runs
                        WHERE strategy_run_id = ?
                        """,
                ROW_MAPPER,
                strategyRunId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public boolean updateStatus(String strategyRunId, StrategyRunStatus status, Instant finishedAt, String errorMessage) {
        return jdbcTemplate.update(
                "UPDATE strategy_runs SET status = ?, finished_at = ?, error_message = ? WHERE strategy_run_id = ?",
                status.name(),
                finishedAt == null ? null : Timestamp.from(finishedAt),
                errorMessage,
                strategyRunId
        ) > 0;
    }

    private static StrategyRun mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        Timestamp finishedAt = resultSet.getTimestamp("finished_at");
        return new StrategyRun(
                resultSet.getString("strategy_run_id"),
                resultSet.getString("strategy_id"),
                resultSet.getLong("account_id"),
                resultSet.getString("exchange_code"),
                resultSet.getString("trade_env"),
                resultSet.getString("trigger_type"),
                StrategyRunStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("config_snapshot"),
                resultSet.getString("request_id"),
                resultSet.getTimestamp("started_at").toInstant(),
                finishedAt == null ? null : finishedAt.toInstant(),
                resultSet.getString("error_message"),
                resultSet.getString("trace_id")
        );
    }
}
