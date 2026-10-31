package com.guidinglight.nexusquant.scheduler.infra.jdbc;

import com.guidinglight.nexusquant.scheduler.model.PaperTradeRecord;
import com.guidinglight.nexusquant.scheduler.service.port.TradeRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JdbcTradeRepository 是 trades 表的 JDBC 访问实现。
 */
@Repository
public class JdbcTradeRepository implements TradeRepository {

    private static final RowMapper<PaperTradeRecord> TRADE_ROW_MAPPER = JdbcTradeRepository::mapTrade;

    private final JdbcTemplate jdbcTemplate;

    public JdbcTradeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<PaperTradeRecord> findByOrderId(String orderId) {
        List<PaperTradeRecord> results = jdbcTemplate.query(
                """
                        SELECT t.trade_id, t.order_id, t.account_id, t.symbol, t.exchange, t.external_order_id,
                               t.exchange_trade_id, t.price, t.qty, t.fee, t.fee_currency, t.trace_id, t.ts,
                               t.trade_env, o.trade_env AS order_trade_env
                        FROM trades t JOIN orders o ON o.order_id=t.order_id
                        WHERE t.order_id = ?
                        ORDER BY t.ts DESC
                        LIMIT 1
                        """,
                TRADE_ROW_MAPPER,
                orderId
        );
        if (results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(results.getFirst());
    }

    @Override
    public List<PaperTradeRecord> findAllByOrderId(String orderId, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        List<PaperTradeRecord> results = jdbcTemplate.query(
                """
                        SELECT t.trade_id, t.order_id, t.account_id, t.symbol, t.exchange, t.external_order_id,
                               t.exchange_trade_id, t.price, t.qty, t.fee, t.fee_currency, t.trace_id, t.ts,
                               t.trade_env, o.trade_env AS order_trade_env
                        FROM trades t JOIN orders o ON o.order_id=t.order_id
                        WHERE t.order_id = ?
                        ORDER BY t.ts ASC, t.trade_id ASC
                        LIMIT ?
                        """,
                TRADE_ROW_MAPPER,
                orderId,
                Math.addExact(limit, 1)
        );
        if (results.size() > limit) {
            throw new IllegalStateException("per-order Trade recovery limit exceeded");
        }
        return List.copyOf(results);
    }

    @Override
    public Optional<PaperTradeRecord> findByExchangeAndExchangeTradeId(String exchange, String exchangeTradeId) {
        List<PaperTradeRecord> results = jdbcTemplate.query(
                """
                        SELECT t.trade_id, t.order_id, t.account_id, t.symbol, t.exchange, t.external_order_id,
                               t.exchange_trade_id, t.price, t.qty, t.fee, t.fee_currency, t.trace_id, t.ts,
                               t.trade_env, o.trade_env AS order_trade_env
                        FROM trades t JOIN orders o ON o.order_id=t.order_id
                        WHERE t.exchange = ? AND t.exchange_trade_id = ?
                        ORDER BY t.ts DESC
                        LIMIT 1
                        """,
                TRADE_ROW_MAPPER,
                exchange,
                exchangeTradeId
        );
        if (results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(results.getFirst());
    }

    @Override
    @Transactional
    public void insert(PaperTradeRecord trade) {
        insertNew(trade, lockOrderQuantity(trade.orderId()));
    }

    private BigDecimal lockOrderQuantity(String orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT qty FROM orders WHERE order_id=? FOR UPDATE", BigDecimal.class, orderId);
    }

    private void insertNew(PaperTradeRecord trade, BigDecimal original) {
        // 只有新的 durable fact 才累加数量；上限来自同一事务中锁定的 effective Order。
        BigDecimal executed = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(qty),0) FROM trades WHERE order_id=?", BigDecimal.class, trade.orderId());
        if (original == null || original.signum() <= 0 || trade.qty() == null || trade.qty().signum() <= 0
                || executed == null || executed.signum() < 0 || executed.add(trade.qty()).compareTo(original) > 0) {
            throw new IllegalStateException("RECONCILIATION_OVERFILL_OR_INVALID_QUANTITY: " + trade.orderId());
        }
        // 环境只继承同一事务中已锁定的父订单；调用方不能另选环境，也不依赖数据库默认值。
        jdbcTemplate.update(
                """
                        INSERT INTO trades (
                            trade_id, order_id, account_id, symbol, exchange, external_order_id, exchange_trade_id,
                            price, qty, fee, fee_currency, trace_id, ts, trade_env
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                                  (SELECT trade_env FROM orders WHERE order_id=?))
                        """,
                trade.tradeId(),
                trade.orderId(),
                trade.accountId(),
                trade.symbol(),
                trade.exchange(),
                trade.externalOrderId(),
                trade.exchangeTradeId(),
                trade.price(),
                trade.qty(),
                trade.fee(),
                trade.feeCurrency(),
                trade.traceId(),
                Timestamp.from(trade.ts()),
                trade.orderId()
        );
    }

    @Override
    @Transactional
    public void insertWithRequiredEvent(PaperTradeRecord trade) {
        // 锁外预检不能分类并发输家；同一 Order 的全部插入必须在锁内重新判断既有 fill。
        BigDecimal original = lockOrderQuantity(trade.orderId());
        var existing = findByExchangeAndExchangeTradeId(trade.exchange(), trade.exchangeTradeId());
        if (existing.isPresent()) {
            String durableId = existing.orElseThrow().tradeId();
            // 唯一键相同而业务内容冲突不能当幂等成功；时间由 PostgreSQL 按持久精度比较。
            Boolean matches = jdbcTemplate.queryForObject("""
                    SELECT order_id=? AND account_id=? AND symbol=? AND external_order_id IS NOT DISTINCT FROM ?
                      AND price=? AND qty=? AND fee IS NOT DISTINCT FROM ?
                      AND fee_currency IS NOT DISTINCT FROM ? AND ts=?
                    FROM trades WHERE trade_id=?
                    """, Boolean.class, trade.orderId(), trade.accountId(), trade.symbol(), trade.externalOrderId(),
                    trade.price(), trade.qty(), trade.fee(), trade.feeCurrency(), Timestamp.from(trade.ts()), durableId);
            if (!Boolean.TRUE.equals(matches)) throw new IllegalStateException("TRADE_FILL_IDENTITY_CONFLICT");
            // 使用赢家身份恢复必需事件；不得给本次临时 trade_id 再造事件。
            ensureRequiredEvent(durableId);
            return;
        }
        insertNew(trade, original);
        ensureRequiredEvent(trade.tradeId());
    }

    @Override
    @Transactional
    public void ensureRequiredEvent(String tradeId) {
        new RequiredTradeEventStore(jdbcTemplate).ensure(tradeId);
    }

    private static PaperTradeRecord mapTrade(ResultSet resultSet, int rowNum) throws SQLException {
        // 已有误绑定事实不得静默修复或进入账本重放；父订单是唯一环境事实来源。
        String environment = resultSet.getString("trade_env");
        if (environment == null || !environment.equals(resultSet.getString("order_trade_env"))) {
            throw new IllegalStateException("TRADE_ORDER_ENVIRONMENT_MISMATCH");
        }
        return new PaperTradeRecord(
                resultSet.getString("trade_id"),
                resultSet.getString("order_id"),
                resultSet.getLong("account_id"),
                resultSet.getString("symbol"),
                resultSet.getString("exchange"),
                resultSet.getString("external_order_id"),
                resultSet.getString("exchange_trade_id"),
                resultSet.getBigDecimal("price"),
                resultSet.getBigDecimal("qty"),
                resultSet.getBigDecimal("fee"),
                resultSet.getString("fee_currency"),
                resultSet.getString("trace_id"),
                resultSet.getTimestamp("ts").toInstant()
        );
    }
}

