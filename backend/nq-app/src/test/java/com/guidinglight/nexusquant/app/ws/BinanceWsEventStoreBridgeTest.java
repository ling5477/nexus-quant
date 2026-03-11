package com.guidinglight.nexusquant.app.ws;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.adapter.binance.ws.BinanceWsClient;
import com.guidinglight.nexusquant.adapter.binance.ws.BinanceWsEventMapper;
import com.guidinglight.nexusquant.adapter.binance.ws.BinanceWsRawMessage;
import com.guidinglight.nexusquant.adapter.binance.ws.BinanceWsRawMessageListener;
import com.guidinglight.nexusquant.contracts.event.TopicNames;
import com.guidinglight.nexusquant.core.service.port.AuditLogRepository;
import com.guidinglight.nexusquant.infra.eventstore.EventStoreAppender;
import com.guidinglight.nexusquant.scheduler.service.BinanceWsOrderAccelerationService;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

/**
 * BinanceWsEventStoreBridgeTest 验证 BW2 的桥接行为。
 */
class BinanceWsEventStoreBridgeTest {

    @Test
    void shouldAppendMappedEventToEventStore() throws Exception {
        BinanceWsClient wsClient = mock(BinanceWsClient.class);
        EventStoreAppender eventStoreAppender = mock(EventStoreAppender.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        BinanceWsOrderAccelerationService orderAccelerationService = mock(BinanceWsOrderAccelerationService.class);
        BinanceWsEventMapper mapper = new BinanceWsEventMapper();
        AtomicReference<BinanceWsRawMessageListener> listenerRef = new AtomicReference<>();
        doAnswer(invocation -> {
            listenerRef.set(invocation.getArgument(0));
            return null;
        }).when(wsClient).addRawMessageListener(any());

        new BinanceWsEventStoreBridge(wsClient, mapper, eventStoreAppender, auditLogRepository, orderAccelerationService);

        BinanceWsRawMessage message = new BinanceWsRawMessage(
                "executionReport",
                new ObjectMapper().readTree("""
                        {"e":"executionReport","E":1700000000001,"s":"BTCUSDT","c":"bridge-client","i":12345,"x":"NEW","X":"NEW","r":"NONE"}
                        """),
                "{}",
                Instant.parse("2026-03-10T00:00:00Z")
        );
        listenerRef.get().onMessage(message);

        verify(eventStoreAppender, atLeastOnce()).append(eq(TopicNames.ORDER_EVENT_V1), any());
        verify(orderAccelerationService, atLeastOnce()).accelerate(any(), any());
    }

    @Test
    void shouldWriteAuditWhenPayloadParseFails() {
        BinanceWsClient wsClient = mock(BinanceWsClient.class);
        EventStoreAppender eventStoreAppender = mock(EventStoreAppender.class);
        AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
        BinanceWsOrderAccelerationService orderAccelerationService = mock(BinanceWsOrderAccelerationService.class);
        BinanceWsEventMapper mapper = new BinanceWsEventMapper();
        AtomicReference<BinanceWsRawMessageListener> listenerRef = new AtomicReference<>();
        doAnswer(invocation -> {
            listenerRef.set(invocation.getArgument(0));
            return null;
        }).when(wsClient).addRawMessageListener(any());

        new BinanceWsEventStoreBridge(wsClient, mapper, eventStoreAppender, auditLogRepository, orderAccelerationService);
        listenerRef.get().onMessage(new BinanceWsRawMessage("UNKNOWN", null, "{invalid", Instant.parse("2026-03-10T00:00:00Z")));

        verify(auditLogRepository).append(eq("WS"), eq("BINANCE_WS_PARSE_FAILED"), any(), any(), any());
        verify(eventStoreAppender).append(eq(TopicNames.AUDIT_EVENT_V1), any());
    }
}
