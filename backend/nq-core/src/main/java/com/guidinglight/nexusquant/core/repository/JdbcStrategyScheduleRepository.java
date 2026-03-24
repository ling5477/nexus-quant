package com.guidinglight.nexusquant.core.repository;

import com.guidinglight.nexusquant.core.model.StrategySchedule;
import com.guidinglight.nexusquant.core.service.port.StrategyScheduleRepository;

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
 * JdbcStrategyScheduleRepository 是 strategy_schedules 表的最小 JDBC 实现。
 */
@Repository
public class JdbcStrategyScheduleRepository implements StrategyScheduleRepository {

    private static final RowMapper<StrategySchedule> ROW_MAPPER = JdbcStrategyScheduleRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public JdbcStrategyScheduleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insert(StrategySchedule schedule) {
        jdbcTemplate.update(
                """
                        INSERT INTO strategy_schedules (
                            schedule_job_id, strategy_id, schedule_type, cron_expr, timezone, enabled, window_config,
                            dedup_scope, exchange_code, account_id, trade_env, last_triggered_at, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, CAST(? AS JSONB), ?, ?, ?, ?, ?, ?, ?)
                        """,
                schedule.scheduleJobId(),
                schedule.strategyId(),
                schedule.scheduleType(),
                schedule.cronExpr(),
                schedule.timezone(),
                schedule.enabled(),
                schedule.windowConfig(),
                schedule.dedupScope(),
                schedule.exchangeCode(),
                schedule.accountId(),
                schedule.tradeEnv(),
                schedule.lastTriggeredAt() == null ? null : Timestamp.from(schedule.lastTriggeredAt()),
                Timestamp.from(schedule.createdAt()),
                Timestamp.from(schedule.updatedAt())
        );
    }

    @Override
    public Optional<StrategySchedule> findByScheduleJobId(String scheduleJobId) {
        List<StrategySchedule> rows = jdbcTemplate.query(
                """
                        SELECT schedule_job_id, strategy_id, schedule_type, cron_expr, timezone, enabled,
                               window_config::text AS window_config, dedup_scope, exchange_code, account_id, trade_env,
                               last_triggered_at, created_at, updated_at
                        FROM strategy_schedules
                        WHERE schedule_job_id = ?
                        """,
                ROW_MAPPER,
                scheduleJobId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public List<StrategySchedule> listByStrategyId(String strategyId) {
        return jdbcTemplate.query(
                """
                        SELECT schedule_job_id, strategy_id, schedule_type, cron_expr, timezone, enabled,
                               window_config::text AS window_config, dedup_scope, exchange_code, account_id, trade_env,
                               last_triggered_at, created_at, updated_at
                        FROM strategy_schedules
                        WHERE strategy_id = ?
                        ORDER BY created_at DESC, schedule_job_id DESC
                        """,
                ROW_MAPPER,
                strategyId
        );
    }

    @Override
    public List<StrategySchedule> listAll() {
        return jdbcTemplate.query(
                """
                        SELECT schedule_job_id, strategy_id, schedule_type, cron_expr, timezone, enabled,
                               window_config::text AS window_config, dedup_scope, exchange_code, account_id, trade_env,
                               last_triggered_at, created_at, updated_at
                        FROM strategy_schedules
                        ORDER BY updated_at ASC, schedule_job_id ASC
                        """,
                ROW_MAPPER
        );
    }

    @Override
    public List<StrategySchedule> listEnabledSchedules() {
        return jdbcTemplate.query(
                """
                        SELECT schedule_job_id, strategy_id, schedule_type, cron_expr, timezone, enabled,
                               window_config::text AS window_config, dedup_scope, exchange_code, account_id, trade_env,
                               last_triggered_at, created_at, updated_at
                        FROM strategy_schedules
                        WHERE enabled = TRUE
                        ORDER BY updated_at ASC, schedule_job_id ASC
                        """,
                ROW_MAPPER
        );
    }

    @Override
    public boolean updateEnabled(String scheduleJobId, boolean enabled, Instant updatedAt) {
        return jdbcTemplate.update(
                "UPDATE strategy_schedules SET enabled = ?, updated_at = ? WHERE schedule_job_id = ?",
                enabled,
                Timestamp.from(updatedAt),
                scheduleJobId
        ) > 0;
    }

    @Override
    public boolean updateLastTriggeredAt(String scheduleJobId, Instant lastTriggeredAt, Instant updatedAt) {
        return jdbcTemplate.update(
                "UPDATE strategy_schedules SET last_triggered_at = ?, updated_at = ? WHERE schedule_job_id = ?",
                Timestamp.from(lastTriggeredAt),
                Timestamp.from(updatedAt),
                scheduleJobId
        ) > 0;
    }

    private static StrategySchedule mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        Timestamp lastTriggeredAt = resultSet.getTimestamp("last_triggered_at");
        return new StrategySchedule(
                resultSet.getString("schedule_job_id"),
                resultSet.getString("strategy_id"),
                resultSet.getString("schedule_type"),
                resultSet.getString("cron_expr"),
                resultSet.getString("timezone"),
                resultSet.getBoolean("enabled"),
                resultSet.getString("window_config"),
                resultSet.getString("dedup_scope"),
                resultSet.getString("exchange_code"),
                resultSet.getLong("account_id"),
                resultSet.getString("trade_env"),
                lastTriggeredAt == null ? null : lastTriggeredAt.toInstant(),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant()
        );
    }
}
