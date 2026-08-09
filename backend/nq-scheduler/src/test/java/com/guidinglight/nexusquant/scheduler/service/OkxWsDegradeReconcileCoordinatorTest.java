package com.guidinglight.nexusquant.scheduler.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.guidinglight.nexusquant.adapter.okx.service.OkxWsClient;
import com.guidinglight.nexusquant.adapter.okx.service.OkxWsConnectionListener;
import com.guidinglight.nexusquant.contracts.event.TopicNames;
import com.guidinglight.nexusquant.audit.domain.port.AuditLogRepository;
import com.guidinglight.nexusquant.eventstore.infra.EventStoreAppender;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/**
 * OkxWsDegradeReconcileCoordinatorTest 验证 PR-W3 的断线降级与限流策略。
 */
class OkxWsDegradeReconcileCoordinatorTest {

    /**
     * 验证 reconnect 回调会触发一次受限 reconcile，并在 cooldown 窗口内去抖。
     */
    @Test
    void shouldTriggerLimitedReconcileOnReconnectAndDebounce() {
        OkxWsClient wsClient = mock(OkxWsClient.class);
        OkxRestReconcileService reconcileService = mock(OkxRestReconcileService.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        EventStoreAppender eventStoreAppender = mock(EventStoreAppender.class);
        AtomicReference<OkxWsConnectionListener> listenerRef = new AtomicReference<>();
        doAnswer(invocation -> {
            listenerRef.set(invocation.getArgument(0));
            return null;
        }).when(wsClient).addConnectionListener(any());
        when(reconcileService.reconcileOnce(77)).thenReturn(0);

        new OkxWsDegradeReconcileCoordinator(
                wsClient,
                reconcileService,
                auditLogRepository,
                eventStoreAppender,
                77,
                60_000L,
                3
        );

        listenerRef.get().onReconnectScheduled("listener_close", 1, 1000L, "trc-ws-degrade-1");
        listenerRef.get().onReconnectScheduled("listener_close", 2, 2000L, "trc-ws-degrade-2");

        verify(reconcileService, times(1)).reconcileOnce(77);
        verify(auditLogRepository).append(eq("WS"), eq("WS_RECONNECT_SCHEDULED"), eq("OKX_WS"), eq("trc-ws-degrade-1"), any());
        verify(auditLogRepository).append(eq("WS"), eq("WS_RECONCILE_DEGRADE_SKIPPED_COOLDOWN"), eq("OKX_WS"), eq("trc-ws-degrade-2"), any());
        verify(eventStoreAppender, times(3)).append(eq(TopicNames.AUDIT_EVENT_V1), any());
    }

    /**
     * 验证连续订阅失败达到阈值后才触发 reconcile。
     */
    @Test
    void shouldTriggerReconcileAfterSubscribeFailThreshold() {
        OkxWsClient wsClient = mock(OkxWsClient.class);
        OkxRestReconcileService reconcileService = mock(OkxRestReconcileService.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        EventStoreAppender eventStoreAppender = mock(EventStoreAppender.class);
        AtomicReference<OkxWsConnectionListener> listenerRef = new AtomicReference<>();
        doAnswer(invocation -> {
            listenerRef.set(invocation.getArgument(0));
            return null;
        }).when(wsClient).addConnectionListener(any());
        when(reconcileService.reconcileOnce(66)).thenReturn(1);

        new OkxWsDegradeReconcileCoordinator(
                wsClient,
                reconcileService,
                auditLogRepository,
                eventStoreAppender,
                66,
                0L,
                2
        );

        listenerRef.get().onSubscribeFailed("orders", "60012", "invalid args", "trc-ws-sub-1");
        listenerRef.get().onSubscribeFailed("orders", "60012", "invalid args", "trc-ws-sub-2");

        verify(reconcileService, times(1)).reconcileOnce(66);
        verify(auditLogRepository).append(eq("WS"), eq("WS_SUBSCRIBE_FAILED_OBSERVED"), eq("OKX_WS"), eq("trc-ws-sub-1"), any());
        verify(auditLogRepository).append(eq("WS"), eq("WS_SUBSCRIBE_FAILED_THRESHOLD"), eq("OKX_WS"), eq("trc-ws-sub-2"), any());
    }
}


