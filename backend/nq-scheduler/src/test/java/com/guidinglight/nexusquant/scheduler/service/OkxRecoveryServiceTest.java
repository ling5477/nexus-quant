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

    /**
     * 当 query-confirm 返回 ORDER_NOT_FOUND 时，恢复流程必须不中断并继续处理后续订单。
     * <p>
     * Why:
     * 真实盘历史脏状态下，某些外部订单可能已不存在；恢复流程需要记录证据并推进终态，
     * 不能因为单笔异常导致应用启动失败。
     */
    @Test
    void shouldNotFailStartupWhenQueryOrderReturnsOrderNotFound() {
        OrderCommandService orderCommandService = Mockito.mock(OrderCommandService.class);
        OkxExchangeAdapter okxExchangeAdapter = Mockito.mock(OkxExchangeAdapter.class);
        OkxRestReconcileService okxRestReconcileService = Mockito.mock(OkxRestReconcileService.class);
        AuditLogRepository auditLogRepository = Mockito.mock(AuditLogRepository.class);
        EventStoreAppender eventStoreAppender = Mockito.mock(EventStoreAppender.class);

        OkxRecoveryService recoveryService = new OkxRecoveryService(
                orderCommandService,
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
                    query.traceId()
            );
        });
        when(orderCommandService.transitionOrder(
                eq("ord-nf-1"),
                eq(OrderStatus.CANCEL_REQUESTED),
                eq("ORDER_NOT_FOUND/OKX_51603"),
                eq("trc-recovery-1")
        )).thenReturn(notFoundOrder.withStatus(OrderStatus.CANCEL_REQUESTED, "ORDER_NOT_FOUND/OKX_51603"));
        when(orderCommandService.transitionOrder(
                eq("ord-nf-1"),
                eq(OrderStatus.CANCELLED),
                eq("ORDER_NOT_FOUND/OKX_51603"),
                eq("trc-recovery-1")
        )).thenReturn(notFoundOrder.withStatus(OrderStatus.CANCELLED, "ORDER_NOT_FOUND/OKX_51603"));
        when(okxRestReconcileService.reconcileOnce(anyInt())).thenReturn(0);

        assertDoesNotThrow(() -> recoveryService.rebuild("trc-recovery-1"));

        verify(orderCommandService).transitionOrder(
                "ord-nf-1",
                OrderStatus.CANCEL_REQUESTED,
                "ORDER_NOT_FOUND/OKX_51603",
                "trc-recovery-1"
        );
        verify(orderCommandService).transitionOrder(
                "ord-nf-1",
                OrderStatus.CANCELLED,
                "ORDER_NOT_FOUND/OKX_51603",
                "trc-recovery-1"
        );
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
