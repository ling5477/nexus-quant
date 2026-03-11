package com.guidinglight.nexusquant.adapter.binance.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.contracts.event.AuditRecorded;
import com.guidinglight.nexusquant.contracts.event.CancelAck;
import com.guidinglight.nexusquant.contracts.event.CancelReject;
import com.guidinglight.nexusquant.contracts.event.EventEnvelope;
import com.guidinglight.nexusquant.contracts.event.OrderAck;
import com.guidinglight.nexusquant.contracts.event.OrderReject;
import com.guidinglight.nexusquant.contracts.event.TopicNames;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * BinanceWsEventMapperTest 覆盖 PR-BW2 的 WS 原始消息映射口径。
 */
class BinanceWsEventMapperTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-10T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldMapExecutionReportToAckCancelAndRejectEvents() throws Exception {
        BinanceWsEventMapper mapper = new BinanceWsEventMapper(CLOCK);

        BinanceWsEventMapper.MappedEvent orderAck = mapSingle(mapper, """
                {"e":"executionReport","E":1700000000001,"s":"BTCUSDT","c":"client-ack","i":12345,"x":"NEW","X":"NEW","r":"NONE"}
                """);
        assertEquals(TopicNames.ORDER_EVENT_V1, orderAck.topic());
        EventEnvelope<?> orderAckEnvelope = orderAck.envelope();
        assertEquals("BINANCE_WS", orderAckEnvelope.source());
        assertEquals("trace-1", orderAckEnvelope.traceId());
        assertEquals("client-ack", orderAckEnvelope.key());
        OrderAck orderAckPayload = assertInstanceOf(OrderAck.class, orderAckEnvelope.payload());
        assertEquals("client-ack", orderAckPayload.clientOrderId());
        assertEquals("12345", orderAckPayload.externalOrderId());

        BinanceWsEventMapper.MappedEvent cancelAck = mapSingle(mapper, """
                {"e":"executionReport","E":1700000000002,"s":"BTCUSDT","c":"client-cancel","i":22345,"x":"CANCELED","X":"CANCELED","r":"NONE"}
                """);
        CancelAck cancelAckPayload = assertInstanceOf(CancelAck.class, cancelAck.envelope().payload());
        assertEquals("client-cancel", cancelAck.envelope().key());
        assertEquals("22345", cancelAckPayload.externalOrderId());

        BinanceWsEventMapper.MappedEvent orderReject = mapSingle(mapper, """
                {"e":"executionReport","E":1700000000003,"s":"BTCUSDT","c":"client-reject","i":32345,"x":"REJECTED","X":"REJECTED","r":"INSUFFICIENT_BALANCES"}
                """);
        OrderReject orderRejectPayload = assertInstanceOf(OrderReject.class, orderReject.envelope().payload());
        assertEquals("INSUFFICIENT_BALANCES", orderRejectPayload.rejectCode());
        assertEquals("INSUFFICIENT_BALANCES", orderRejectPayload.rejectReason());

        BinanceWsEventMapper.MappedEvent cancelReject = mapSingle(mapper, """
                {"e":"executionReport","E":1700000000004,"s":"BTCUSDT","c":"cancel-client","C":"orig-client","i":42345,"x":"REJECTED","X":"NEW","r":"UNKNOWN_ORDER"}
                """);
        CancelReject cancelRejectPayload = assertInstanceOf(CancelReject.class, cancelReject.envelope().payload());
        assertEquals("cancel-client", cancelReject.envelope().key());
        assertEquals("42345", cancelRejectPayload.externalOrderId());
        assertEquals("UNKNOWN_ORDER", cancelRejectPayload.rejectCode());
    }

    @Test
    void shouldMapAccountAndBalanceUpdatesToReferenceEvents() throws Exception {
        BinanceWsEventMapper mapper = new BinanceWsEventMapper(CLOCK);

        List<BinanceWsEventMapper.MappedEvent> accountSnapshots = mapper.map(
                rawMessage("""
                        {"e":"outboundAccountPosition","E":1700000001000,"u":1700000000999,"B":[{"a":"BTC","f":"0.10000000","l":"0.02000000"}]}
                        """),
                "trace-1"
        );
        assertEquals(1, accountSnapshots.size());
        BinanceWsEventMapper.MappedEvent positionEvent = accountSnapshots.get(0);
        assertEquals(TopicNames.POSITION_EVENT_V1, positionEvent.topic());
        EventEnvelope<?> positionEnvelope = positionEvent.envelope();
        assertEquals("BINANCE|UNKNOWN_ACCOUNT", positionEnvelope.key());
        BinanceWsEventMapper.AccountBalanceSnapshotEvidence positionPayload =
                assertInstanceOf(BinanceWsEventMapper.AccountBalanceSnapshotEvidence.class, positionEnvelope.payload());
        assertEquals("BTC", positionPayload.asset());
        assertEquals("BINANCE_WS", positionPayload.source());

        List<BinanceWsEventMapper.MappedEvent> balanceUpdates = mapper.map(
                rawMessage("""
                        {"e":"balanceUpdate","E":1700000002000,"a":"USDT","d":"5.25000000","T":1700000001999}
                        """),
                "trace-2"
        );
        assertEquals(1, balanceUpdates.size());
        BinanceWsEventMapper.MappedEvent auditEvent = balanceUpdates.get(0);
        assertEquals(TopicNames.AUDIT_EVENT_V1, auditEvent.topic());
        AuditRecorded auditPayload = assertInstanceOf(AuditRecorded.class, auditEvent.envelope().payload());
        assertEquals("BINANCE_WS", auditEvent.envelope().source());
        assertEquals("trace-2", auditEvent.envelope().traceId());
        assertEquals("BINANCE|UNKNOWN_ACCOUNT", auditEvent.envelope().key());
        assertEquals("BINANCE_WS_BALANCE_UPDATE", auditPayload.action());
    }

    private BinanceWsEventMapper.MappedEvent mapSingle(BinanceWsEventMapper mapper, String json) throws Exception {
        List<BinanceWsEventMapper.MappedEvent> events = mapper.map(rawMessage(json), "trace-1");
        assertEquals(1, events.size());
        return events.get(0);
    }

    private BinanceWsRawMessage rawMessage(String json) throws Exception {
        return new BinanceWsRawMessage(
                MAPPER.readTree(json).path("e").asText(),
                MAPPER.readTree(json),
                json,
                Instant.parse("2026-03-10T00:00:00Z")
        );
    }
}
