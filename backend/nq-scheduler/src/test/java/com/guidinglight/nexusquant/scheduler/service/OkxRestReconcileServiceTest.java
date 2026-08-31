package com.guidinglight.nexusquant.scheduler.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * OkxRestReconcileServiceTest 覆盖 OKX reconcile 的状态收敛与成交落库行为。
 */
class OkxRestReconcileServiceTest {

    @Test
    void shouldRecoverFromCancelRequestedToAcceptedViaCancelRejected() {
        OrderCommandService orderCommandService = Mockito.mock(OrderCommandService.class);
        OrderLifecycleService orderLifecycleService = Mockito.mock(OrderLifecycleService.class);
        OkxExchangeAdapter okxExchangeAdapter = Mockito.mock(OkxExchangeAdapter.class);
        TradeRepository tradeRepository = Mockito.mock(TradeRepository.class);
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

        when(orderCommandService.findOrdersByStatuses(any(), eq(10))).thenReturn(List.of(cancelRequestedOrder));
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
        TradeRepository tradeRepository = Mockito.mock(TradeRepository.class);
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

        when(orderCommandService.findOrdersByStatuses(any(), eq(10))).thenReturn(List.of(acceptedOrder));
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
        verify(tradeRepository, times(1)).insert(tradeCaptor.capture());
        assertEquals("ext-rec-2", tradeCaptor.getValue().externalOrderId());
    }
    @Test
    void shouldBackfillFillsForFilledOrderWithoutTradeFacts() {
        OrderCommandService orderCommandService = Mockito.mock(OrderCommandService.class);
        OrderLifecycleService orderLifecycleService = Mockito.mock(OrderLifecycleService.class);
        OkxExchangeAdapter okxExchangeAdapter = Mockito.mock(OkxExchangeAdapter.class);
        TradeRepository tradeRepository = Mockito.mock(TradeRepository.class);
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

        when(orderCommandService.findOrdersByStatuses(any(), eq(10))).thenReturn(List.of(filledOrder));
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
        verify(eventStoreAppender, times(1)).append(eq("trade.event.v1"), any());
        verify(tradeLedgerGateway, times(1)).postTrade(any());
    }

    @Test
    void shouldEnsureLedgerConvergenceWhenFilledOrderTradeAlreadyExists() {
        OrderCommandService orderCommandService = Mockito.mock(OrderCommandService.class);
        OrderLifecycleService orderLifecycleService = Mockito.mock(OrderLifecycleService.class);
        OkxExchangeAdapter okxExchangeAdapter = Mockito.mock(OkxExchangeAdapter.class);
        TradeRepository tradeRepository = Mockito.mock(TradeRepository.class);
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

        when(orderCommandService.findOrdersByStatuses(any(), eq(10))).thenReturn(List.of(filledOrder));
        when(okxExchangeAdapter.listTradeReports("BTC-USDT", "ext-rec-4", "trc-rec-4")).thenReturn(List.of());
        when(tradeRepository.findAllByOrderId("ord-rec-4", 10)).thenReturn(List.of(existingTrade));
        when(tradeLedgerGateway.postTrade(any())).thenReturn(new LedgerPostingResult(true, false, "POSTED"));

        int newTrades = service.reconcileOnce(10);

        assertEquals(0, newTrades);
        verify(okxExchangeAdapter, never()).getOrder(any());
        verify(okxExchangeAdapter, times(1)).listTradeReports("BTC-USDT", "ext-rec-4", "trc-rec-4");
        verify(tradeRepository, never()).insert(any());
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


