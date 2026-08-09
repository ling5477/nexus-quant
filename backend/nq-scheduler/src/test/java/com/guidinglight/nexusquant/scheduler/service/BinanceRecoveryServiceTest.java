package com.guidinglight.nexusquant.scheduler.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guidinglight.nexusquant.adapter.api.model.AdapterOpenOrdersQuery;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderSnapshot;
import com.guidinglight.nexusquant.adapter.binance.service.BinanceExchangeAdapter;
import com.guidinglight.nexusquant.contracts.event.TopicNames;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;
import com.guidinglight.nexusquant.trading.application.RecoveryReport;
import com.guidinglight.nexusquant.trading.application.OrderCommandService;
import com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository;
import com.guidinglight.nexusquant.eventstore.infra.EventStoreAppender;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * BinanceRecoveryServiceTest 验证 Binance manual recovery 只回填本 venue 订单，并复用 reconcile 收敛。
 */
class BinanceRecoveryServiceTest {

    @Test
    void shouldLinkExternalOrderIdAndRunReconcileForBinanceOnly() {
        OrderCommandService orderCommandService = Mockito.mock(OrderCommandService.class);
        BinanceExchangeAdapter binanceExchangeAdapter = Mockito.mock(BinanceExchangeAdapter.class);
        BinanceRestReconcileService binanceRestReconcileService = Mockito.mock(BinanceRestReconcileService.class);
        AuditLogRepository auditLogRepository = Mockito.mock(AuditLogRepository.class);
        EventStoreAppender eventStoreAppender = Mockito.mock(EventStoreAppender.class);

        BinanceRecoveryService recoveryService = new BinanceRecoveryService(
                orderCommandService,
                binanceExchangeAdapter,
                binanceRestReconcileService,
                auditLogRepository,
                eventStoreAppender
        );

        OrderRecord binanceOrder = order("ord-binance-rec-1", "BINANCE", "cid-binance-rec-1", null, OrderStatus.ACCEPTED);
        OrderRecord okxOrder = order("ord-okx-rec-1", "OKX", "cid-okx-rec-1", null, OrderStatus.ACCEPTED);
        when(orderCommandService.findOrdersByStatuses(any(), anyInt())).thenReturn(List.of(binanceOrder, okxOrder));
        when(binanceExchangeAdapter.listOpenOrders(any(AdapterOpenOrdersQuery.class))).thenReturn(List.of(
                new AdapterOrderSnapshot(
                        2001L,
                        "BINANCE",
                        "BTC-USDT",
                        "cid-binance-rec-1",
                        "90001",
                        "ACCEPTED",
                        new BigDecimal("1000"),
                        new BigDecimal("0.01"),
                        BigDecimal.ZERO,
                        null,
                        null,
                        "binance_open_order",
                        "trc-binance-recovery-1"
                )
        ));
        when(binanceRestReconcileService.reconcileOnce(eq(500))).thenReturn(2);

        RecoveryReport report = assertDoesNotThrow(() -> recoveryService.rebuild("trc-binance-recovery-1"));

        assertEquals(1L, report.processedEventCount());
        assertEquals(2L, report.processedLedgerCount());
        assertEquals(1L, report.invalidTransitionCount());
        verify(binanceExchangeAdapter, times(1)).listOpenOrders(any(AdapterOpenOrdersQuery.class));
        verify(orderCommandService, times(1)).linkExternalOrderId("ord-binance-rec-1", "90001", "trc-binance-recovery-1");
        verify(binanceRestReconcileService, times(1)).reconcileOnce(500);
        verify(auditLogRepository, times(1)).append(
                eq("RECOVERY"),
                eq("BINANCE_RECOVERY_COMPLETED"),
                eq("trc-binance-recovery-1"),
                eq("trc-binance-recovery-1"),
                any()
        );
        verify(eventStoreAppender, times(1)).append(eq(TopicNames.AUDIT_EVENT_V1), any());
    }

    private OrderRecord order(
            String orderId,
            String venue,
            String clientOrderId,
            String externalOrderId,
            OrderStatus status
    ) {
        return new OrderRecord(
                orderId,
                2001L,
                null,
                venue,
                "BTC-USDT",
                clientOrderId,
                "BUY",
                "LIMIT",
                new BigDecimal("1000.00"),
                new BigDecimal("0.01000000"),
                externalOrderId,
                status,
                "TEST",
                "trc-binance-recovery-1"
        );
    }
}


