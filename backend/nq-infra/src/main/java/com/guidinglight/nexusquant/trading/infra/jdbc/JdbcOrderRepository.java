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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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
                   external_order_id, status, reason, trace_id, trade_env, version
            FROM orders
            """;

    private static final RowMapper<OrderRecord> ORDER_ROW_MAPPER = JdbcOrderRepository::mapOrderRecord;

    // 不对 qty 做 DISTINCT：等量的不同 fill 必须分别累计；重复身份使证明失效。
    private static final String EXECUTION_PROOF = """
            SELECT o.order_id, o.qty AS original_qty, COALESCE(SUM(t.qty),0) AS executed_qty,
                   COUNT(t.trade_id) AS fill_count,
                   COUNT(DISTINCT (t.exchange,t.exchange_trade_id)) FILTER (WHERE t.trade_id IS NOT NULL) AS identity_count,
                   COUNT(*) FILTER (WHERE t.trade_id IS NOT NULL AND (
                       t.account_id IS DISTINCT FROM o.account_id OR t.symbol IS DISTINCT FROM o.symbol
                       OR t.exchange IS DISTINCT FROM o.venue OR t.trade_env IS DISTINCT FROM o.trade_env
                       OR t.external_order_id IS DISTINCT FROM o.external_order_id
                       OR t.exchange_trade_id IS NULL OR BTRIM(t.exchange_trade_id)=''
                       OR t.qty IS NULL OR t.qty<=0)) AS invalid_count,
                   o.external_order_id
            FROM orders o LEFT JOIN trades t ON t.order_id=o.order_id
            WHERE o.order_id=? GROUP BY o.order_id
            """;

    private static final String VALID_EXECUTION_PROOF = """
            invalid_count=0 AND fill_count=identity_count AND original_qty>0
            AND external_order_id IS NOT NULL AND BTRIM(external_order_id)<>''
            """;

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
        if (order.version() != 0L) {
            throw new IllegalArgumentException("new order version must be zero");
        }
        jdbcTemplate.update(
                """
                        INSERT INTO orders (
                            order_id, account_id, strategy_run_id, venue, symbol, client_order_id, side, type, price, qty,
                            external_order_id, status, reason, trace_id, exchange_code, trade_env, created_at, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                 order.venue(),
                 order.tradeEnv(),
                 Timestamp.from(now),
                Timestamp.from(now)
        );
    }

    @Override
    public int compareAndSetStatus(String orderId, OrderStatus expectedStatus, long expectedVersion,
            OrderStatus status, String reason, Instant now) {
        if (expectedVersion < 0 || expectedVersion == Long.MAX_VALUE) {
            throw new IllegalArgumentException("order version cannot advance");
        }
        return jdbcTemplate.update(
                "UPDATE orders SET status = ?, reason = ?, version = version + 1, updated_at = ?"
                        + " WHERE order_id = ? AND status = ? AND version = ?",
                status.name(),
                reason,
                Timestamp.from(now),
                orderId,
                expectedStatus.name(),
                expectedVersion
        );
    }

    @Override
    public int updateExternalOrderId(String orderId, String externalOrderId, Instant now) {
        if (externalOrderId == null || externalOrderId.isBlank()) {
            throw new IllegalArgumentException("externalOrderId must not be blank");
        }
        return jdbcTemplate.update(
                "UPDATE orders SET external_order_id = ?, updated_at = ? WHERE order_id = ?"
                        + " AND (external_order_id IS NULL OR BTRIM(external_order_id) = '')",
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

    /**
     * 同 venue 的 cursor row 锁串行化预留，READ_COMMITTED 在等待后读取已提交进度。
     * REQUIRES_NEW 保证不把锁带入调用方的 venue I/O；十秒事务超时限制数据库等待。
     * 一条循环排序查询只使用一份 limit，取出的扫描键与订单来自同一语句快照。
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED,
            timeoutString = "${nq.reconciliation.scan-transaction-timeout-seconds:10}")
    public List<OrderRecord> reserveReconciliationCandidates(String venue, Collection<OrderStatus> statuses, int limit) {
        if (venue == null || venue.isBlank() || statuses == null || statuses.isEmpty()
                || statuses.stream().anyMatch(java.util.Objects::isNull) || limit <= 0) {
            throw new IllegalArgumentException("venue, candidate statuses and positive limit are required");
        }
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("reconciliation reservation requires a Spring transaction");
        }
        jdbcTemplate.update("INSERT INTO reconciliation_scan_cursors(venue) VALUES (?) ON CONFLICT (venue) DO NOTHING", venue);
        ScanKey cursor = jdbcTemplate.queryForObject(
                "SELECT cursor_created_at, cursor_order_id FROM reconciliation_scan_cursors WHERE venue=? FOR UPDATE",
                (rs, row) -> new ScanKey(rs.getTimestamp("cursor_created_at"), rs.getString("cursor_order_id")), venue);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("venue", venue)
                .addValue("statuses", statuses.stream().map(Enum::name).toList())
                .addValue("cursorTime", cursor.createdAt(), java.sql.Types.TIMESTAMP)
                .addValue("cursorId", cursor.orderId(), java.sql.Types.VARCHAR)
                .addValue("limit", limit);
        List<ScannedOrder> selected = namedParameterJdbcTemplate.query(
                BASE_SELECT.replace("SELECT order_id", "SELECT created_at, order_id") + """
                 WHERE venue = :venue AND status IN (:statuses)
                 ORDER BY CASE WHEN CAST(:cursorTime AS timestamptz) IS NULL
                     OR (created_at, order_id) > (CAST(:cursorTime AS timestamptz), :cursorId)
                     THEN 0 ELSE 1 END, created_at ASC, order_id ASC
                 LIMIT :limit
                """, parameters, (rs, row) -> new ScannedOrder(mapOrderRecord(rs, row),
                        new ScanKey(rs.getTimestamp("created_at"), rs.getString("order_id"))));
        if (!selected.isEmpty()) {
            ScanKey last = selected.getLast().key();
            int updated = jdbcTemplate.update("UPDATE reconciliation_scan_cursors SET cursor_created_at=?, cursor_order_id=?,"
                    + " revision=revision+1, updated_at=CURRENT_TIMESTAMP WHERE venue=?", last.createdAt(), last.orderId(), venue);
            if (updated != 1) throw new IllegalStateException("locked reconciliation cursor was lost");
        }
        return selected.stream().map(ScannedOrder::order).toList();
    }

    @Override
    public java.math.BigDecimal durableExecutedQuantity(String orderId) {
        return jdbcTemplate.queryForObject("SELECT executed_qty, (" + VALID_EXECUTION_PROOF
                + ") AS valid FROM (" + EXECUTION_PROOF + ") p", (rs, row) -> {
                    if (!rs.getBoolean("valid")) throw new IllegalStateException("INVALID_DURABLE_EXECUTION_PROOF");
                    return rs.getBigDecimal("executed_qty");
                }, orderId);
    }

    @Override
    @Transactional
    public int compareAndSetCancelledToFilled(String orderId, long expectedVersion, String reason, Instant now) {
        if (expectedVersion < 0 || expectedVersion == Long.MAX_VALUE) {
            throw new IllegalArgumentException("order version cannot advance");
        }
        // 与 canonical Trade 插入共用订单行锁；等待结束后另起语句读取完整证明，避免等待前的旧快照。
        jdbcTemplate.queryForObject("SELECT order_id FROM orders WHERE order_id=? FOR UPDATE", String.class, orderId);
        // 数量证明不是调用方传来的布尔值；更新语句自身重新读取已提交成交，并以状态和版本取所有权。
        return jdbcTemplate.update("WITH proof AS (" + EXECUTION_PROOF + ") "
                + "UPDATE orders o SET status='FILLED', reason=?, version=o.version+1, updated_at=? "
                + "WHERE o.order_id=? AND o.status='CANCELLED' AND o.version=? "
                + "AND EXISTS (SELECT 1 FROM proof p WHERE p.order_id=o.order_id AND "
                + VALID_EXECUTION_PROOF + " AND executed_qty=original_qty AND executed_qty=o.qty)",
                orderId, reason, Timestamp.from(now), orderId, expectedVersion);
    }

    private record ScanKey(Timestamp createdAt, String orderId) { }
    private record ScannedOrder(OrderRecord order, ScanKey key) { }

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
                resultSet.getString("trace_id"),
                resultSet.getString("trade_env"),
                resultSet.getLong("version")
        );
    }
}


