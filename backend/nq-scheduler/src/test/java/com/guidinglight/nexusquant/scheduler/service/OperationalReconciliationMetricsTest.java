package com.guidinglight.nexusquant.scheduler.service;

import com.guidinglight.nexusquant.adapter.okx.service.OkxExchangeAdapter;
import com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository;
import com.guidinglight.nexusquant.contracts.event.EventPublisherPort;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.ledger.contracts.model.LedgerPostingResult;
import com.guidinglight.nexusquant.observability.operational.*;
import com.guidinglight.nexusquant.scheduler.model.LedgerReconcileDiff;
import com.guidinglight.nexusquant.scheduler.model.PaperTradeRecord;
import com.guidinglight.nexusquant.scheduler.service.port.TradeRepository;
import com.guidinglight.nexusquant.trading.application.OrderCommandService;
import com.guidinglight.nexusquant.trading.application.OrderLifecycleService;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OperationalReconciliationMetricsTest {
    @org.junit.jupiter.api.AfterEach
    void closeRegistry() { registry.close(); }

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final OperationalObservation observation = new MicrometerOperationalObservation(registry, Clock.systemUTC());
    private final AuditLogRepository audit = mock(AuditLogRepository.class);

    @Test
    void ledgerReconciliationReportsMatchMismatchAndOriginalException() {
        var repo = mock(com.guidinglight.nexusquant.scheduler.service.port.LedgerReconcileRepository.class);
        var service = new LedgerReconcileScheduler(repo, audit, observation);
        doReturn(List.of()).when(repo).findDiffs();
        assertEquals(0, service.reconcileOnce());
        when(repo.findDiffs()).thenReturn(List.of(new LedgerReconcileDiff(42L, "USDT", BigDecimal.ONE,
                BigDecimal.ZERO, BigDecimal.ONE, "MISMATCH")));
        assertEquals(1, service.reconcileOnce());
        assertEquals(1, registry.get("nq.operational.unresolved.snapshot").gauge().value());
        RuntimeException failure = new IllegalStateException("synthetic repository failure");
        when(repo.findDiffs()).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, service::reconcileOnce));
        assertCount("ledger_reconcile", "attempt", 3);
        assertCount("ledger_reconcile", "success", 1);
        assertCount("ledger_reconcile", "degraded", 1);
        assertCount("ledger_reconcile", "failure", 1);
        assertEquals(1, registry.get("nq.operational.unresolved.snapshot").gauge().value());
        var broken = new LedgerReconcileScheduler(repo, audit, (op, signal, value) -> { throw new IllegalStateException(); });
        assertSame(failure, assertThrows(IllegalStateException.class, broken::reconcileOnce));
        doReturn(List.of()).when(repo).findDiffs();
        assertEquals(0, broken.reconcileOnce());
    }

    @Test
    void durableTradeReplayReportsPostedRejectedAndThrownWithoutChangingBusinessFacts() {
        var orders = mock(OrderCommandService.class);
        var lifecycle = mock(OrderLifecycleService.class);
        var adapter = mock(OkxExchangeAdapter.class);
        var trades = mock(TradeRepository.class);
        var ledger = mock(TradeLedgerGateway.class);
        var events = mock(EventPublisherPort.class);
        var service = new OkxRestReconcileService(orders, lifecycle, adapter, trades, ledger, events, audit, observation);
        var order = new OrderRecord("order-dynamic", 2001L, null, "OKX", "BTC-USDT", "client-dynamic", "SELL",
                "MARKET", null, BigDecimal.ONE, "external-dynamic", OrderStatus.FILLED, "TEST", "trace-dynamic");
        var trade = new PaperTradeRecord("trade-dynamic", order.orderId(), order.accountId(), order.symbol(), "OKX",
                order.externalOrderId(), "fill-dynamic", BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO, "USDT",
                order.traceId(), Instant.EPOCH);
        when(orders.reserveReconciliationCandidates(eq("OKX"), any(), eq(10))).thenReturn(List.of(order));
        when(adapter.listTradeReports(any(), any(), any())).thenReturn(List.of());
        when(trades.findAllByOrderId(order.orderId(), 10)).thenReturn(List.of(trade));
        doReturn(new LedgerPostingResult(true, false, "POSTED")).when(ledger).postTrade(any());
        assertEquals(0, service.reconcileOnce(10));
        when(ledger.postTrade(any())).thenReturn(new LedgerPostingResult(true, true, "IDEMPOTENT"));
        assertEquals(0, service.reconcileOnce(10));
        when(ledger.postTrade(any())).thenReturn(new LedgerPostingResult(false, false, "REJECTED"));
        assertEquals(0, service.reconcileOnce(10));
        assertEquals(1, registry.get("nq.operational.unresolved").tag("operation", "okx_reconcile").counter().count());
        assertEquals(1, registry.get("nq.operational.unresolved").tag("operation", "durable_trade_replay").counter().count());
        RuntimeException failure = new IllegalStateException("synthetic ledger failure");
        when(ledger.postTrade(any())).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.reconcileOnce(10)));
        assertCount("durable_trade_replay", "attempt", 4);
        assertCount("durable_trade_replay", "success", 2);
        assertCount("durable_trade_replay", "failure", 2);
        assertCount("okx_reconcile", "success", 3);
        assertCount("okx_reconcile", "failure", 1);
        verify(ledger, times(4)).postTrade(any());
        verify(trades, never()).insert(any());
        verifyNoInteractions(lifecycle, events);
        for (var meter : registry.getMeters()) {
            assertFalse(meter.getId().getTags().toString().contains("dynamic"));
            assertFalse(meter.getId().getTags().toString().contains("2001"));
            assertFalse(meter.getId().getTags().toString().contains("BTC"));
        }
        var broken = new OkxRestReconcileService(orders, lifecycle, adapter, trades, ledger, events, audit,
                (op, signal, value) -> { throw new IllegalStateException("meter unavailable"); });
        assertSame(failure, assertThrows(IllegalStateException.class, () -> broken.reconcileOnce(10)));
        doReturn(new LedgerPostingResult(true, false, "POSTED")).when(ledger).postTrade(any());
        assertEquals(0, broken.reconcileOnce(10));
    }

    private void assertCount(String operation, String result, double count) {
        assertEquals(count, registry.get("nq.operational.executions").tags("operation", operation, "result", result).counter().count());
    }
}
