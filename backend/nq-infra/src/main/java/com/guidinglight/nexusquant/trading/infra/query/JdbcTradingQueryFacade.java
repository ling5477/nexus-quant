package com.guidinglight.nexusquant.trading.infra.query;

import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.trading.application.query.AccountBalanceQueryView;
import com.guidinglight.nexusquant.trading.application.query.AccountQueryView;
import com.guidinglight.nexusquant.trading.application.query.OrderQueryView;
import com.guidinglight.nexusquant.trading.application.query.PositionQueryView;
import com.guidinglight.nexusquant.trading.application.query.TradeQueryView;
import com.guidinglight.nexusquant.trading.application.query.TradingQueryFacade;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * JdbcTradingQueryFacade 提供 trading 查询门面的 JDBC 实现。
 * <p>
 * Why:
 * 交易读侧 SQL 必须由 `nq-infra` 承接，不能继续落在 `nq-app` composition root。
 */
public class JdbcTradingQueryFacade implements TradingQueryFacade {

    private final JdbcTemplate jdbcTemplate;

    public JdbcTradingQueryFacade(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<OrderQueryView> listOrders(
            Long accountId,
            String orderId,
            String venue,
            String symbol,
            OrderStatus status,
            String tradeEnv,
            int page,
            int size,
            String traceId
    ) {
        if (accountId == null || accountId <= 0) {
            return List.of();
        }
        OrderWhereClause whereClause = buildOrderWhereClause(accountId, orderId, venue, symbol, status, tradeEnv);
        List<Object> args = new ArrayList<>(whereClause.args());
        args.add(size);
        args.add((long) page * size);
        return jdbcTemplate.query(
                """
                        SELECT order_id, account_id, venue, symbol, client_order_id, external_order_id,
                               side, type, price, qty, status, trade_env, created_at, updated_at, trace_id
                        FROM orders
                        """
                        + whereClause.sql()
                        + """
                        
                        ORDER BY created_at DESC, order_id DESC
                        LIMIT ? OFFSET ?
                        """,
                (resultSet, rowNum) -> mapOrder(resultSet),
                args.toArray()
        );
    }

    @Override
    public long countOrders(Long accountId, String orderId, String venue, String symbol, OrderStatus status, String tradeEnv, String traceId) {
        if (accountId == null || accountId <= 0) {
            return 0L;
        }
        OrderWhereClause whereClause = buildOrderWhereClause(accountId, orderId, venue, symbol, status, tradeEnv);
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders " + whereClause.sql(),
                Long.class,
                whereClause.args().toArray()
        );
        return total == null ? 0L : total;
    }

    @Override
    public Optional<OrderQueryView> queryOrder(String orderId, String traceId) {
        if (orderId == null || orderId.isBlank()) {
            return Optional.empty();
        }
        List<OrderQueryView> rows = jdbcTemplate.query(
                """
                        SELECT order_id, account_id, venue, symbol, client_order_id, external_order_id,
                               side, type, price, qty, status, trade_env, created_at, updated_at, trace_id
                        FROM orders
                        WHERE order_id = ?
                        """,
                (resultSet, rowNum) -> mapOrder(resultSet),
                orderId.trim()
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public Optional<TradeQueryView> queryLatestTrade(String orderId, String traceId) {
        if (orderId == null || orderId.isBlank()) {
            return Optional.empty();
        }
        List<TradeQueryView> rows = jdbcTemplate.query(
                """
                        SELECT trade_id, order_id, account_id, exchange, symbol, external_order_id, exchange_trade_id,
                               price, qty, fee, fee_currency, ts, trace_id
                        FROM trades
                        WHERE order_id = ?
                        ORDER BY ts DESC, trade_id DESC
                        LIMIT 1
                        """,
                (resultSet, rowNum) -> new TradeQueryView(
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
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public Optional<PositionQueryView> queryPosition(Long accountId, String symbol, String traceId) {
        if (accountId == null || accountId <= 0 || symbol == null || symbol.isBlank()) {
            return Optional.empty();
        }
        List<PositionQueryView> rows = jdbcTemplate.query(
                """
                        SELECT p.account_id, a.venue, p.symbol, p.qty, p.available_qty, p.avg_price, p.trace_id
                        FROM positions p
                        JOIN accounts a ON a.account_id = p.account_id
                        WHERE p.account_id = ? AND p.symbol = ?
                        """,
                (resultSet, rowNum) -> new PositionQueryView(
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
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public Optional<AccountQueryView> queryAccount(Long accountId, String traceId) {
        if (accountId == null || accountId <= 0) {
            return Optional.empty();
        }
        List<AccountBalanceQueryView> balances = jdbcTemplate.query(
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
                (resultSet, rowNum) -> new AccountBalanceQueryView(
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
        return Optional.of(new AccountQueryView(accountId, venue, balances, balances.getFirst().traceId()));
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private OrderWhereClause buildOrderWhereClause(
            Long accountId,
            String orderId,
            String venue,
            String symbol,
            OrderStatus status,
            String tradeEnv
    ) {
        StringBuilder sql = new StringBuilder("WHERE account_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(accountId);
        appendTextFilter(sql, args, "order_id", orderId);
        appendTextFilter(sql, args, "venue", venue);
        appendTextFilter(sql, args, "symbol", symbol);
        if (status != null) {
            sql.append(" AND status = ?");
            args.add(status.name());
        }
        appendTextFilter(sql, args, "trade_env", tradeEnv);
        return new OrderWhereClause(sql.toString(), args);
    }

    private void appendTextFilter(StringBuilder sql, List<Object> args, String column, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        sql.append(" AND ").append(column).append(" = ?");
        args.add(value.trim());
    }

    private OrderQueryView mapOrder(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new OrderQueryView(
                resultSet.getString("order_id"),
                resultSet.getLong("account_id"),
                resultSet.getString("venue"),
                resultSet.getString("symbol"),
                resultSet.getString("client_order_id"),
                resultSet.getString("external_order_id"),
                resultSet.getString("side"),
                resultSet.getString("type"),
                resultSet.getBigDecimal("price"),
                resultSet.getBigDecimal("qty"),
                OrderStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("trade_env"),
                toInstant(resultSet.getTimestamp("created_at")),
                toInstant(resultSet.getTimestamp("updated_at")),
                resultSet.getString("trace_id")
        );
    }

    private record OrderWhereClause(String sql, List<Object> args) {
    }
}
