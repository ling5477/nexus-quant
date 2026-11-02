package com.guidinglight.nexusquant.strategy.infra.jdbc;

import com.guidinglight.nexusquant.strategy.domain.StrategyRun;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunStatus;
import com.guidinglight.nexusquant.strategy.domain.StrategyDispatchIdentity;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunAdmission;
import com.guidinglight.nexusquant.strategy.domain.StrategyDispatchWork;
import com.guidinglight.nexusquant.strategy.domain.StrategySchedule;
import com.guidinglight.nexusquant.strategy.application.StrategyScheduleTiming;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public StrategyRunAdmission admit(StrategyRun run, StrategyDispatchWork work, StrategyDispatchIdentity identity) {
        work.intent(run);
        jdbcTemplate.queryForObject("SELECT strategy_id FROM strategy_definitions WHERE strategy_id=? FOR UPDATE",
                String.class, run.strategyId());
        Timestamp expectedCursor = null;
        if (identity != null) {
            if (!run.strategyId().equals(identity.strategyId()) || !run.accountId().equals(identity.accountId())) {
                throw new IllegalArgumentException("strategy admission scope mismatch");
            }
            StrategySchedule schedule = jdbcTemplate.queryForObject("SELECT * FROM strategy_schedules WHERE schedule_job_id=? FOR UPDATE",
                    (rs, n) -> new StrategySchedule(rs.getString("schedule_job_id"), rs.getString("strategy_id"),
                            rs.getString("schedule_type"), rs.getString("cron_expr"), rs.getString("timezone"), rs.getBoolean("enabled"),
                            rs.getString("window_config"), rs.getString("dedup_scope"), rs.getString("exchange_code"),
                            rs.getLong("account_id"), rs.getString("trade_env"),
                            rs.getTimestamp("last_triggered_at") == null ? null : rs.getTimestamp("last_triggered_at").toInstant(),
                            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()), identity.scheduleJobId());
            List<StrategyRun> existing = jdbcTemplate.query("SELECT * FROM strategy_runs WHERE strategy_id=? AND account_id=? "
                    + "AND admission_schedule_id=? AND admission_due_at=?", ROW_MAPPER, identity.strategyId(), identity.accountId(),
                    identity.scheduleJobId(), Timestamp.from(identity.dueAt()));
            if (!existing.isEmpty()) return new StrategyRunAdmission(false, existing.getFirst());
            expectedCursor = schedule.lastTriggeredAt() == null ? null : Timestamp.from(schedule.lastTriggeredAt());
            Timestamp admitted = jdbcTemplate.queryForObject("SELECT max(admission_due_at) FROM strategy_runs WHERE admission_schedule_id=?",
                    Timestamp.class, identity.scheduleJobId());
            Instant reference = schedule.lastTriggeredAt();
            if (admitted != null && (reference == null || admitted.toInstant().isAfter(reference))) reference = admitted.toInstant();
            if (!identity.dueAt().equals(StrategyScheduleTiming.nextDue(schedule, reference, run.startedAt()))
                    || StrategyScheduleTiming.blockedReason(schedule, run.startedAt()) != null) {
                throw new IllegalStateException("STALE_STRATEGY_WINDOW");
            }
        }
        String canonical = jdbcTemplate.queryForObject("SELECT nq_admit_strategy_work(?,?,?,?,?,?,CAST(? AS jsonb),"
                        + "?,?,?,?,?,?,?,?,?,?,?,?,?,?)", String.class,
                run.strategyRunId(), run.strategyId(), run.accountId(), run.exchangeCode(), run.tradeEnv(), run.triggerType(),
                run.configSnapshot(), run.requestId(), Timestamp.from(run.startedAt()), run.traceId(),
                identity == null ? null : identity.scheduleJobId(), identity == null ? null : Timestamp.from(identity.dueAt()),
                work.definitionVersion(), work.clientOrderId(), work.symbol(), work.side().name(), work.orderType().name(),
                work.quantity(), work.price(), work.timeInForce(), expectedCursor);
        return new StrategyRunAdmission(run.strategyRunId().equals(canonical), findByStrategyRunId(canonical).orElseThrow());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public StrategyRunAdmission admit(StrategyRun run, StrategyDispatchIdentity identity) {
        if (!run.strategyId().equals(identity.strategyId()) || !run.accountId().equals(identity.accountId())
                || run.status() != StrategyRunStatus.CREATED || !"SCHEDULER".equals(run.triggerType())) {
            throw new IllegalArgumentException("strategy admission scope/state mismatch");
        }
        // 旧行可能已重复且没有结构化窗口字段，不能回填猜测。拒绝精确匹配旧请求格式的已消费窗口。
        var legacy = identity.legacyRequestIds();
        List<StrategyRun> old = jdbcTemplate.query("""
                SELECT * FROM strategy_runs WHERE strategy_id=? AND account_id=?
                AND admission_schedule_id IS NULL AND request_id IN (?,?,?)
                ORDER BY started_at, strategy_run_id LIMIT 1
                """, ROW_MAPPER, identity.strategyId(), identity.accountId(), legacy.get(0), legacy.get(1), legacy.get(2));
        if (!old.isEmpty()) return new StrategyRunAdmission(false, old.getFirst());

        int inserted = jdbcTemplate.update("""
                INSERT INTO strategy_runs (strategy_run_id,strategy_id,account_id,status,trigger_type,
                    exchange_code,trade_env,config_snapshot,request_id,started_at,trace_id,
                    admission_schedule_id,admission_due_at)
                SELECT ?,?,?,?,'SCHEDULER',?,?,CAST(? AS JSONB),?,?,?,s.schedule_job_id,?
                FROM strategy_schedules s WHERE s.schedule_job_id=? AND s.strategy_id=? AND s.account_id=?
                ON CONFLICT (strategy_id,account_id,admission_schedule_id,admission_due_at)
                    WHERE admission_schedule_id IS NOT NULL DO NOTHING
                """, run.strategyRunId(), run.strategyId(), run.accountId(), run.status().name(),
                run.exchangeCode(), run.tradeEnv(), run.configSnapshot(), run.requestId(),
                Timestamp.from(run.startedAt()), run.traceId(), Timestamp.from(identity.dueAt()),
                identity.scheduleJobId(), identity.strategyId(), identity.accountId());
        if (inserted == 1) return new StrategyRunAdmission(true, run);
        // READ COMMITTED下使用新语句快照读取冲突赢家；不在unique violation后继续污染事务。
        List<StrategyRun> existing = jdbcTemplate.query("""
                SELECT * FROM strategy_runs WHERE strategy_id=? AND account_id=?
                AND admission_schedule_id=? AND admission_due_at=?
                """, ROW_MAPPER, identity.strategyId(), identity.accountId(), identity.scheduleJobId(),
                Timestamp.from(identity.dueAt()));
        if (existing.size() != 1) throw new IllegalStateException("strategy admission binding missing");
        return new StrategyRunAdmission(false, existing.getFirst());
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
    public Optional<StrategyRun> findLatestByRequestId(String requestId) {
        List<StrategyRun> rows = jdbcTemplate.query(
                """
                        SELECT strategy_run_id, strategy_id, account_id, exchange_code, trade_env, trigger_type, status,
                               config_snapshot::text AS config_snapshot, request_id, started_at, finished_at, error_message, trace_id
                        FROM strategy_runs
                        WHERE request_id = ?
                        ORDER BY started_at DESC, strategy_run_id DESC
                        LIMIT 1
                        """,
                ROW_MAPPER,
                requestId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public boolean existsActiveRunByStrategyId(String strategyId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(1)
                        FROM strategy_runs
                        WHERE strategy_id = ?
                          AND status IN ('CREATED', 'DISPATCHING', 'RUNNING')
                        """,
                Integer.class,
                strategyId
        );
        return count != null && count > 0;
    }

    @Override
    public boolean updateStatus(String strategyRunId, StrategyRunStatus status, Instant finishedAt, String errorMessage) {
        // 原 owner 的迟到回调不能覆盖已恢复的终态；当前生产生命周期仅有这两段迁移。
        StrategyRunStatus expected = switch (status) {
            case DISPATCHING -> StrategyRunStatus.CREATED;
            case RUNNING, FAILED -> StrategyRunStatus.DISPATCHING;
            default -> throw new IllegalArgumentException("unsupported strategy run transition target: " + status);
        };
        return jdbcTemplate.update(
                "UPDATE strategy_runs SET status = ?, finished_at = ?, error_message = ? WHERE strategy_run_id = ? AND status = ?",
                status.name(),
                finishedAt == null ? null : Timestamp.from(finishedAt),
                errorMessage,
                strategyRunId,
                expected.name()
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


