package com.guidinglight.nexusquant.strategy.infra.jdbc;

import com.guidinglight.nexusquant.research.infra.ResearchFeeReadScope;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunBusinessEventItem;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunLedgerEntryItem;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunRelatedPage;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunRiskEventItem;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunRelatedQuery;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunRelationQueryPort;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/** 使用正式外键/类型身份及账户环境交叉校验；同trace、同前缀或同ref值的其他类型均不构成关联。 */
@Repository
public class JdbcStrategyRunRelationQueryAdapter implements StrategyRunRelationQueryPort {
    private static final String RISK_SCOPE =
            """
            FROM strategy_runs r JOIN orders o ON o.strategy_run_id=r.strategy_run_id
                AND o.account_id=r.account_id AND o.trade_env=r.trade_env
                AND upper(btrim(o.exchange_code))=upper(btrim(r.exchange_code))
            JOIN risk_events e ON e.scope='ORDER' AND e.scope_id=o.order_id
            WHERE r.strategy_run_id=?
            """;
    private static final String LEDGER_SCOPE =
            """
            FROM strategy_runs r JOIN orders o ON o.strategy_run_id=r.strategy_run_id
                AND o.account_id=r.account_id AND o.trade_env=r.trade_env
                AND upper(btrim(o.exchange_code))=upper(btrim(r.exchange_code))
            JOIN trades t ON t.order_id=o.order_id AND t.strategy_run_id=r.strategy_run_id
                AND t.account_id=o.account_id AND t.trade_env=o.trade_env
                AND upper(btrim(t.exchange_code))=upper(btrim(o.exchange_code))
            JOIN ledger_entries e ON e.ref_type='TRADE' AND e.ref_id=t.trade_id AND e.account_id=t.account_id
            WHERE r.strategy_run_id=?
            """;
    private final JdbcTemplate jdbc;

    private final ResearchFeeReadScope fees;

    public JdbcStrategyRunRelationQueryAdapter(JdbcTemplate jdbc, ResearchFeeReadScope fees) {
        this.jdbc = jdbc;
        this.fees = fees;
    }

    /** 普通账户保留owner归属；独立SIM沿用既有创建者/源策略归属，并叠加不可变发布来源的私有费用范围。 */
    private String visibility(long owner) {
        if (owner <= 0) throw new IllegalArgumentException("positive server owner required");
        return " AND ((upper(btrim(r.exchange_code))<>'PAPER' AND EXISTS (SELECT 1 FROM exchange_accounts a "
                + "WHERE a.owner_user_id="
                + owner
                + " AND a.legacy_account_id=r.account_id AND a.trade_env=r.trade_env "
                + "AND upper(btrim(a.exchange_code))=upper(btrim(r.exchange_code)))) OR EXISTS ("
                + "SELECT 1 FROM paper_trading_runs p LEFT JOIN backtest_publish_records published ON published.publish_record_id=p.publish_id "
                + "LEFT JOIN strategy_versions frozen ON frozen.strategy_version_id=coalesce(p.strategy_version_id,published.strategy_version_id) "
                + "WHERE p.canonical_account_id=r.account_id AND p.trade_env='SIM' AND r.trade_env='SIM' AND r.exchange_code='PAPER' "
                + "AND ((p.created_by<>'system' AND p.created_by=(SELECT username FROM users WHERE id="
                + owner
                + ")) OR EXISTS ("
                + "SELECT 1 FROM strategy_definitions source JOIN exchange_accounts a ON a.legacy_account_id=source.account_id "
                + "AND a.trade_env=source.trade_env AND a.exchange_code=source.exchange_code WHERE a.owner_user_id="
                + owner
                + " AND (source.strategy_code=frozen.strategy_code OR source.strategy_id=published.target_strategy_definition_id "
                + "OR source.strategy_id=published.source_strategy_id))) AND "
                + fees.backtest("published.backtest_run_id", owner)
                + "))";
    }

    /** 身份分支在SQL中互斥；状态字段还须属于对应正式事件合同，额外同名字段不能伪装业务回报。 */
    @Override
    public StrategyRunRelatedPage<StrategyRunBusinessEventItem> readBusinessEvents(
            long owner, StrategyRunRelatedQuery query) {
        String cte = StrategyRunEventSql.withLinkedEvents(visibility(owner));
        long total =
                jdbc.queryForObject(
                        cte + "SELECT COUNT(*) FROM linked_events",
                        Long.class,
                        query.strategyRunId());
        var items =
                jdbc.query(
                        cte
                                + """
            SELECT event_id,matched_order_id,matched_trade_id,matched_account_id,event_type,topic,association,trace_id,created_at,
              CASE WHEN jsonb_typeof(payload_json->'source')='string'
 AND char_length(payload_json->>'source')<=256
                THEN payload_json->>'source' END AS event_source,
              payload_json#>>'{payload,ts}' AS business_time,payload_json->>'ts' AS publish_time,
              CASE WHEN event_type IN ('OrderCreated','OrderSubmitted','OrderStatusChangedPayload','OrderAck','CancelAck')
 AND jsonb_typeof(payload_json#>'{payload,status}')='string'
 AND char_length(payload_json#>>'{payload,status}')<=256 THEN payload_json#>>'{payload,status}' END AS snapshot_status,
              CASE WHEN event_type IN ('RiskPassed','RiskRejected','RiskEventRaised')
 AND jsonb_typeof(payload_json#>'{payload,decision}')='string'
 AND char_length(payload_json#>>'{payload,decision}')<=256 THEN payload_json#>>'{payload,decision}' END AS event_decision,
              CASE WHEN event_type IN ('LedgerPosted','LedgerPostFailed')
 AND jsonb_typeof(payload_json#>'{payload,result}')='string'
 AND char_length(payload_json#>>'{payload,result}')<=256 THEN payload_json#>>'{payload,result}' END AS event_result,
              CASE WHEN event_type IN ('OrderReject','CancelReject')
 AND jsonb_typeof(payload_json#>'{payload,reject_code}')='string'
 AND char_length(payload_json#>>'{payload,reject_code}')<=256 THEN payload_json#>>'{payload,reject_code}' END AS reject_code
            FROM linked_events ORDER BY created_at ASC,event_id ASC LIMIT ? OFFSET ?
            """,
                        (rs, index) ->
                                new StrategyRunBusinessEventItem(
                                        rs.getString("event_id"),
                                        rs.getString("matched_order_id"),
                                        rs.getString("matched_trade_id"),
                                        rs.getLong("matched_account_id"),
                                        rs.getString("event_type"),
                                        rs.getString("topic"),
                                        rs.getString("event_source"),
                                        rs.getString("association"),
                                        rs.getString("snapshot_status"),
                                        rs.getString("event_decision"),
                                        rs.getString("event_result"),
                                        rs.getString("reject_code"),
                                        eventTime(rs.getString("business_time")),
                                        eventTime(rs.getString("publish_time")),
                                        rs.getTimestamp("created_at").toInstant(),
                                        rs.getString("trace_id")),
                        query.strategyRunId(),
                        query.limit(),
                        query.offset());
        return new StrategyRunRelatedPage<>(
                query.strategyRunId(), items, total, query.limit(), query.offset());
    }

    /** 旧记录的无效业务时间保持未知，不能用入库时间冒充实际发生时间。 */
    private static Instant eventTime(String raw) {
        if (raw == null || raw.length() > 128) return null;
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException invalid) {
            return null;
        }
    }

    @Override
    public boolean visibleRunExists(long owner, String id) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM strategy_runs r WHERE r.strategy_run_id=?"
                                + visibility(owner)
                                + ")",
                        Boolean.class,
                        id));
    }

    @Override
    public StrategyRunRelatedPage<StrategyRunRiskEventItem> readRiskEvents(
            long owner, StrategyRunRelatedQuery query) {
        long total =
                jdbc.queryForObject(
                        "SELECT COUNT(*) " + RISK_SCOPE + visibility(owner),
                        Long.class,
                        query.strategyRunId());
        var items =
                jdbc.query(
                        "SELECT e.risk_event_id,o.order_id,e.rule_id,e.decision,e.reason,e.severity,e.trace_id,e.created_at "
                                + RISK_SCOPE
                                + visibility(owner)
                                + " ORDER BY e.created_at ASC,e.risk_event_id ASC LIMIT ? OFFSET ?",
                        (rs, row) ->
                                new StrategyRunRiskEventItem(
                                        rs.getString("risk_event_id"),
                                        rs.getString("order_id"),
                                        rs.getString("rule_id"),
                                        rs.getString("decision"),
                                        rs.getString("reason"),
                                        rs.getString("severity"),
                                        rs.getString("trace_id"),
                                        rs.getTimestamp("created_at").toInstant()),
                        query.strategyRunId(),
                        query.limit(),
                        query.offset());
        return new StrategyRunRelatedPage<>(
                query.strategyRunId(), items, total, query.limit(), query.offset());
    }

    @Override
    public StrategyRunRelatedPage<StrategyRunLedgerEntryItem> readLedgerEntries(
            long owner, StrategyRunRelatedQuery query) {
        long total =
                jdbc.queryForObject(
                        "SELECT COUNT(*) " + LEDGER_SCOPE + visibility(owner),
                        Long.class,
                        query.strategyRunId());
        var items =
                jdbc.query(
                        "SELECT e.entry_id,t.trade_id,t.order_id,e.account_id,e.currency,e.delta,e.balance_after,e.direction,e.ref_type,e.idempotency_key,e.trace_id,e.ts "
                                + LEDGER_SCOPE
                                + visibility(owner)
                                + " ORDER BY e.ts ASC,e.entry_id ASC LIMIT ? OFFSET ?",
                        (rs, row) ->
                                new StrategyRunLedgerEntryItem(
                                        rs.getString("entry_id"),
                                        rs.getString("trade_id"),
                                        rs.getString("order_id"),
                                        rs.getLong("account_id"),
                                        rs.getString("currency"),
                                        rs.getBigDecimal("delta"),
                                        rs.getBigDecimal("balance_after"),
                                        rs.getString("direction"),
                                        rs.getString("ref_type"),
                                        rs.getString("idempotency_key"),
                                        rs.getString("trace_id"),
                                        rs.getTimestamp("ts").toInstant()),
                        query.strategyRunId(),
                        query.limit(),
                        query.offset());
        return new StrategyRunRelatedPage<>(
                query.strategyRunId(), items, total, query.limit(), query.offset());
    }
}
