package com.guidinglight.nexusquant.strategy.infra.jdbc;

import com.guidinglight.nexusquant.strategy.domain.StrategyRunOrderItem;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunOverview;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunRelatedPage;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunTradeItem;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunDetailQueryPort;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunRelatedQuery;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** 子列表仅使用已持久化strategy_run_id外键；没有按请求前缀、订单文本或trace猜测归属。 */
@Repository
public class JdbcStrategyRunDetailQueryAdapter implements StrategyRunDetailQueryPort {
    private final JdbcTemplate jdbc;

    public JdbcStrategyRunDetailQueryAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<StrategyRunOverview> findOverview(String id) {
        var rows =
                jdbc.query(
                        JdbcStrategyRunListQueryAdapter.RUN_VIEW
                                + """
                SELECT r.*, (SELECT COUNT(*) FROM orders o WHERE o.strategy_run_id=r.strategy_run_id) AS order_count,
                       (SELECT COUNT(*) FROM trades t WHERE t.strategy_run_id=r.strategy_run_id) AS trade_count
                FROM run_view r WHERE strategy_run_id=?
                """,
                        (rs, row) ->
                                new StrategyRunOverview(
                                        JdbcStrategyRunListQueryAdapter.mapSummary(rs),
                                        rs.getLong("order_count"),
                                        rs.getLong("trade_count"),
                                        rs.getString("trace_id")),
                        id);
        return rows.stream().findFirst();
    }

    @Override
    public boolean runExists(String id) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM strategy_runs WHERE strategy_run_id=?)",
                        Boolean.class,
                        id));
    }

    @Override
    public StrategyRunRelatedPage<StrategyRunOrderItem> readOrders(StrategyRunRelatedQuery query) {
        long total =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM orders WHERE strategy_run_id=?",
                        Long.class,
                        query.strategyRunId());
        var items =
                jdbc.query(
                        """
                SELECT order_id,client_order_id,COALESCE(exchange_order_id,external_order_id) AS exchange_order_id,
                       status,symbol,side,type,price,qty,created_at,updated_at
                FROM orders WHERE strategy_run_id=? ORDER BY created_at ASC,order_id ASC LIMIT ? OFFSET ?
                """,
                        (rs, row) ->
                                new StrategyRunOrderItem(
                                        rs.getString("order_id"),
                                        rs.getString("client_order_id"),
                                        rs.getString("exchange_order_id"),
                                        rs.getString("status"),
                                        rs.getString("symbol"),
                                        rs.getString("side"),
                                        rs.getString("type"),
                                        rs.getBigDecimal("price"),
                                        rs.getBigDecimal("qty"),
                                        rs.getTimestamp("created_at").toInstant(),
                                        rs.getTimestamp("updated_at").toInstant()),
                        query.strategyRunId(),
                        query.limit(),
                        query.offset());
        return new StrategyRunRelatedPage<>(
                query.strategyRunId(), items, total, query.limit(), query.offset());
    }

    @Override
    public StrategyRunRelatedPage<StrategyRunTradeItem> readTrades(StrategyRunRelatedQuery query) {
        long total =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM trades WHERE strategy_run_id=?",
                        Long.class,
                        query.strategyRunId());
        var items =
                jdbc.query(
                        """
                SELECT trade_id,order_id,exchange_trade_id,COALESCE(exchange_order_id,external_order_id) AS exchange_order_id,
                       symbol,price,qty,fee,fee_currency,ts
                FROM trades WHERE strategy_run_id=? ORDER BY ts ASC,trade_id ASC LIMIT ? OFFSET ?
                """,
                        (rs, row) ->
                                new StrategyRunTradeItem(
                                        rs.getString("trade_id"),
                                        rs.getString("order_id"),
                                        rs.getString("exchange_trade_id"),
                                        rs.getString("exchange_order_id"),
                                        rs.getString("symbol"),
                                        rs.getBigDecimal("price"),
                                        rs.getBigDecimal("qty"),
                                        rs.getBigDecimal("fee"),
                                        rs.getString("fee_currency"),
                                        rs.getTimestamp("ts").toInstant()),
                        query.strategyRunId(),
                        query.limit(),
                        query.offset());
        return new StrategyRunRelatedPage<>(
                query.strategyRunId(), items, total, query.limit(), query.offset());
    }
}
