package com.guidinglight.nexusquant.scheduler.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderSnapshot;
import com.guidinglight.nexusquant.adapter.okx.model.OkxFillRecord;
import com.guidinglight.nexusquant.adapter.okx.service.OkxExchangeAdapter;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.core.model.OrderRecord;
import com.guidinglight.nexusquant.core.service.OrderCommandService;
import com.guidinglight.nexusquant.core.service.OrderLifecycleService;
import com.guidinglight.nexusquant.core.service.port.AuditLogRepository;
import com.guidinglight.nexusquant.infra.eventstore.EventStoreAppender;
import com.guidinglight.nexusquant.ledger.model.LedgerPostingResult;
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
        when(okxExchangeAdapter.listFills("BTC-USDT", "ext-rec-1", "trc-rec-1")).thenReturn(List.of());

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
        when(okxExchangeAdapter.listFills("BTC-USDT", "ext-rec-2", "trc-rec-2")).thenReturn(List.of(fillRecord));
        when(tradeRepository.findByExchangeAndExchangeTradeId("OKX", "fill-rec-2")).thenReturn(Optional.empty());
        when(tradeLedgerGateway.postTrade(any())).thenReturn(new LedgerPostingResult(true, false, "OK"));

        int newTrades = service.reconcileOnce(10);

        assertEquals(1, newTrades);
        ArgumentCaptor<PaperTradeRecord> tradeCaptor = ArgumentCaptor.forClass(PaperTradeRecord.class);
        verify(tradeRepository, times(1)).insert(tradeCaptor.capture());
        assertEquals("ext-rec-2", tradeCaptor.getValue().externalOrderId());
    }
}
