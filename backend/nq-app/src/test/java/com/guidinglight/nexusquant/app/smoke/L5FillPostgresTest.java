package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.scheduler.model.PaperTradeRecord;
import com.guidinglight.nexusquant.scheduler.service.port.TradeRepository;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;
import com.guidinglight.nexusquant.trading.infra.jdbc.JdbcOrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 真实 PostgreSQL 边界负例与业务内容冲突，沿用已证明的投影 oracle。 */
@EnabledIfSystemProperty(named = "nq.l5.fill", matches = "true")
class L5FillPostgresTest {
    private static final Instant TIME = Instant.parse("2026-09-11T00:00:00Z");

    @Test void sequentialReplaysAndDistinctFillsAtExactBoundary() throws Exception {
        try (var pg = B0Processes.Pg.start(); var f = new L5ProjectionConcurrencyTest.Fixture(pg)) {
            long account = order(f, "valid", "1.0");
            var trades = f.context.getBean(TradeRepository.class);
            var a = fill(account, "valid", "a", "a", "0.6");
            var b = fill(account, "valid", "b", "b", "0.4");
            for (var t : List.of(a, b)) { trades.insertWithRequiredEvent(t); f.apply(t.tradeId()); }
            f.exact(); L5ProjectionConcurrencyTest.decimal("1", f.balance(account, "BTC"));
            String stable = f.durable();
            for (int i = 0; i < 3; i++) {
                // 同一 venue fill 可携带不同临时内部 ID；只允许使用 durable identity 记账。
                trades.insertWithRequiredEvent(fill(account, "valid", "attempt-" + i, "a", "0.60"));
                var durable = trades.findByExchangeAndExchangeTradeId("OKX", "fill-a").orElseThrow();
                assertEquals(a.tradeId(), durable.tradeId()); assertTrue(f.apply(durable.tradeId()));
                assertEquals(stable, f.durable());
            }
            System.out.println("L5_FILL_PG_PASS distinct_0.6_plus_0.4 sequential_replay_full_fact_snapshot_unchanged");
        }
    }

    @Test void uniqueOverfillAndConflictingDuplicateFailWithoutSideEffects() throws Exception {
        try (var pg = B0Processes.Pg.start(); var f = new L5ProjectionConcurrencyTest.Fixture(pg)) {
            long account = order(f, "overfill", "1.0");
            var trades = f.context.getBean(TradeRepository.class);
            var a = fill(account, "overfill", "a", "a", "0.8");
            trades.insertWithRequiredEvent(a); f.apply(a.tradeId()); String stable = f.durable();
            var failure = assertThrows(IllegalStateException.class,
                    () -> trades.insertWithRequiredEvent(fill(account, "overfill", "b", "b", "0.3")));
            assertTrue(failure.getMessage().startsWith("RECONCILIATION_OVERFILL_OR_INVALID_QUANTITY"));
            assertEquals(stable, f.durable());
            assertTrue(assertThrows(IllegalStateException.class,
                    () -> trades.insertWithRequiredEvent(fill(account, "overfill", "other", "a", "0.3")))
                    .getMessage().contains("TRADE_FILL_IDENTITY_CONFLICT"));
            assertEquals(stable, f.durable());
            long other = order(f, "other", "1.0");
            assertTrue(assertThrows(IllegalStateException.class,
                    () -> trades.insertWithRequiredEvent(fill(other, "other", "cross-order", "a", "0.8")))
                    .getMessage().contains("TRADE_FILL_IDENTITY_CONFLICT"));
            assertEquals(stable, f.durable());
            // 真正主键冲突保留数据库拒绝；后续正常事务仍可提交，没有吞掉 unique violation。
            assertThrows(RuntimeException.class, () -> trades.insertWithRequiredEvent(fill(account, "overfill", "a", "different", "0.2")));
            assertEquals(stable, f.durable());
            var valid = fill(account, "overfill", "c", "c", "0.2");
            trades.insertWithRequiredEvent(valid); f.apply(valid.tradeId()); f.exact();
            System.out.println("L5_FILL_PG_PASS true_overfill_rejected conflicting_duplicate_and_pk_collision_rejected later_transaction_valid");
        }
    }

    @Test void effectiveOrderQuantityIsTheUpperBoundEvenInsideRequestedRoundingRemainder() throws Exception {
        try (var pg = B0Processes.Pg.start(); var f = new L5ProjectionConcurrencyTest.Fixture(pg)) {
            long account = order(f, "effective", "0.1");
            var trades = f.context.getBean(TradeRepository.class);
            var a = fill(account, "effective", "a", "a", "0.1");
            trades.insertWithRequiredEvent(a); f.apply(a.tradeId()); String stable = f.durable();
            // 正常 V51 请求/规范化链另由有效数量真实进程回归证明；这里直接验证仓储上限。
            var failure = assertThrows(IllegalStateException.class,
                    () -> trades.insertWithRequiredEvent(fill(account, "effective", "b", "b", "0.0001")));
            assertTrue(failure.getMessage().startsWith("RECONCILIATION_OVERFILL_OR_INVALID_QUANTITY"));
            assertEquals(stable, f.durable()); f.exact();
            System.out.println("L5_FILL_PG_PASS effective_order_0.1 rejects_unique_0.0001_after_full_fill");
        }
    }

    private static long order(L5ProjectionConcurrencyTest.Fixture f, String id, String qty) {
        long account = f.account(id);
        new JdbcOrderRepository(f.jdbc).insert(new OrderRecord("order-" + id, account, null, "OKX", "BTC-USDT",
                "client-" + id, "BUY", "LIMIT", new BigDecimal("100"), new BigDecimal(qty), "venue-" + id,
                OrderStatus.FILLED, "FILL_REMEDIATION", "trace-" + id, "SIM"), TIME);
        return account;
    }

    private static PaperTradeRecord fill(long account, String order, String trade, String fill, String qty) {
        return new PaperTradeRecord("trade-" + trade, "order-" + order, account, "BTC-USDT", "OKX", "venue-" + order,
                "fill-" + fill, new BigDecimal("100"), new BigDecimal(qty), new BigDecimal("0.01"), "USDT", "trace-" + order, TIME);
    }
}
