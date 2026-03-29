package com.guidinglight.nexusquant.scheduler.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guidinglight.nexusquant.adapter.binance.ws.BinanceWsClient;
import com.guidinglight.nexusquant.adapter.binance.ws.BinanceWsConnectionListener;
import com.guidinglight.nexusquant.contracts.event.TopicNames;
import com.guidinglight.nexusquant.trading.domain.port.AuditLogRepository;
import com.guidinglight.nexusquant.infra.eventstore.EventStoreAppender;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/**
 * BinanceWsDegradeReconcileCoordinatorTest 验证 PR-BW3 的断线降级与限流策略。
 */
class BinanceWsDegradeReconcileCoordinatorTest {

    /**
     * 验证断线回调会触发一次受限 reconcile，并在 cooldown 窗口内去抖。
     */
    @Test
    void shouldTriggerLimitedReconcileOnDisconnectAndDebounce() {
        BinanceWsClient wsClient = mock(BinanceWsClient.class);
        BinanceRestReconcileService reconcileService = mock(BinanceRestReconcileService.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        EventStoreAppender eventStoreAppender = mock(EventStoreAppender.class);
        AtomicReference<BinanceWsConnectionListener> listenerRef = new AtomicReference<>();
        doAnswer(invocation -> {
            listenerRef.set(invocation.getArgument(0));
            return null;
        }).when(wsClient).addConnectionListener(any());
        when(reconcileService.reconcileOnce(77)).thenReturn(0);

        new BinanceWsDegradeReconcileCoordinator(
                wsClient,
                reconcileService,
                auditLogRepository,
                eventStoreAppender,
                77,
                60_000L,
                2
        );

        listenerRef.get().onDisconnected("listener_close", 1, 1000L, "trc-binance-ws-degrade-1");
        listenerRef.get().onDisconnected("listener_error", 2, 2000L, "trc-binance-ws-degrade-2");

        verify(reconcileService, times(1)).reconcileOnce(77);
        verify(auditLogRepository).append(
                eq("WS"),
                eq("BINANCE_WS_DISCONNECTED"),
                eq("BINANCE_WS"),
                eq("trc-binance-ws-degrade-1"),
                any()
        );
        verify(auditLogRepository, never()).append(
                eq("WS"),
                eq("BINANCE_WS_RECONCILE_DEGRADE_SKIPPED_COOLDOWN"),
                eq("BINANCE_WS"),
                eq("trc-binance-ws-degrade-2"),
                any()
        );
        verify(eventStoreAppender, times(2)).append(eq(TopicNames.AUDIT_EVENT_V1), any());
    }

    /**
     * 验证连续 connect_failed 达到阈值后才触发 reconcile。
     */
    @Test
    void shouldTriggerReconcileAfterReconnectFailThreshold() {
        BinanceWsClient wsClient = mock(BinanceWsClient.class);
        BinanceRestReconcileService reconcileService = mock(BinanceRestReconcileService.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        EventStoreAppender eventStoreAppender = mock(EventStoreAppender.class);
        AtomicReference<BinanceWsConnectionListener> listenerRef = new AtomicReference<>();
        doAnswer(invocation -> {
            listenerRef.set(invocation.getArgument(0));
            return null;
        }).when(wsClient).addConnectionListener(any());
        when(reconcileService.reconcileOnce(66)).thenReturn(1);

        new BinanceWsDegradeReconcileCoordinator(
                wsClient,
                reconcileService,
                auditLogRepository,
                eventStoreAppender,
                66,
                0L,
                2
        );

        listenerRef.get().onDisconnected("connect_failed", 1, 500L, "trc-binance-ws-sub-1");
        listenerRef.get().onDisconnected("connect_failed", 2, 1000L, "trc-binance-ws-sub-2");

        verify(reconcileService, times(1)).reconcileOnce(66);
        verify(auditLogRepository, never()).append(
                eq("WS"),
                eq("BINANCE_WS_RECONNECT_FAILED_OBSERVED"),
                eq("BINANCE_WS"),
                eq("trc-binance-ws-sub-1"),
                any()
        );
        verify(auditLogRepository).append(
                eq("WS"),
                eq("BINANCE_WS_RECONNECT_FAILED_THRESHOLD"),
                eq("BINANCE_WS"),
                eq("trc-binance-ws-sub-2"),
                any()
        );
    }

    /**
     * 验证 listenKey 失效会触发一次受限 reconcile。
     */
    @Test
    void shouldTriggerReconcileWhenListenKeyExpires() {
        BinanceWsClient wsClient = mock(BinanceWsClient.class);
        BinanceRestReconcileService reconcileService = mock(BinanceRestReconcileService.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        EventStoreAppender eventStoreAppender = mock(EventStoreAppender.class);
        AtomicReference<BinanceWsConnectionListener> listenerRef = new AtomicReference<>();
        doAnswer(invocation -> {
            listenerRef.set(invocation.getArgument(0));
            return null;
        }).when(wsClient).addConnectionListener(any());
        when(reconcileService.reconcileOnce(88)).thenReturn(0);

        new BinanceWsDegradeReconcileCoordinator(
                wsClient,
                reconcileService,
                auditLogRepository,
                eventStoreAppender,
                88,
                0L,
                2
        );

        listenerRef.get().onListenKeyExpired("-1125", "This listenKey does not exist.", "trc-binance-ws-lk-1");

        verify(reconcileService, times(1)).reconcileOnce(88);
        verify(auditLogRepository).append(
                eq("WS"),
                eq("BINANCE_WS_LISTENKEY_EXPIRED"),
                eq("BINANCE_WS"),
                eq("trc-binance-ws-lk-1"),
                any()
        );
        verify(eventStoreAppender, times(2)).append(eq(TopicNames.AUDIT_EVENT_V1), any());
    }
}

