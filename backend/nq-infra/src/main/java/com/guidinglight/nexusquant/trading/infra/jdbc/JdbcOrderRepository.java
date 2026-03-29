package com.guidinglight.nexusquant.trading.infra.jdbc;

import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;
import com.guidinglight.nexusquant.trading.domain.port.OrderRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JdbcOrderRepository 是订单端口的 JDBC 实现。
 * <p>
 * Why:
 * Gate B 需要直接落库 `orders` 表以支撑幂等与重启恢复；
 * 此实现集中管理 SQL，避免编排服务散落 SQL 造成状态更新绕过风险。
 */
@Repository
public class JdbcOrderRepository implements OrderRepository {

    private static final String BASE_SELECT = """
            SELECT order_id, account_id, strategy_run_id, venue, symbol, client_order_id, side, type, price, qty,
                   external_order_id, status, reason, trace_id
            FROM orders
            """;

    private static final RowMapper<OrderRecord> ORDER_ROW_MAPPER = JdbcOrderRepository::mapOrderRecord;

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    public JdbcOrderRepository(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, new NamedParameterJdbcTemplate(jdbcTemplate));
    }

    JdbcOrderRepository(JdbcTemplate jdbcTemplate, NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
    }

    @Override
    public Optional<OrderRecord> findByAccountAndClientOrderId(Long accountId, String clientOrderId) {
        List<OrderRecord> results = jdbcTemplate.query(
                BASE_SELECT + " WHERE account_id = ? AND client_order_id = ?",
                ORDER_ROW_MAPPER,
                accountId,
                clientOrderId
        );
        if (results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(results.getFirst());
    }

    @Override
    public Optional<OrderRecord> findByOrderId(String orderId) {
        List<OrderRecord> results = jdbcTemplate.query(
                BASE_SELECT + " WHERE order_id = ?",
                ORDER_ROW_MAPPER,
                orderId
        );
        if (results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(results.getFirst());
    }

    @Override
    public void insert(OrderRecord order, Instant now) {
        jdbcTemplate.update(
                """
                        INSERT INTO orders (
                            order_id, account_id, strategy_run_id, venue, symbol, client_order_id, side, type, price, qty,
                            external_order_id, status, reason, trace_id, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                order.orderId(),
                order.accountId(),
                order.strategyRunId(),
                order.venue(),
                order.symbol(),
                order.clientOrderId(),
                order.side(),
                order.type(),
                order.price(),
                order.qty(),
                order.externalOrderId(),
                order.status().name(),
                order.reason(),
                order.traceId(),
                Timestamp.from(now),
                Timestamp.from(now)
        );
    }

    @Override
    public void updateStatus(String orderId, OrderStatus status, String reason, Instant now) {
        jdbcTemplate.update(
                "UPDATE orders SET status = ?, reason = ?, updated_at = ? WHERE order_id = ?",
                status.name(),
                reason,
                Timestamp.from(now),
                orderId
        );
    }

    @Override
    public void updateExternalOrderId(String orderId, String externalOrderId, Instant now) {
        jdbcTemplate.update(
                "UPDATE orders SET external_order_id = ?, updated_at = ? WHERE order_id = ?",
                externalOrderId,
                Timestamp.from(now),
                orderId
        );
    }

    @Override
    public List<OrderRecord> findByStatuses(Collection<OrderStatus> statuses, int limit) {
        if (statuses == null || statuses.isEmpty()) {
            return List.of();
        }
        List<String> statusValues = statuses.stream().map(Enum::name).collect(Collectors.toList());
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("statuses", statusValues)
                .addValue("limit", limit);
        return namedParameterJdbcTemplate.query(
                BASE_SELECT + " WHERE status IN (:statuses) ORDER BY created_at ASC LIMIT :limit",
                parameters,
                ORDER_ROW_MAPPER
        );
    }

    private static OrderRecord mapOrderRecord(ResultSet resultSet, int rowNum) throws SQLException {
        return new OrderRecord(
                resultSet.getString("order_id"),
                resultSet.getLong("account_id"),
                resultSet.getString("strategy_run_id"),
                resultSet.getString("venue"),
                resultSet.getString("symbol"),
                resultSet.getString("client_order_id"),
                resultSet.getString("side"),
                resultSet.getString("type"),
                resultSet.getBigDecimal("price"),
                resultSet.getBigDecimal("qty"),
                resultSet.getString("external_order_id"),
                OrderStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("reason"),
                resultSet.getString("trace_id")
        );
    }
}


