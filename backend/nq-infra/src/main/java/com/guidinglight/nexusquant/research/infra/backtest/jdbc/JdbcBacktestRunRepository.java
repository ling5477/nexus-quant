package com.guidinglight.nexusquant.research.infra.backtest.jdbc;

import com.guidinglight.nexusquant.research.domain.BacktestRun;
import com.guidinglight.nexusquant.research.domain.BacktestRunStatus;
import com.guidinglight.nexusquant.research.domain.port.BacktestRunRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JdbcBacktestRunRepository 是 backtest_runs 表的 JDBC 实现。
 */
@Repository
public class JdbcBacktestRunRepository implements BacktestRunRepository {

    private static final String BASE_SELECT = """
            SELECT backtest_run_id, backtest_config_id, research_config_id, source_strategy_id, status,
                   strategy_snapshot::text AS strategy_snapshot,
                   backtest_config_snapshot::text AS backtest_config_snapshot,
                   summary_json::text AS summary_json, requested_at, started_at, finished_at,
                   failure_code, failure_message, created_at, updated_at
            FROM backtest_runs
            """;

    private static final RowMapper<BacktestRun> ROW_MAPPER = JdbcBacktestRunRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public JdbcBacktestRunRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insert(BacktestRun backtestRun) {
        jdbcTemplate.update(
                """
                        INSERT INTO backtest_runs (
                            backtest_run_id, backtest_config_id, research_config_id, source_strategy_id, status,
                            strategy_snapshot, backtest_config_snapshot, summary_json,
                            requested_at, started_at, finished_at, failure_code, failure_message, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, CAST(? AS JSONB), CAST(? AS JSONB), CAST(? AS JSONB), ?, ?, ?, ?, ?, ?, ?)
                        """,
                backtestRun.backtestRunId(),
                backtestRun.backtestConfigId(),
                backtestRun.researchConfigId(),
                backtestRun.sourceStrategyId(),
                backtestRun.status().name(),
                backtestRun.strategySnapshot(),
                backtestRun.backtestConfigSnapshot(),
                backtestRun.summaryJson(),
                Timestamp.from(backtestRun.requestedAt()),
                toTimestamp(backtestRun.startedAt()),
                toTimestamp(backtestRun.finishedAt()),
                backtestRun.failureCode(),
                backtestRun.failureMessage(),
                Timestamp.from(backtestRun.createdAt()),
                Timestamp.from(backtestRun.updatedAt())
        );
    }

    @Override
    public Optional<BacktestRun> findByBacktestRunId(String backtestRunId) {
        List<BacktestRun> rows = jdbcTemplate.query(
                BASE_SELECT + " WHERE backtest_run_id = ?",
                ROW_MAPPER,
                backtestRunId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public List<BacktestRun> list(String researchConfigId, String backtestConfigId) {
        StringBuilder sql = new StringBuilder(BASE_SELECT);
        List<Object> args = new ArrayList<>();
        boolean hasWhere = false;
        if (researchConfigId != null) {
            sql.append(" WHERE research_config_id = ?");
            args.add(researchConfigId);
            hasWhere = true;
        }
        if (backtestConfigId != null) {
            sql.append(hasWhere ? " AND" : " WHERE").append(" backtest_config_id = ?");
            args.add(backtestConfigId);
        }
        sql.append(" ORDER BY requested_at DESC, backtest_run_id DESC");
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, args.toArray());
    }

    @Override
    public boolean updateExecution(
            String backtestRunId,
            BacktestRunStatus status,
            Instant startedAt,
            Instant finishedAt,
            String failureCode,
            String failureMessage,
            String summaryJson,
            Instant updatedAt
    ) {
        return jdbcTemplate.update(
                """
                        UPDATE backtest_runs
                        SET status = ?,
                            started_at = ?,
                            finished_at = ?,
                            failure_code = ?,
                            failure_message = ?,
                            summary_json = CASE
                                WHEN ? IS NULL THEN summary_json
                                ELSE CAST(? AS JSONB)
                            END,
                            updated_at = ?
                        WHERE backtest_run_id = ?
                        """,
                status.name(),
                toTimestamp(startedAt),
                toTimestamp(finishedAt),
                failureCode,
                failureMessage,
                summaryJson,
                summaryJson,
                Timestamp.from(updatedAt),
                backtestRunId
        ) > 0;
    }

    private static BacktestRun mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        Timestamp startedAt = resultSet.getTimestamp("started_at");
        Timestamp finishedAt = resultSet.getTimestamp("finished_at");
        return new BacktestRun(
                resultSet.getString("backtest_run_id"),
                resultSet.getString("backtest_config_id"),
                resultSet.getString("research_config_id"),
                resultSet.getString("source_strategy_id"),
                resultSet.getString("strategy_snapshot"),
                resultSet.getString("backtest_config_snapshot"),
                BacktestRunStatus.valueOf(resultSet.getString("status")),
                resultSet.getTimestamp("requested_at").toInstant(),
                startedAt == null ? null : startedAt.toInstant(),
                finishedAt == null ? null : finishedAt.toInstant(),
                resultSet.getString("failure_code"),
                resultSet.getString("failure_message"),
                resultSet.getString("summary_json"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant()
        );
    }

    private static Timestamp toTimestamp(java.time.Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
}


