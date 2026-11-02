package com.guidinglight.nexusquant.scheduler.paper;

import com.guidinglight.nexusquant.research.domain.port.ResearchFeeSourcePort;
import com.guidinglight.nexusquant.research.infra.ResearchFeeReadScope;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 精确定位实例的 canonical 订单，不依赖总览的500条窗口，也不授予订单执行权限。 */
@Service
public class StrategySimOrderQuery {
    private final JdbcTemplate jdbc;
    private ResearchFeeReadScope feeScope =
            new ResearchFeeReadScope(ResearchFeeSourcePort.disabled());

    /** 原账户归属规则外再叠加费用来源范围，任何一层不能替代另一层授权。 */
    @Autowired
    private void bindFeeReadScope(ResearchFeeReadScope scope) {
        feeScope = scope;
    }

    public StrategySimOrderQuery(JdbcTemplate jdbc) {
        this.jdbc = new JdbcTemplate(jdbc.getDataSource());
        this.jdbc.setQueryTimeout(5);
    }

    /** 订单须同时匹配用户可见实例、账户及SIM/PAPER范围；同快照读取关联事实，超窗口明确截断，不执行交易。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 10)
    public Optional<Details> read(long owner, String paperRunId, String orderId) {
        if (owner <= 0
                || paperRunId == null
                || orderId == null
                || paperRunId.length() > 128
                || orderId.length() > 64) {
            throw new IllegalArgumentException("INVALID_SIM_ORDER_IDENTITY");
        }
        var orders =
                jdbc.queryForList(
                        """
                SELECT o.order_id,o.strategy_run_id,o.client_order_id,o.account_id,o.symbol,o.side,o.type,o.status,o.reason,
                    o.qty::text AS exact_qty,o.price::text AS exact_price,o.created_at,o.updated_at,o.trace_id,execution.status AS strategy_run_status
                FROM paper_trading_runs p JOIN orders o ON o.account_id=p.canonical_account_id
                LEFT JOIN strategy_runs execution ON execution.strategy_run_id=o.strategy_run_id AND execution.account_id=o.account_id
                LEFT JOIN backtest_publish_records published ON published.publish_record_id=p.publish_id
                LEFT JOIN strategy_versions frozen ON frozen.strategy_version_id=coalesce(p.strategy_version_id,published.strategy_version_id)
                WHERE p.paper_run_id=? AND o.order_id=? AND p.trade_env='SIM' AND o.trade_env='SIM' AND o.exchange_code='PAPER'
                    AND ((p.created_by<>'system' AND p.created_by=(SELECT username FROM users WHERE id=?)) OR EXISTS(
                        SELECT 1 FROM strategy_definitions source JOIN exchange_accounts a
                            ON a.legacy_account_id=source.account_id AND a.trade_env=source.trade_env AND a.exchange_code=source.exchange_code
                        WHERE a.owner_user_id=? AND (source.strategy_code=frozen.strategy_code
                            OR source.strategy_id=published.target_strategy_definition_id OR source.strategy_id=published.source_strategy_id)))
                """
                                + " AND "
                                + feeScope.backtest("published.backtest_run_id", owner)
                                + " LIMIT 1",
                        paperRunId,
                        orderId,
                        owner,
                        owner);
        if (orders.isEmpty()) return Optional.empty();
        var order = orders.getFirst();
        long accountId = ((Number) order.get("account_id")).longValue();
        var trades =
                jdbc.queryForList(
                        """
                SELECT trade_id,order_id,symbol,price::text AS exact_price,qty::text AS exact_qty,
                    fee::text AS exact_fee,fee_currency,ts FROM trades
                WHERE account_id=? AND order_id=? ORDER BY ts,trade_id LIMIT 501
                """,
                        accountId,
                        orderId);
        var ledger =
                jdbc.queryForList(
                        """
                SELECT e.entry_id,e.currency,e.delta::text AS exact_delta,e.ref_type,e.ref_id,e.ts,e.idempotency_key
                FROM ledger_entries e JOIN trades t ON e.ref_type='TRADE' AND e.ref_id=t.trade_id AND e.account_id=t.account_id
                WHERE t.account_id=? AND t.order_id=? ORDER BY e.ts,e.entry_id LIMIT 2001
                """,
                        accountId,
                        orderId);
        var risk =
                jdbc.queryForList(
                        """
                SELECT risk_event_id,rule_id,decision,reason,created_at FROM risk_events
                WHERE scope='ORDER' AND scope_id=? ORDER BY created_at,risk_event_id LIMIT 101
                """,
                        orderId);
        return Optional.of(
                new Details(
                        paperRunId,
                        order,
                        trades.stream().limit(500).toList(),
                        ledger.stream().limit(2000).toList(),
                        risk.stream().limit(100).toList(),
                        trades.size() > 500 || ledger.size() > 2000 || risk.size() > 100));
    }

    public record Details(
            String paperRunId,
            Map<String, Object> order,
            List<Map<String, Object>> trades,
            List<Map<String, Object>> ledgerEntries,
            List<Map<String, Object>> riskEvents,
            boolean relatedRecordsLimited) {}
}
