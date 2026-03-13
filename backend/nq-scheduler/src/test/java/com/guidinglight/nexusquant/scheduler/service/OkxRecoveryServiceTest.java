package com.guidinglight.nexusquant.scheduler.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderQuery;
import com.guidinglight.nexusquant.adapter.api.model.AdapterOrderSnapshot;
import com.guidinglight.nexusquant.adapter.okx.service.OkxApiException;
import com.guidinglight.nexusquant.adapter.okx.service.OkxErrorCode;
import com.guidinglight.nexusquant.adapter.okx.service.OkxExchangeAdapter;
import com.guidinglight.nexusquant.contracts.event.TopicNames;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.core.model.OrderRecord;
import com.guidinglight.nexusquant.core.service.OrderCommandService;
import com.guidinglight.nexusquant.core.service.OrderLifecycleService;
import com.guidinglight.nexusquant.core.service.port.AuditLogRepository;
import com.guidinglight.nexusquant.infra.eventstore.EventStoreAppender;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * OkxRecoveryServiceTest 验证恢复流程对 OKX 51603（订单不存在）的容错降级。
 */
class OkxRecoveryServiceTest {

    @Test
    void shouldNotFailStartupWhenQueryOrderReturnsOrderNotFound() {
        OrderCommandService orderCommandService = Mockito.mock(OrderCommandService.class);
        OrderLifecycleService orderLifecycleService = Mockito.mock(OrderLifecycleService.class);
        OkxExchangeAdapter okxExchangeAdapter = Mockito.mock(OkxExchangeAdapter.class);
        OkxRestReconcileService okxRestReconcileService = Mockito.mock(OkxRestReconcileService.class);
        AuditLogRepository auditLogRepository = Mockito.mock(AuditLogRepository.class);
        EventStoreAppender eventStoreAppender = Mockito.mock(EventStoreAppender.class);

        OkxRecoveryService recoveryService = new OkxRecoveryService(
                orderCommandService,
                orderLifecycleService,
                okxExchangeAdapter,
                okxRestReconcileService,
                auditLogRepository,
                eventStoreAppender
        );

        OrderRecord notFoundOrder = order("ord-nf-1", "coid-nf-1", "ext-nf-1", OrderStatus.ACCEPTED);
        OrderRecord normalOrder = order("ord-ok-1", "coid-ok-1", "ext-ok-1", OrderStatus.SENT);
        when(orderCommandService.findOrdersByStatuses(any(), anyInt())).thenReturn(List.of(notFoundOrder, normalOrder));
        when(okxExchangeAdapter.listOpenOrders(any())).thenReturn(List.of());
        when(okxExchangeAdapter.getOrder(any(AdapterOrderQuery.class))).thenAnswer(invocation -> {
            AdapterOrderQuery query = invocation.getArgument(0);
            if ("coid-nf-1".equals(query.clientOrderId())) {
                throw new OkxApiException(
                        "order does not exist",
                        200,
                        "/api/v5/trade/order",
                        "51603",
                        OkxErrorCode.ORDER_NOT_FOUND,
                        "trc-recovery-1"
                );
            }
            return new AdapterOrderSnapshot(
                    query.accountId(),
                    query.venue(),
                    query.symbol(),
                    query.clientOrderId(),
                    query.externalOrderId(),
                    "ACCEPTED",
                    null,
                    null,
                    null,
                    null,
                    null,
                    "okx_recovery_snapshot",
                    query.traceId()
            );
        });
        when(orderLifecycleService.requestCancel(
                eq("ord-nf-1"),
                eq("ORDER_NOT_FOUND/OKX_51603"),
                eq("trc-recovery-1")
        )).thenReturn(notFoundOrder.withStatus(OrderStatus.CANCEL_REQUESTED, "ORDER_NOT_FOUND/OKX_51603"));
        when(orderLifecycleService.cancel(
                eq("ord-nf-1"),
                eq("ORDER_NOT_FOUND/OKX_51603"),
                eq("trc-recovery-1")
        )).thenReturn(notFoundOrder.withStatus(OrderStatus.CANCELLED, "ORDER_NOT_FOUND/OKX_51603"));
        when(okxRestReconcileService.reconcileOnce(anyInt())).thenReturn(0);

        assertDoesNotThrow(() -> recoveryService.rebuild("trc-recovery-1"));

        verify(orderLifecycleService).requestCancel("ord-nf-1", "ORDER_NOT_FOUND/OKX_51603", "trc-recovery-1");
        verify(orderLifecycleService).cancel("ord-nf-1", "ORDER_NOT_FOUND/OKX_51603", "trc-recovery-1");
        verify(auditLogRepository, atLeastOnce()).append(
                eq("RECOVERY"),
                eq("RECOVERY_QUERY_ORDER_NOT_FOUND"),
                eq("ord-nf-1"),
                eq("trc-recovery-1"),
                any()
        );
        verify(eventStoreAppender, atLeastOnce()).append(eq(TopicNames.AUDIT_EVENT_V1), any());
        verify(eventStoreAppender, atLeastOnce()).append(eq(TopicNames.ORDER_EVENT_V1), any());
        verify(okxExchangeAdapter, times(2)).getOrder(any(AdapterOrderQuery.class));
        verify(okxRestReconcileService).reconcileOnce(500);
    }

    private OrderRecord order(String orderId, String clientOrderId, String externalOrderId, OrderStatus status) {
        return new OrderRecord(
                orderId,
                2001L,
                null,
                "OKX",
                "BTC-USDT",
                clientOrderId,
                "BUY",
                "LIMIT",
                new BigDecimal("10000.00000000"),
                new BigDecimal("0.00100000"),
                externalOrderId,
                status,
                "TEST",
                "trc-recovery-1"
        );
    }
}
