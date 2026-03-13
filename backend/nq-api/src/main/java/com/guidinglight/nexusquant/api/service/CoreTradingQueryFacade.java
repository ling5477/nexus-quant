package com.guidinglight.nexusquant.api.service;

import com.guidinglight.nexusquant.api.model.AccountBalanceView;
import com.guidinglight.nexusquant.api.model.AccountView;
import com.guidinglight.nexusquant.api.model.OrderView;
import com.guidinglight.nexusquant.api.model.PositionView;
import com.guidinglight.nexusquant.api.model.TradeView;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * CoreTradingQueryFacade 使用最小 JDBC 只读查询提供 GateD 验收视图。
 * <p>
 * Why:
 * 第四批需要把 `nq-api` 扩展到订单、成交、持仓、账户快照四类最小视图；这些视图已经由
 * `orders / trades / positions / account_snapshots` 提供稳定事实来源，因此本轮直接用只读 SQL
 * 收口查询闭环，避免为了读路径再引入新的服务耦合或 projection 模块。
 */
public class CoreTradingQueryFacade implements TradingQueryFacade {

    private final JdbcTemplate jdbcTemplate;

    /**
     * @param jdbcTemplate JDBC 执行器
     */
    public CoreTradingQueryFacade(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    /**
     * 根据订单 ID 查询最小订单视图。
     *
     * @param orderId 系统订单 ID
     * @param traceId 链路追踪 ID；当前实现不参与查询条件，但保留在接口语义中，便于后续补日志/审计
     * @return 命中时返回订单最小读视图
     */
    @Override
    public Optional<OrderView> queryOrder(String orderId, String traceId) {
        if (orderId == null || orderId.isBlank()) {
            return Optional.empty();
        }
        List<OrderView> rows = jdbcTemplate.query(
                """
                        SELECT order_id, account_id, venue, symbol, client_order_id, external_order_id, price, qty, status, trace_id
                        FROM orders
                        WHERE order_id = ?
                        """,
                (resultSet, rowNum) -> new OrderView(
                        resultSet.getString("order_id"),
                        resultSet.getLong("account_id"),
                        resultSet.getString("venue"),
                        resultSet.getString("symbol"),
                        resultSet.getString("client_order_id"),
                        resultSet.getString("external_order_id"),
                        resultSet.getBigDecimal("price"),
                        resultSet.getBigDecimal("qty"),
                        OrderStatus.valueOf(resultSet.getString("status")),
                        resultSet.getString("trace_id")
                ),
                orderId.trim()
        );
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.getFirst());
    }

    /**
     * 根据订单 ID 查询最近一笔成交。
     *
     * @param orderId 系统订单 ID
     * @param traceId 链路追踪 ID；当前不参与过滤，但保留统一语义
     * @return 命中时返回最近一笔成交
     */
    @Override
    public Optional<TradeView> queryLatestTrade(String orderId, String traceId) {
        if (orderId == null || orderId.isBlank()) {
            return Optional.empty();
        }
        List<TradeView> rows = jdbcTemplate.query(
                """
                        SELECT trade_id, order_id, account_id, exchange, symbol, external_order_id, exchange_trade_id,
                               price, qty, fee, fee_currency, ts, trace_id
                        FROM trades
                        WHERE order_id = ?
                        ORDER BY ts DESC, trade_id DESC
                        LIMIT 1
                        """,
                (resultSet, rowNum) -> new TradeView(
                        resultSet.getString("trade_id"),
                        resultSet.getString("order_id"),
                        resultSet.getLong("account_id"),
                        resultSet.getString("exchange"),
                        resultSet.getString("symbol"),
                        resultSet.getString("external_order_id"),
                        resultSet.getString("exchange_trade_id"),
                        resultSet.getBigDecimal("price"),
                        resultSet.getBigDecimal("qty"),
                        resultSet.getBigDecimal("fee"),
                        resultSet.getString("fee_currency"),
                        toInstant(resultSet.getTimestamp("ts")),
                        resultSet.getString("trace_id")
                ),
                orderId.trim()
        );
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.getFirst());
    }

    /**
     * 根据账户与交易对查询最小持仓视图。
     *
     * @param accountId 账户 ID
     * @param symbol    交易对
     * @param traceId   链路追踪 ID；当前不参与过滤，但保留统一语义
     * @return 命中时返回持仓投影
     */
    @Override
    public Optional<PositionView> queryPosition(Long accountId, String symbol, String traceId) {
        if (accountId == null || accountId <= 0 || symbol == null || symbol.isBlank()) {
            return Optional.empty();
        }
        List<PositionView> rows = jdbcTemplate.query(
                """
                        SELECT p.account_id, a.venue, p.symbol, p.qty, p.available_qty, p.avg_price, p.trace_id
                        FROM positions p
                        JOIN accounts a ON a.account_id = p.account_id
                        WHERE p.account_id = ? AND p.symbol = ?
                        """,
                (resultSet, rowNum) -> new PositionView(
                        resultSet.getLong("account_id"),
                        resultSet.getString("venue"),
                        resultSet.getString("symbol"),
                        resultSet.getBigDecimal("qty"),
                        resultSet.getBigDecimal("available_qty"),
                        resultSet.getBigDecimal("avg_price"),
                        resultSet.getString("trace_id")
                ),
                accountId,
                symbol.trim()
        );
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.getFirst());
    }

    /**
     * 根据账户查询最新账户快照集合。
     *
     * @param accountId 账户 ID
     * @param traceId   链路追踪 ID；当前不参与过滤，但保留统一语义
     * @return 命中时返回账户最新余额集合
     */
    @Override
    public Optional<AccountView> queryAccount(Long accountId, String traceId) {
        if (accountId == null || accountId <= 0) {
            return Optional.empty();
        }
        List<AccountBalanceView> balances = jdbcTemplate.query(
                """
                        SELECT latest.currency, latest.balance, latest.available, latest.frozen, latest.ts, latest.trace_id
                        FROM (
                            SELECT snapshot_id, account_id, currency, balance, available, frozen, ts, trace_id,
                                   ROW_NUMBER() OVER (
                                       PARTITION BY account_id, currency
                                       ORDER BY ts DESC, snapshot_id DESC
                                   ) AS rn
                            FROM account_snapshots
                            WHERE account_id = ?
                        ) latest
                        WHERE latest.rn = 1
                        ORDER BY latest.currency
                        """,
                (resultSet, rowNum) -> new AccountBalanceView(
                        resultSet.getString("currency"),
                        resultSet.getBigDecimal("balance"),
                        resultSet.getBigDecimal("available"),
                        resultSet.getBigDecimal("frozen"),
                        toInstant(resultSet.getTimestamp("ts")),
                        resultSet.getString("trace_id")
                ),
                accountId
        );
        if (balances.isEmpty()) {
            return Optional.empty();
        }
        String venue = jdbcTemplate.query(
                "SELECT venue FROM accounts WHERE account_id = ?",
                (resultSet, rowNum) -> resultSet.getString("venue"),
                accountId
        ).stream().findFirst().orElse(null);
        if (venue == null || venue.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new AccountView(accountId, venue, balances, balances.getFirst().traceId()));
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
