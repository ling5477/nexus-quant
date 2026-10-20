package com.guidinglight.nexusquant.notification.infra;

import com.guidinglight.nexusquant.notification.domain.UserNotification;
import com.guidinglight.nexusquant.notification.domain.port.UserNotificationReadPort;
import com.guidinglight.nexusquant.research.domain.port.ResearchFeeSourcePort;
import com.guidinglight.nexusquant.research.infra.ResearchFeeReadScope;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/** 一次有界查询归并原事件；先按账户/创建身份和管理员边界过滤，绝不读取事件payload或凭据。 */
public final class JdbcUserNotificationReadPort implements UserNotificationReadPort {
  private final JdbcTemplate jdbc;
  private final ResearchFeeReadScope feeScope =
      new ResearchFeeReadScope(ResearchFeeSourcePort.disabled());

  public JdbcUserNotificationReadPort(JdbcTemplate template) {
    jdbc = new JdbcTemplate(template.getDataSource());
    jdbc.setQueryTimeout(5);
  }

  @Override
  public List<UserNotification> latest(long owner, int offset, int limit) {
    if (owner <= 0 || offset < 0 || offset > 950 || limit < 1 || limit > 51)
      throw new IllegalArgumentException("INVALID_NOTIFICATION_QUERY");
    // 各来源先取足当前页窗口，再全局排序。系统任务状态是最近一次执行摘要，不补造历史通知事件。
    int window = offset + limit;
    return jdbc.query(
        """
WITH actor AS (
    SELECT u.id,u.username,EXISTS(
        SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id
        WHERE ur.user_id=u.id AND r.role_code IN ('ADMIN','ROLE_ADMIN')) AS administrator
    FROM users u WHERE u.id=? AND u.enabled
), owned_runs AS (
    SELECT p.paper_run_id,p.trade_env,p.canonical_account_id
    FROM paper_trading_runs p
    LEFT JOIN backtest_publish_records published ON published.publish_record_id=p.publish_id
    LEFT JOIN strategy_versions frozen ON frozen.strategy_version_id=coalesce(p.strategy_version_id,published.strategy_version_id)
    WHERE p.trade_env='SIM' AND ((p.created_by<>'system' AND p.created_by=(SELECT username FROM actor)) OR EXISTS(
        SELECT 1 FROM strategy_definitions source JOIN exchange_accounts a
            ON a.legacy_account_id=source.account_id AND a.trade_env=source.trade_env AND a.exchange_code=source.exchange_code
        WHERE a.owner_user_id=(SELECT id FROM actor) AND (source.strategy_code=frozen.strategy_code
            OR source.strategy_id=published.target_strategy_definition_id OR source.strategy_id=published.source_strategy_id)))
"""
            + " AND "
            + feeScope.backtest("published.backtest_run_id", owner)
            + """
), alerts AS (
    SELECT 'alert:'||a.alert_id AS id,'ALERT' AS kind,a.paper_run_id,
        CASE WHEN p.canonical_account_id IS NULL THEN 'LEGACY_PAPER' ELSE 'CANONICAL_SIM' END AS run_kind,
        p.trade_env,a.title,a.severity,a.status,a.updated_at AS occurred_at,
        'PAPER_RUN'::text AS source_type,a.paper_run_id AS source_id
    FROM paper_run_alerts a JOIN owned_runs p ON p.paper_run_id=a.paper_run_id
    ORDER BY a.updated_at DESC,a.alert_id DESC LIMIT ?
), recovery AS (
    SELECT 'recovery:'||r.recovery_event_id AS id,'RECOVERY' AS kind,r.paper_run_id,
        CASE WHEN p.canonical_account_id IS NULL THEN 'LEGACY_PAPER' ELSE 'CANONICAL_SIM' END AS run_kind,
        p.trade_env,r.recovery_type AS title,'INFO' AS severity,r.status,coalesce(r.finished_at,r.created_at) AS occurred_at,
        'PAPER_RUN'::text AS source_type,r.paper_run_id AS source_id
    FROM paper_run_recovery_events r JOIN owned_runs p ON p.paper_run_id=r.paper_run_id
    ORDER BY coalesce(r.finished_at,r.created_at) DESC,r.recovery_event_id DESC LIMIT ?
), owned_shadow AS (
    SELECT s.id FROM shadow_runs s
    JOIN strategy_versions v ON v.strategy_version_id=s.strategy_version_id
    LEFT JOIN backtest_publish_records published ON published.publish_record_id=s.publish_id
    WHERE EXISTS(SELECT 1 FROM strategy_definitions d JOIN exchange_accounts a
        ON a.legacy_account_id=d.account_id AND a.trade_env=d.trade_env AND a.exchange_code=d.exchange_code
        WHERE d.strategy_code=v.strategy_code AND a.owner_user_id=(SELECT id FROM actor))
        AND (s.paper_run_id IS NULL OR EXISTS(SELECT 1 FROM owned_runs p WHERE p.paper_run_id=s.paper_run_id))
"""
            + " AND (s.publish_id IS NULL OR "
            + feeScope.backtest("published.backtest_run_id", owner)
            + ")"
            + """
), shadow_events AS (
    SELECT 'shadow:'||e.id::text AS id,'SHADOW_EVENT' AS kind,
        NULL::varchar AS paper_run_id,NULL::text AS run_kind,NULL::varchar AS trade_env,
        e.event_type AS title,
        CASE WHEN e.event_type IN ('FAILED','ILLEGAL_STATE_TRANSITION_ATTEMPT') THEN 'HIGH'
            WHEN e.event_type='PRECHECK_BLOCKED' THEN 'MEDIUM' ELSE 'INFO' END AS severity,
        coalesce(e.to_status,'UNKNOWN') AS status,e.created_at AS occurred_at,
        'SHADOW_RUN'::text AS source_type,e.shadow_run_id::text AS source_id
    FROM shadow_run_events e JOIN owned_shadow s ON s.id=e.shadow_run_id
    WHERE e.event_type<>'SNAPSHOT_CAPTURED'
    ORDER BY e.created_at DESC,e.id DESC LIMIT ?
), system_operations AS (
    SELECT 'scheduler:'||o.operation_id::text AS id,'SYSTEM_OPERATION' AS kind,
        NULL::varchar AS paper_run_id,NULL::text AS run_kind,NULL::varchar AS trade_env,
        o.job_key AS title,
        CASE WHEN o.execution_result='FAILED' OR o.status='REJECTED' THEN 'HIGH'
            WHEN o.status='OUTCOME_UNKNOWN' THEN 'MEDIUM' ELSE 'INFO' END AS severity,
        coalesce(o.execution_result,o.status) AS status,coalesce(o.completed_at,o.started_at,o.created_at) AS occurred_at,
        'SCHEDULER_OPERATION'::text AS source_type,o.operation_id::text AS source_id
    FROM scheduled_job_operations o JOIN actor a ON a.id=o.owner_user_id AND a.administrator
    ORDER BY coalesce(o.completed_at,o.started_at,o.created_at) DESC,o.operation_id DESC LIMIT ?
), system_health AS (
    SELECT 'job:'||j.job_key||':'||extract(epoch FROM j.last_finished_at)::text AS id,'SYSTEM_HEALTH' AS kind,
        NULL::varchar AS paper_run_id,NULL::text AS run_kind,NULL::varchar AS trade_env,
        j.job_key AS title,'HIGH' AS severity,j.last_status AS status,j.last_finished_at AS occurred_at,
        'SCHEDULER_JOB'::text AS source_type,j.job_key AS source_id
    FROM scheduled_job_controls j
    WHERE (SELECT administrator FROM actor) AND j.last_status='FAILED' AND j.last_finished_at IS NOT NULL
    ORDER BY j.last_finished_at DESC,j.job_key DESC LIMIT ?
)
SELECT * FROM (SELECT * FROM alerts UNION ALL SELECT * FROM recovery
    UNION ALL SELECT * FROM shadow_events UNION ALL SELECT * FROM system_operations
    UNION ALL SELECT * FROM system_health) items
ORDER BY occurred_at DESC,id DESC LIMIT ? OFFSET ?
""",
        (rs, row) ->
            new UserNotification(
                rs.getString("id"),
                rs.getString("kind"),
                rs.getString("paper_run_id"),
                rs.getString("run_kind"),
                rs.getString("trade_env"),
                rs.getString("title"),
                rs.getString("severity"),
                rs.getString("status"),
                rs.getTimestamp("occurred_at").toInstant(),
                rs.getString("source_type"),
                rs.getString("source_id")),
        owner,
        window,
        window,
        window,
        window,
        window,
        limit,
        offset);
  }
}
