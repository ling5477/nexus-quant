package com.guidinglight.nexusquant.strategy.infra.jdbc;

import com.guidinglight.nexusquant.strategy.domain.StrategyRunPage;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunStatus;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunSummary;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunListQuery;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunListQueryPort;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

/** 读取canonical运行表；计划以准入字段为先，历史请求按既有标记顺序解析，不做LIKE前缀猜测。 */
@Repository
public class JdbcStrategyRunListQueryAdapter implements StrategyRunListQueryPort {
    static final String RUN_VIEW =
            """
            WITH run_view AS (
                SELECT strategy_id, strategy_run_id, request_id, status, exchange_code, account_id,
                       trade_env, started_at, finished_at, error_message, trace_id,
                       CASE WHEN admission_schedule_id IS NOT NULL THEN trigger_type
                            WHEN starts_with(request_id, 'req-schedule-') THEN 'SCHEDULED'
                            ELSE trigger_type END AS normalized_trigger_type,
                       COALESCE(admission_schedule_id, CASE WHEN starts_with(request_id, 'req-schedule-') THEN
                           CASE WHEN strpos(substring(request_id from 14), '-window-') > 1
                                THEN split_part(substring(request_id from 14), '-window-', 1)
                                WHEN strpos(substring(request_id from 14), '-request-') > 1
                                THEN split_part(substring(request_id from 14), '-request-', 1)
                                WHEN strpos(substring(request_id from 14), '-strategy-') > 1
                                THEN split_part(substring(request_id from 14), '-strategy-', 1)
                           END END) AS schedule_job_id
                FROM strategy_runs
            )
            """;
    private final JdbcTemplate jdbc;

    public JdbcStrategyRunListQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public StrategyRunPage readPage(StrategyRunListQuery query) {
        var where = new StringBuilder(" WHERE ");
        var values = new ArrayList<Object>();
        if (query.strategyId() != null) {
            where.append("strategy_id = ?");
            values.add(query.strategyId());
        } else {
            where.append("schedule_job_id = ?");
            values.add(query.scheduleId());
        }
        if (query.status() != null) {
            where.append(" AND status = ?");
            values.add(query.status());
        }
        if (query.triggerType() != null) {
            where.append(" AND normalized_trigger_type = ?");
            values.add(query.triggerType());
        }
        long total =
                jdbc.queryForObject(
                        RUN_VIEW + "SELECT COUNT(*) FROM run_view" + where,
                        Long.class,
                        values.toArray());
        values.add(query.limit());
        values.add(query.offset());
        // 大JSON和关联子列表不投影；本次窗口不触发N+1查询。
        var items =
                jdbc.query(
                        RUN_VIEW
                                + "SELECT * FROM run_view"
                                + where
                                + " ORDER BY started_at DESC, strategy_run_id DESC LIMIT ? OFFSET ?",
                        (rs, row) -> mapSummary(rs),
                        values.toArray());
        return new StrategyRunPage(items, total, query.limit(), query.offset());
    }

    /** 列表与精确概览共享元信息口径，避免同一运行的计划关系或触发来源互相矛盾。 */
    static StrategyRunSummary mapSummary(ResultSet rs) throws SQLException {
        return new StrategyRunSummary(
                rs.getString("strategy_id"),
                rs.getString("schedule_job_id"),
                rs.getString("strategy_run_id"),
                rs.getString("request_id"),
                rs.getString("normalized_trigger_type"),
                StrategyRunStatus.valueOf(rs.getString("status")),
                rs.getString("exchange_code"),
                rs.getObject("account_id", Long.class),
                rs.getString("trade_env"),
                rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("finished_at") == null
                        ? null
                        : rs.getTimestamp("finished_at").toInstant(),
                rs.getString("error_message"));
    }
}
