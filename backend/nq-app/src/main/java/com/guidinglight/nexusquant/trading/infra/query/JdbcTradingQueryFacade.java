package com.guidinglight.nexusquant.trading.infra.query;

import com.guidinglight.nexusquant.trading.api.web.AccountBalanceView;
import com.guidinglight.nexusquant.trading.api.web.AccountView;
import com.guidinglight.nexusquant.trading.api.web.OrderView;
import com.guidinglight.nexusquant.trading.api.web.PositionView;
import com.guidinglight.nexusquant.trading.api.web.TradeView;
import com.guidinglight.nexusquant.trading.application.query.TradingQueryFacade;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * JdbcTradingQueryFacade 把 nq-api 的交易查询 SQL 下沉到应用装配侧支撑类。
 */
public class JdbcTradingQueryFacade implements TradingQueryFacade {

    private final JdbcTemplate jdbcTemplate;

    public JdbcTradingQueryFacade(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

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
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

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
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

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
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

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



