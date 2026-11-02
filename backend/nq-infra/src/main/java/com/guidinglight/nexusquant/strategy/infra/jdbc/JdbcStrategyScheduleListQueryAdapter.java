package com.guidinglight.nexusquant.strategy.infra.jdbc;

import com.guidinglight.nexusquant.strategy.domain.StrategySchedulePage;
import com.guidinglight.nexusquant.strategy.domain.StrategyScheduleSummary;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyScheduleListQuery;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyScheduleListQueryPort;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** 条件统一用于总数和页记录；最后触发时间沿用正式准入事实，内部扫描仓储保持不变。 */
@Repository
public class JdbcStrategyScheduleListQueryAdapter implements StrategyScheduleListQueryPort {
    private final JdbcTemplate jdbc;

    public JdbcStrategyScheduleListQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public StrategySchedulePage readPage(StrategyScheduleListQuery query) {
        String where = " WHERE s.strategy_id=?";
        List<Object> args = new ArrayList<>();
        args.add(query.strategyId());
        if (query.scheduleId() != null) {
            where += " AND s.schedule_job_id=?";
            args.add(query.scheduleId());
        }
        if (query.status() != null) {
            where += " AND s.enabled=?";
            args.add("ENABLED".equals(query.status()));
        }
        if (query.scheduleType() != null) {
            where += " AND s.schedule_type=?";
            args.add(query.scheduleType());
        }
        long total =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM strategy_schedules s" + where,
                        Long.class,
                        args.toArray());
        args.add(query.limit());
        args.add(query.offset());
        var items =
                jdbc.query(
                        """
            SELECT s.schedule_job_id,s.strategy_id,s.schedule_type,s.cron_expr,s.timezone,s.enabled,
                s.exchange_code,s.account_id,s.trade_env,s.created_at,s.updated_at,
                greatest(s.last_triggered_at,(SELECT max(r.admission_due_at) FROM strategy_runs r
                    WHERE r.admission_schedule_id=s.schedule_job_id)) AS last_triggered_at
            FROM strategy_schedules s
            """
                                + where
                                + " ORDER BY s.created_at DESC,s.schedule_job_id DESC LIMIT ? OFFSET ?",
                        (rs, index) ->
                                new StrategyScheduleSummary(
                                        rs.getString("schedule_job_id"),
                                        rs.getString("strategy_id"),
                                        rs.getString("schedule_type"),
                                        rs.getString("cron_expr"),
                                        rs.getString("timezone"),
                                        rs.getBoolean("enabled"),
                                        rs.getString("exchange_code"),
                                        rs.getObject("account_id", Long.class),
                                        rs.getString("trade_env"),
                                        instant(rs.getTimestamp("last_triggered_at")),
                                        instant(rs.getTimestamp("created_at")),
                                        instant(rs.getTimestamp("updated_at"))),
                        args.toArray());
        return new StrategySchedulePage(items, total, query.limit(), query.offset());
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
