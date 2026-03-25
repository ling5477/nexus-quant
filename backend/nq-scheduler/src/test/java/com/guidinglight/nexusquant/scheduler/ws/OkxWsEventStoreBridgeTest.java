package com.guidinglight.nexusquant.scheduler.ws;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.okx.service.OkxWsBusinessMessage;
import com.guidinglight.nexusquant.adapter.okx.service.OkxWsBusinessMessageListener;
import com.guidinglight.nexusquant.adapter.okx.service.OkxWsClient;
import com.guidinglight.nexusquant.adapter.okx.service.OkxWsEventMapper;
import com.guidinglight.nexusquant.contracts.event.TopicNames;
import com.guidinglight.nexusquant.core.service.port.AuditLogRepository;
import com.guidinglight.nexusquant.infra.eventstore.EventStoreAppender;
import com.guidinglight.nexusquant.scheduler.service.OkxWsOrderAccelerationService;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/**
 * OkxWsEventStoreBridgeTest 验证 PR-W2 的桥接行为。
 */
class OkxWsEventStoreBridgeTest {

    /**
     * 验证 orders 消息会通过 mapper 入链到 order.event.v1。
     */
    @Test
    void shouldAppendMappedEventToEventStore() throws Exception {
        OkxWsClient wsClient = mock(OkxWsClient.class);
        EventStoreAppender eventStoreAppender = mock(EventStoreAppender.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        OkxWsOrderAccelerationService accelerationService = mock(OkxWsOrderAccelerationService.class);
        OkxWsEventMapper mapper = new OkxWsEventMapper();
        AtomicReference<OkxWsBusinessMessageListener> listenerRef = new AtomicReference<>();
        doAnswer(invocation -> {
            listenerRef.set(invocation.getArgument(0));
            return null;
        }).when(wsClient).addBusinessMessageListener(any());

        new OkxWsEventStoreBridge(wsClient, mapper, eventStoreAppender, auditLogRepository, accelerationService);

        OkxWsBusinessMessage message = new OkxWsBusinessMessage(
                "orders",
                "",
                "",
                "",
                List.of(new ObjectMapper().readTree("{\"clOrdId\":\"bridge-c1\",\"ordId\":\"bridge-o1\",\"state\":\"live\"}")),
                "{}"
        );
        listenerRef.get().onMessage(message, "trc-bridge-1");

        verify(eventStoreAppender, atLeastOnce()).append(eq(TopicNames.ORDER_EVENT_V1), any());
        verify(accelerationService, atLeastOnce()).accelerate(any(), eq("trc-bridge-1"));
    }

    /**
     * 验证 mapper 抛错时会写 audit_logs 与 audit.event.v1。
     */
    @Test
    void shouldWriteAuditWhenMappingFails() {
        OkxWsClient wsClient = mock(OkxWsClient.class);
        EventStoreAppender eventStoreAppender = mock(EventStoreAppender.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        OkxWsOrderAccelerationService accelerationService = mock(OkxWsOrderAccelerationService.class);
        OkxWsEventMapper mapper = mock(OkxWsEventMapper.class);
        AtomicReference<OkxWsBusinessMessageListener> listenerRef = new AtomicReference<>();
        doAnswer(invocation -> {
            listenerRef.set(invocation.getArgument(0));
            return null;
        }).when(wsClient).addBusinessMessageListener(any());
        when(mapper.map(any(), any())).thenThrow(new IllegalStateException("mapping boom"));

        new OkxWsEventStoreBridge(wsClient, mapper, eventStoreAppender, auditLogRepository, accelerationService);
        listenerRef.get().onMessage(new OkxWsBusinessMessage("orders", "", "", "", List.of(), "{}"), "trc-bridge-2");

        verify(auditLogRepository).append(eq("WS"), eq("OKX_WS_EVENT_MAPPING_FAILED"), any(), eq("trc-bridge-2"), any());
        verify(eventStoreAppender).append(eq(TopicNames.AUDIT_EVENT_V1), any());
    }
}
