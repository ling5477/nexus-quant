package com.guidinglight.nexusquant.strategy.infra.jdbc;

import com.guidinglight.nexusquant.strategy.domain.StrategyRun;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunOrderSummary;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunStatus;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunTradeSummary;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunQueryRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JdbcStrategyRunQueryRepository 提供 GateE-2.3 的最小 JDBC 聚合查询。
 */
@Repository
public class JdbcStrategyRunQueryRepository implements StrategyRunQueryRepository {

    private static final RowMapper<StrategyRun> RUN_ROW_MAPPER = JdbcStrategyRunQueryRepository::mapRun;
    private static final RowMapper<StrategyRunOrderSummary> ORDER_ROW_MAPPER = JdbcStrategyRunQueryRepository::mapOrderSummary;
    private static final RowMapper<StrategyRunTradeSummary> TRADE_ROW_MAPPER = JdbcStrategyRunQueryRepository::mapTradeSummary;

    private final JdbcTemplate jdbcTemplate;

    public JdbcStrategyRunQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<StrategyRun> findRunByStrategyRunId(String strategyRunId) {
        List<StrategyRun> rows = jdbcTemplate.query(
                """
                        SELECT strategy_run_id, strategy_id, account_id, exchange_code, trade_env, trigger_type, status,
                               config_snapshot::text AS config_snapshot, request_id, started_at, finished_at, error_message, trace_id
                        FROM strategy_runs
                        WHERE strategy_run_id = ?
                        """,
                RUN_ROW_MAPPER,
                strategyRunId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public List<StrategyRun> listRecentRunsByStrategyId(String strategyId, int limit) {
        return jdbcTemplate.query(
                """
                        SELECT strategy_run_id, strategy_id, account_id, exchange_code, trade_env, trigger_type, status,
                               config_snapshot::text AS config_snapshot, request_id, started_at, finished_at, error_message, trace_id
                        FROM strategy_runs
                        WHERE strategy_id = ?
                        ORDER BY started_at DESC, strategy_run_id DESC
                        LIMIT ?
                        """,
                RUN_ROW_MAPPER,
                strategyId,
                limit
        );
    }

    @Override
    public List<StrategyRun> listRecentRunsByScheduleJobId(String scheduleJobId, int limit) {
        return jdbcTemplate.query(
                """
                        SELECT strategy_run_id, strategy_id, account_id, exchange_code, trade_env, trigger_type, status,
                               config_snapshot::text AS config_snapshot, request_id, started_at, finished_at, error_message, trace_id
                        FROM strategy_runs
                        WHERE request_id LIKE ?
                        ORDER BY started_at DESC, strategy_run_id DESC
                        LIMIT ?
                        """,
                RUN_ROW_MAPPER,
                "req-schedule-" + scheduleJobId + "-%",
                limit
        );
    }

    @Override
    public List<StrategyRunOrderSummary> listOrderSummariesByStrategyRunId(String strategyRunId) {
        return jdbcTemplate.query(
                """
                        SELECT order_id,
                               client_order_id,
                               COALESCE(exchange_order_id, external_order_id) AS exchange_order_id,
                               status,
                               symbol,
                               side,
                               type,
                               price,
                               qty
                        FROM orders
                        WHERE strategy_run_id = ?
                        ORDER BY created_at ASC, order_id ASC
                        """,
                ORDER_ROW_MAPPER,
                strategyRunId
        );
    }

    @Override
    public List<StrategyRunTradeSummary> listTradeSummariesByStrategyRunId(String strategyRunId) {
        return jdbcTemplate.query(
                """
                        SELECT trade_id,
                               exchange_trade_id,
                               COALESCE(exchange_order_id, external_order_id) AS exchange_order_id,
                               price,
                               qty,
                               ts
                        FROM trades
                        WHERE strategy_run_id = ?
                        ORDER BY ts ASC, trade_id ASC
                        """,
                TRADE_ROW_MAPPER,
                strategyRunId
        );
    }

    private static StrategyRun mapRun(ResultSet resultSet, int rowNum) throws SQLException {
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

    private static StrategyRunOrderSummary mapOrderSummary(ResultSet resultSet, int rowNum) throws SQLException {
        return new StrategyRunOrderSummary(
                resultSet.getString("order_id"),
                resultSet.getString("client_order_id"),
                resultSet.getString("exchange_order_id"),
                resultSet.getString("status"),
                resultSet.getString("symbol"),
                resultSet.getString("side"),
                resultSet.getString("type"),
                resultSet.getBigDecimal("price"),
                resultSet.getBigDecimal("qty")
        );
    }

    private static StrategyRunTradeSummary mapTradeSummary(ResultSet resultSet, int rowNum) throws SQLException {
        return new StrategyRunTradeSummary(
                resultSet.getString("trade_id"),
                resultSet.getString("exchange_trade_id"),
                resultSet.getString("exchange_order_id"),
                resultSet.getBigDecimal("price"),
                resultSet.getBigDecimal("qty"),
                resultSet.getTimestamp("ts").toInstant()
        );
    }
}


