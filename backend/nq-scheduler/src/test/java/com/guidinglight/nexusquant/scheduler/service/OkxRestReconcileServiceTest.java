package com.guidinglight.nexusquant.scheduler.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderSnapshot;
import com.guidinglight.nexusquant.adapter.api.model.AdapterTradeReport;
import com.guidinglight.nexusquant.adapter.okx.model.OkxFillRecord;
import com.guidinglight.nexusquant.adapter.okx.service.OkxExchangeAdapter;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;
import com.guidinglight.nexusquant.trading.application.OrderCommandService;
import com.guidinglight.nexusquant.trading.application.OrderLifecycleService;
import com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository;
import com.guidinglight.nexusquant.eventstore.infra.EventStoreAppender;
import com.guidinglight.nexusquant.ledger.contracts.model.LedgerPostingResult;
import com.guidinglight.nexusquant.scheduler.model.PaperTradeRecord;
import com.guidinglight.nexusquant.scheduler.service.port.TradeRepository;
import com.guidinglight.nexusquant.contracts.event.EventPublisherPort;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * OkxRestReconcileServiceTest 覆盖 OKX reconcile 的状态收敛与成交落库行为。
 */
class OkxRestReconcileServiceTest {

    /** 写入后读取 durable identity；模拟已有 void 仓储契约，不用临时 ID 代替真实回读。 */
    private static TradeRepository tradeRepository() {
        TradeRepository repository = Mockito.mock(TradeRepository.class);
        Mockito.doAnswer(invocation -> {
            PaperTradeRecord trade = invocation.getArgument(0);
            when(repository.findByExchangeAndExchangeTradeId(trade.exchange(), trade.exchangeTradeId()))
                    .thenReturn(Optional.of(trade));
            return null;
        }).when(repository).insertWithRequiredEvent(any());
        return repository;
    }


    /** limit 是公开总预算；终态覆盖不得引入第二次独立扫描。 */
    @Test
    void limitOneUsesExactlyOneSharedCandidateScan() {
        var f = new CancelledFixture("ext-c2");
        var statuses = List.of(OrderStatus.SENT, OrderStatus.ACCEPTED, OrderStatus.PARTIALLY_FILLED,
                OrderStatus.CANCEL_REQUESTED, OrderStatus.CANCEL_REJECTED, OrderStatus.FILLED, OrderStatus.CANCELLED);
        when(f.commands.reserveReconciliationCandidates(eq("OKX"), eq(statuses), eq(1))).thenReturn(List.of(f.order));
        assertEquals(0, f.service.reconcileOnce(1));
        verify(f.commands).reserveReconciliationCandidates(eq("OKX"), eq(statuses), eq(1));
        Mockito.verifyNoMoreInteractions(f.commands);
        verify(f.adapter).listTradeReports("BTC-USDT", "ext-c2", "trc-c2");
        verify(f.trades).findAllByOrderId("ord-c2", 1);
        verify(f.lifecycle).reconcileCancelledExecution("ord-c2", "trc-c2");
        Mockito.verifyNoInteractions(f.events, f.ledger);
        verify(f.adapter, never()).getOrder(any());
        verify(f.trades, never()).insertWithRequiredEvent(any());
    }

    @Test
    void cancelledPartialFillIsRecoveredOnceWithoutOrderMutation() {
        var f = new CancelledFixture("ext-c2");
        var report = f.report("OKX", 2001L, "coid-c2", "ext-c2", "fill-c2");
        when(f.adapter.listTradeReports(any(), any(), any())).thenReturn(List.of(report));
        when(f.ledger.postTrade(any())).thenReturn(new LedgerPostingResult(true, false, "POSTED"),
                new LedgerPostingResult(true, true, "IDEMPOTENT_HIT"));
        assertEquals(1, f.service.reconcileOnce(10));
        var trade = ArgumentCaptor.forClass(PaperTradeRecord.class);
        verify(f.trades).insertWithRequiredEvent(trade.capture());
        assertTrue(trade.getValue().qty().compareTo(f.order.qty()) < 0);
        when(f.trades.findByExchangeAndExchangeTradeId("OKX", "fill-c2"))
                .thenReturn(Optional.of(trade.getValue()));
        when(f.trades.findAllByOrderId("ord-c2", 10)).thenReturn(List.of(trade.getValue()));
        assertEquals(0, f.service.reconcileOnce(10));
        verify(f.trades, times(1)).insertWithRequiredEvent(any());
        verify(f.trades, times(2)).ensureRequiredEvent(trade.getValue().tradeId());
        verify(f.ledger, times(2)).postTrade(any());
        verify(f.adapter, times(2)).listTradeReports("BTC-USDT", "ext-c2", "trc-c2");
        verify(f.commands, times(2)).reserveReconciliationCandidates(eq("OKX"), Mockito.argThat(statuses ->
                statuses.contains(OrderStatus.CANCELLED) && statuses.contains(OrderStatus.FILLED)), eq(10));
        verify(f.audit).append("RECONCILE", "OKX_CANCELLED_ORDER_FILL_BACKFILL_COMPLETED", "ord-c2", "trc-c2",
                Map.of("order_id", "ord-c2", "status", "CANCELLED", "external_order_id", "ext-c2", "new_trades", 1));
        verify(f.audit).append("RECONCILE", "OKX_CANCELLED_ORDER_FILL_BACKFILL_COMPLETED", "ord-c2", "trc-c2",
                Map.of("order_id", "ord-c2", "status", "CANCELLED", "external_order_id", "ext-c2", "new_trades", 0));
        f.assertNoOrderMutation();
    }

    @Test
    void cancelledWithoutFillsDoesNotInventExecution() {
        var f = new CancelledFixture("ext-c2");
        assertEquals(0, f.service.reconcileOnce(10));
        verify(f.adapter).listTradeReports("BTC-USDT", "ext-c2", "trc-c2");
        verify(f.trades, never()).insertWithRequiredEvent(any());
        Mockito.verifyNoInteractions(f.events, f.ledger);
        f.assertNoOrderMutation();
    }

    @Test
    void cancelledExistingDurableTradeReplaysLedgerWithoutVenueReport() {
        var f = new CancelledFixture("ext-c2");
        var trade = new PaperTradeRecord("trd-c2", "ord-c2", 2001L, "BTC-USDT", "OKX", "ext-c2", "fill-c2",
                new BigDecimal("100"), new BigDecimal("0.01"), BigDecimal.ZERO, "USDT", "trc-c2", Instant.EPOCH);
        when(f.trades.findAllByOrderId("ord-c2", 10)).thenReturn(List.of(trade));
        when(f.ledger.postTrade(any())).thenReturn(new LedgerPostingResult(true, false, "POSTED"));
        assertEquals(0, f.service.reconcileOnce(10));
        verify(f.trades, never()).insertWithRequiredEvent(any());
        Mockito.verifyNoInteractions(f.events);
        verify(f.ledger).postTrade(Mockito.argThat(request -> request.tradeId().equals("trd-c2")));
        verify(f.audit).append(eq("RECONCILE"), eq("OKX_LEDGER_RECOVERY_COMPLETED"), eq("ord-c2"), eq("trc-c2"), any());
        f.assertNoOrderMutation();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void cancelledWithoutStableExternalIdentitySkipsAmbiguousQuery(String externalId) {
        var f = new CancelledFixture(externalId);
        assertEquals(0, f.service.reconcileOnce(10));
        Mockito.verifyNoInteractions(f.adapter, f.trades, f.ledger, f.events);
        f.assertNoOrderMutation();
    }

    @ParameterizedTest
    @ValueSource(strings = {"venue", "account", "client", "order", "trade"})
    void cancelledMismatchedVenueIdentityFailsClosed(String mismatch) {
        var f = new CancelledFixture("ext-c2");
        when(f.adapter.listTradeReports(any(), any(), any())).thenReturn(List.of(f.report(
                mismatch.equals("venue") ? "OTHER" : "OKX", mismatch.equals("account") ? 999L : 2001L,
                mismatch.equals("client") ? "other" : "coid-c2", mismatch.equals("order") ? "other" : "ext-c2",
                mismatch.equals("trade") ? " " : "fill-c2")));
        assertTrue(assertThrows(IllegalStateException.class, () -> f.service.reconcileOnce(10))
                .getMessage().contains("identity mismatch"));
        verify(f.trades, never()).insertWithRequiredEvent(any());
        Mockito.verifyNoInteractions(f.events, f.ledger);
        f.assertNoOrderMutation();
    }

    @Test
    void cancelledIdenticalDuplicateVenueFillIsIdempotent() {
        var f = new CancelledFixture("ext-c2");
        var report = f.report("OKX", 2001L, "coid-c2", "ext-c2", "fill-c2");
        when(f.adapter.listTradeReports(any(), any(), any())).thenReturn(List.of(report, report));
        when(f.ledger.postTrade(any())).thenReturn(new LedgerPostingResult(true, false, "POSTED"));
        assertEquals(1, f.service.reconcileOnce(10));
        // 相同报告只处理一次；不同内容的同身份回报必须在任何写入前拒绝。
        verify(f.trades, times(1)).insertWithRequiredEvent(any());
        verify(f.trades, times(1)).ensureRequiredEvent(Mockito.anyString());
        verify(f.ledger, times(1)).postTrade(any());
        f.assertNoOrderMutation();
    }

    @Test void conflictingDuplicateFillRejectsWholeResponseBeforeWrites() {
        var f = new CancelledFixture("ext-c2");
        var first = f.report("OKX", 2001L, "coid-c2", "ext-c2", "fill-c2");
        var changed = new AdapterTradeReport("OKX", 2001L, "BTC-USDT", "coid-c2", "ext-c2", "fill-c2", "BUY",
                new BigDecimal("100"), new BigDecimal("0.02"), BigDecimal.ZERO, "USDT", Instant.EPOCH, "x", "trc-c2", "SIM");
        when(f.adapter.listTradeReports(any(), any(), any())).thenReturn(List.of(first, changed));
        assertTrue(assertThrows(IllegalStateException.class, () -> f.service.reconcileOnce(10))
                .getMessage().contains("CONFLICTING_DUPLICATE"));
        verify(f.trades, never()).insertWithRequiredEvent(any());
        Mockito.verifyNoInteractions(f.ledger, f.events, f.lifecycle);
    }

    @Test void unseenDurableFillsCountTowardOverfillBeforeAnyNewPosting() {
        var f = new CancelledFixture("ext-c2");
        var durable = new PaperTradeRecord("old", "ord-c2", 2001L, "BTC-USDT", "OKX", "ext-c2", "old-fill",
                new BigDecimal("100"), new BigDecimal("0.1"), BigDecimal.ZERO, "USDT", "trc-c2", Instant.EPOCH);
        when(f.trades.findAllByOrderId("ord-c2", 10)).thenReturn(List.of(durable));
        when(f.adapter.listTradeReports(any(), any(), any())).thenReturn(List.of(f.report("OKX", 2001L, "coid-c2", "ext-c2", "new-fill")));
        assertTrue(assertThrows(IllegalStateException.class, () -> f.service.reconcileOnce(10)).getMessage().contains("OVERFILL"));
        verify(f.trades, never()).insertWithRequiredEvent(any());
        Mockito.verifyNoInteractions(f.ledger, f.events, f.lifecycle);
    }

    @Test
    void cancelledVenueReportLimitOverflowFailsBeforeWrites() {
        var f = new CancelledFixture("ext-c2");
        when(f.adapter.listTradeReports(any(), any(), any())).thenReturn(Collections.nCopies(11,
                f.report("OKX", 2001L, "coid-c2", "ext-c2", "fill-c2")));
        assertThrows(IllegalStateException.class, () -> f.service.reconcileOnce(10));
        verify(f.audit).append("RECONCILE", "OKX_LEDGER_RECOVERY_INCOMPLETE", "ord-c2", "trc-c2",
                Map.of("order_id", "ord-c2", "reason", "VENUE_REPORT_LIMIT_EXCEEDED"));
        Mockito.verifyNoInteractions(f.trades, f.events, f.ledger);
        f.assertNoOrderMutation();
    }

    @Test
    void cancelledDurableTradeLimitOverflowFailsBeforeWrites() {
        var f = new CancelledFixture("ext-c2");
        when(f.trades.findAllByOrderId("ord-c2", 10)).thenThrow(new IllegalStateException("durable trade limit"));
        assertThrows(IllegalStateException.class, () -> f.service.reconcileOnce(10));
        verify(f.audit).append("RECONCILE", "OKX_LEDGER_RECOVERY_INCOMPLETE", "ord-c2", "trc-c2",
                Map.of("order_id", "ord-c2", "reason", "DURABLE_TRADE_LIMIT_EXCEEDED"));
        verify(f.trades, never()).insertWithRequiredEvent(any());
        Mockito.verifyNoInteractions(f.events, f.ledger);
        f.assertNoOrderMutation();
    }

    private static final class CancelledFixture {
        final OrderCommandService commands = Mockito.mock(OrderCommandService.class);
        final OrderLifecycleService lifecycle = Mockito.mock(OrderLifecycleService.class);
        final OkxExchangeAdapter adapter = Mockito.mock(OkxExchangeAdapter.class);
        final TradeRepository trades = tradeRepository();
        final TradeLedgerGateway ledger = Mockito.mock(TradeLedgerGateway.class);
        final EventPublisherPort events =
                Mockito.mock(EventPublisherPort.class);
        final AuditLogRepository audit = Mockito.mock(AuditLogRepository.class);
        final OkxRestReconcileService service = new OkxRestReconcileService(commands, lifecycle, adapter, trades, ledger, events, audit);
        final OrderRecord order;

        CancelledFixture(String externalId) {
            order = new OrderRecord("ord-c2", 2001L, null, "OKX", "BTC-USDT", "coid-c2", "BUY", "LIMIT",
                    new BigDecimal("100"), new BigDecimal("0.1"), externalId, OrderStatus.CANCELLED, "TEST", "trc-c2");
            when(commands.reserveReconciliationCandidates(eq("OKX"), any(), eq(10))).thenReturn(List.of(order));
            when(lifecycle.reconcileCancelledExecution("ord-c2", "trc-c2")).thenReturn(order);
        }

        AdapterTradeReport report(String venue, Long account, String client, String external, String tradeId) {
            return new AdapterTradeReport(venue, account, "BTC-USDT", client, external, tradeId, "BUY",
                    new BigDecimal("100"), new BigDecimal("0.01"), BigDecimal.ZERO, "USDT", Instant.EPOCH,
                    "synthetic", "trc-c2", "SIM");
        }

        void assertNoOrderMutation() {
            verify(lifecycle, Mockito.atMost(2)).reconcileCancelledExecution("ord-c2", "trc-c2");
            Mockito.verifyNoMoreInteractions(lifecycle);
            verify(adapter, never()).getOrder(any());
            verify(commands, Mockito.atLeastOnce()).reserveReconciliationCandidates(eq("OKX"), any(), eq(10));
            Mockito.verifyNoMoreInteractions(commands);
            verify(audit, never()).append(any(), eq("OKX_FILLED_ORDER_FILL_BACKFILL_COMPLETED"), any(), any(), any());
        }
    }

    @Test
    void shouldRecoverFromCancelRequestedToAcceptedViaCancelRejected() {
        OrderCommandService orderCommandService = Mockito.mock(OrderCommandService.class);
        OrderLifecycleService orderLifecycleService = Mockito.mock(OrderLifecycleService.class);
        OkxExchangeAdapter okxExchangeAdapter = Mockito.mock(OkxExchangeAdapter.class);
        TradeRepository tradeRepository = tradeRepository();
        TradeLedgerGateway tradeLedgerGateway = Mockito.mock(TradeLedgerGateway.class);
        EventStoreAppender eventStoreAppender = Mockito.mock(EventStoreAppender.class);
        AuditLogRepository auditLogRepository = Mockito.mock(AuditLogRepository.class);

        OkxRestReconcileService service = new OkxRestReconcileService(
                orderCommandService,
                orderLifecycleService,
                okxExchangeAdapter,
                tradeRepository,
                tradeLedgerGateway,
                eventStoreAppender,
                auditLogRepository
        );

        OrderRecord cancelRequestedOrder = new OrderRecord(
                "ord-rec-1",
                2001L,
                null,
                "OKX",
                "BTC-USDT",
                "coid-rec-1",
                "BUY",
                "LIMIT",
                new BigDecimal("10000.00000000"),
                new BigDecimal("0.00100000"),
                "ext-rec-1",
                OrderStatus.CANCEL_REQUESTED,
                "TEST",
                "trc-rec-1"
        );

        when(orderCommandService.reserveReconciliationCandidates(eq("OKX"), any(), eq(10))).thenReturn(List.of(cancelRequestedOrder));
        when(okxExchangeAdapter.getOrder(any())).thenReturn(new AdapterOrderSnapshot(
                cancelRequestedOrder.accountId(),
                cancelRequestedOrder.venue(),
                cancelRequestedOrder.symbol(),
                cancelRequestedOrder.clientOrderId(),
                cancelRequestedOrder.externalOrderId(),
                "ACCEPTED",
                null,
                null,
                null,
                null,
                null,
                "okx_reconcile_snapshot",
                cancelRequestedOrder.traceId()
        ));
        when(orderCommandService.findByOrderId("ord-rec-1")).thenReturn(
                Optional.of(cancelRequestedOrder),
                Optional.of(cancelRequestedOrder.withStatus(OrderStatus.ACCEPTED, "RECONCILE_STATUS_ALIGN"))
        );
        when(okxExchangeAdapter.listTradeReports("BTC-USDT", "ext-rec-1", "trc-rec-1")).thenReturn(List.of());
        when(tradeRepository.findAllByOrderId("ord-rec-1", 10)).thenReturn(List.of());

        int newTrades = service.reconcileOnce(10);

        assertEquals(0, newTrades);
        verify(orderLifecycleService).rejectCancel("ord-rec-1", "RECONCILE_CANCEL_REJECTED", "trc-rec-1");
        verify(orderLifecycleService).applyExternalStatus("ord-rec-1", OrderStatus.ACCEPTED, "RECONCILE_STATUS_ALIGN", "trc-rec-1");
    }

    @Test
    void shouldInsertTradeWithExternalOrderIdFromFill() {
        OrderCommandService orderCommandService = Mockito.mock(OrderCommandService.class);
        OrderLifecycleService orderLifecycleService = Mockito.mock(OrderLifecycleService.class);
        OkxExchangeAdapter okxExchangeAdapter = Mockito.mock(OkxExchangeAdapter.class);
        TradeRepository tradeRepository = tradeRepository();
        TradeLedgerGateway tradeLedgerGateway = Mockito.mock(TradeLedgerGateway.class);
        EventStoreAppender eventStoreAppender = Mockito.mock(EventStoreAppender.class);
        AuditLogRepository auditLogRepository = Mockito.mock(AuditLogRepository.class);

        OkxRestReconcileService service = new OkxRestReconcileService(
                orderCommandService,
                orderLifecycleService,
                okxExchangeAdapter,
                tradeRepository,
                tradeLedgerGateway,
                eventStoreAppender,
                auditLogRepository
        );

        OrderRecord acceptedOrder = new OrderRecord(
                "ord-rec-2",
                2001L,
                null,
                "OKX",
                "BTC-USDT",
                "coid-rec-2",
                "BUY",
                "LIMIT",
                new BigDecimal("10000.00000000"),
                new BigDecimal("0.00100000"),
                "ext-rec-2",
                OrderStatus.ACCEPTED,
                "TEST",
                "trc-rec-2"
        );

        OkxFillRecord fillRecord = new OkxFillRecord(
                "fill-rec-2",
                "ext-rec-2",
                "BTC-USDT",
                "BUY",
                new BigDecimal("10000.00000000"),
                new BigDecimal("0.00100000"),
                new BigDecimal("-0.0001"),
                "USDT",
                Instant.parse("2026-03-06T01:00:00Z")
        );

        when(orderCommandService.reserveReconciliationCandidates(eq("OKX"), any(), eq(10))).thenReturn(List.of(acceptedOrder));
        when(okxExchangeAdapter.getOrder(any())).thenReturn(new AdapterOrderSnapshot(
                acceptedOrder.accountId(),
                acceptedOrder.venue(),
                acceptedOrder.symbol(),
                acceptedOrder.clientOrderId(),
                acceptedOrder.externalOrderId(),
                "ACCEPTED",
                null,
                null,
                null,
                null,
                null,
                "okx_reconcile_snapshot",
                acceptedOrder.traceId()
        ));
        when(orderCommandService.findByOrderId("ord-rec-2")).thenReturn(Optional.of(acceptedOrder));
        when(okxExchangeAdapter.listTradeReports("BTC-USDT", "ext-rec-2", "trc-rec-2")).thenReturn(List.of(
                new AdapterTradeReport(
                        "OKX",
                        acceptedOrder.accountId(),
                        fillRecord.symbol(),
                        acceptedOrder.clientOrderId(),
                        fillRecord.externalOrderId(),
                        fillRecord.exchangeTradeId(),
                        fillRecord.side(),
                        fillRecord.price(),
                        fillRecord.qty(),
                        fillRecord.fee(),
                        fillRecord.feeCurrency(),
                        fillRecord.ts(),
                        fillRecord.toString(),
                        acceptedOrder.traceId(),
                        "SIM"
                )
        ));
        when(tradeRepository.findByExchangeAndExchangeTradeId("OKX", "fill-rec-2")).thenReturn(Optional.empty());
        when(tradeRepository.findAllByOrderId("ord-rec-2", 10)).thenReturn(List.of());
        when(tradeLedgerGateway.postTrade(any())).thenReturn(new LedgerPostingResult(true, false, "OK"));

        int newTrades = service.reconcileOnce(10);

        assertEquals(1, newTrades);
        ArgumentCaptor<PaperTradeRecord> tradeCaptor = ArgumentCaptor.forClass(PaperTradeRecord.class);
        verify(tradeRepository, times(1)).insertWithRequiredEvent(tradeCaptor.capture());
        assertEquals("ext-rec-2", tradeCaptor.getValue().externalOrderId());
    }
    @Test
    void shouldBackfillFillsForFilledOrderWithoutTradeFacts() {
        OrderCommandService orderCommandService = Mockito.mock(OrderCommandService.class);
        OrderLifecycleService orderLifecycleService = Mockito.mock(OrderLifecycleService.class);
        OkxExchangeAdapter okxExchangeAdapter = Mockito.mock(OkxExchangeAdapter.class);
        TradeRepository tradeRepository = tradeRepository();
        TradeLedgerGateway tradeLedgerGateway = Mockito.mock(TradeLedgerGateway.class);
        EventStoreAppender eventStoreAppender = Mockito.mock(EventStoreAppender.class);
        AuditLogRepository auditLogRepository = Mockito.mock(AuditLogRepository.class);

        OkxRestReconcileService service = new OkxRestReconcileService(
                orderCommandService,
                orderLifecycleService,
                okxExchangeAdapter,
                tradeRepository,
                tradeLedgerGateway,
                eventStoreAppender,
                auditLogRepository
        );

        OrderRecord filledOrder = new OrderRecord(
                "ord-rec-3",
                2001L,
                null,
                "OKX",
                "BTC-USDT",
                "coid-rec-3",
                "SELL",
                "MARKET",
                null,
                new BigDecimal("0.00002000"),
                "ext-rec-3",
                OrderStatus.FILLED,
                "RECONCILE_STATUS_ALIGN",
                "trc-rec-3"
        );

        OkxFillRecord fillRecord = new OkxFillRecord(
                "fill-rec-3",
                "ext-rec-3",
                "BTC-USDT",
                "SELL",
                new BigDecimal("70812.20000000"),
                new BigDecimal("0.00002000"),
                new BigDecimal("-0.0015"),
                "USDT",
                Instant.parse("2026-03-14T05:58:17Z")
        );

        when(orderCommandService.reserveReconciliationCandidates(eq("OKX"), any(), eq(10))).thenReturn(List.of(filledOrder));
        when(tradeRepository.findAllByOrderId("ord-rec-3", 10)).thenReturn(List.of());
        when(okxExchangeAdapter.listTradeReports("BTC-USDT", "ext-rec-3", "trc-rec-3")).thenReturn(List.of(
                new AdapterTradeReport(
                        "OKX",
                        filledOrder.accountId(),
                        fillRecord.symbol(),
                        filledOrder.clientOrderId(),
                        fillRecord.externalOrderId(),
                        fillRecord.exchangeTradeId(),
                        fillRecord.side(),
                        fillRecord.price(),
                        fillRecord.qty(),
                        fillRecord.fee(),
                        fillRecord.feeCurrency(),
                        fillRecord.ts(),
                        fillRecord.toString(),
                        filledOrder.traceId(),
                        "SIM"
                )
        ));
        when(tradeRepository.findByExchangeAndExchangeTradeId("OKX", "fill-rec-3")).thenReturn(Optional.empty());
        when(tradeLedgerGateway.postTrade(any())).thenReturn(new LedgerPostingResult(true, false, "OK"));

        int newTrades = service.reconcileOnce(10);

        assertEquals(1, newTrades);
        verify(okxExchangeAdapter, never()).getOrder(any());
        verify(orderLifecycleService, never()).applyExternalStatus(any(), any(), any(), any());
        verify(tradeRepository, times(1)).insertWithRequiredEvent(any());
        verify(tradeRepository, times(1)).ensureRequiredEvent(Mockito.anyString());
        verify(tradeLedgerGateway, times(1)).postTrade(any());
    }

    @Test
    void shouldEnsureLedgerConvergenceWhenFilledOrderTradeAlreadyExists() {
        OrderCommandService orderCommandService = Mockito.mock(OrderCommandService.class);
        OrderLifecycleService orderLifecycleService = Mockito.mock(OrderLifecycleService.class);
        OkxExchangeAdapter okxExchangeAdapter = Mockito.mock(OkxExchangeAdapter.class);
        TradeRepository tradeRepository = tradeRepository();
        TradeLedgerGateway tradeLedgerGateway = Mockito.mock(TradeLedgerGateway.class);
        EventStoreAppender eventStoreAppender = Mockito.mock(EventStoreAppender.class);
        AuditLogRepository auditLogRepository = Mockito.mock(AuditLogRepository.class);

        OkxRestReconcileService service = new OkxRestReconcileService(
                orderCommandService,
                orderLifecycleService,
                okxExchangeAdapter,
                tradeRepository,
                tradeLedgerGateway,
                eventStoreAppender,
                auditLogRepository
        );

        OrderRecord filledOrder = new OrderRecord(
                "ord-rec-4",
                2001L,
                null,
                "OKX",
                "BTC-USDT",
                "coid-rec-4",
                "SELL",
                "MARKET",
                null,
                new BigDecimal("0.00002000"),
                "ext-rec-4",
                OrderStatus.FILLED,
                "RECONCILE_STATUS_ALIGN",
                "trc-rec-4"
        );

        PaperTradeRecord existingTrade = new PaperTradeRecord(
                "trd-rec-4",
                "ord-rec-4",
                2001L,
                "BTC-USDT",
                "OKX",
                "ext-rec-4",
                "fill-rec-4",
                new BigDecimal("70812.20000000"),
                new BigDecimal("0.00002000"),
                new BigDecimal("0.0015"),
                "USDT",
                "trc-rec-4",
                Instant.parse("2026-03-14T05:58:18Z")
        );

        when(orderCommandService.reserveReconciliationCandidates(eq("OKX"), any(), eq(10))).thenReturn(List.of(filledOrder));
        when(okxExchangeAdapter.listTradeReports("BTC-USDT", "ext-rec-4", "trc-rec-4")).thenReturn(List.of());
        when(tradeRepository.findAllByOrderId("ord-rec-4", 10)).thenReturn(List.of(existingTrade));
        when(tradeLedgerGateway.postTrade(any())).thenReturn(new LedgerPostingResult(true, false, "POSTED"));

        int newTrades = service.reconcileOnce(10);

        assertEquals(0, newTrades);
        verify(okxExchangeAdapter, never()).getOrder(any());
        verify(okxExchangeAdapter, times(1)).listTradeReports("BTC-USDT", "ext-rec-4", "trc-rec-4");
        verify(tradeRepository, never()).insertWithRequiredEvent(any());
        verify(tradeLedgerGateway, times(1)).postTrade(any());
        verify(eventStoreAppender, never()).append(eq("trade.event.v1"), any());
        verify(auditLogRepository, times(1)).append(
                eq("RECONCILE"),
                eq("OKX_LEDGER_RECOVERY_COMPLETED"),
                eq("ord-rec-4"),
                eq("trc-rec-4"),
                any()
        );
    }
}


