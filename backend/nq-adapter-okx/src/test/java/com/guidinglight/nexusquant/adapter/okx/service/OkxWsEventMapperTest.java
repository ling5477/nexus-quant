package com.guidinglight.nexusquant.adapter.okx.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.contracts.event.AuditRecorded;
import com.guidinglight.nexusquant.contracts.event.CancelAck;
import com.guidinglight.nexusquant.contracts.event.OrderAck;
import com.guidinglight.nexusquant.contracts.event.OrderReject;
import com.guidinglight.nexusquant.contracts.event.TopicNames;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * OkxWsEventMapperTest 验证 PR-W2 的 WS -> EventEnvelope 映射口径。
 */
class OkxWsEventMapperTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 验证 orders/live 映射为 OrderAck 且 key 优先 clOrdId。
     */
    @Test
    void shouldMapOrderAckFromOrdersChannel() throws Exception {
        OkxWsEventMapper mapper = new OkxWsEventMapper(Clock.fixed(Instant.parse("2026-03-05T00:00:00Z"), ZoneOffset.UTC));
        OkxWsBusinessMessage message = new OkxWsBusinessMessage(
                "orders",
                "",
                "",
                "",
                List.of(readJson("{\"uid\":\"1001\",\"instId\":\"BTC-USDT\",\"clOrdId\":\"c1\",\"ordId\":\"o1\",\"state\":\"live\",\"uTime\":\"1772668800000\"}")),
                "{}"
        );

        List<OkxWsEventMapper.MappedEvent> mapped = mapper.map(message, "trc-ws-1");

        assertEquals(1, mapped.size());
        OkxWsEventMapper.MappedEvent event = mapped.get(0);
        assertEquals(TopicNames.ORDER_EVENT_V1, event.topic());
        assertEquals("trc-ws-1", event.envelope().traceId());
        assertEquals("OKX_WS", event.envelope().source());
        assertEquals("c1", event.envelope().key());
        assertInstanceOf(OrderAck.class, event.envelope().payload());
    }

    /**
     * 验证 orders/canceled 映射为 CancelAck。
     */
    @Test
    void shouldMapCancelAckFromOrdersChannel() throws Exception {
        OkxWsEventMapper mapper = new OkxWsEventMapper();
        OkxWsBusinessMessage message = new OkxWsBusinessMessage(
                "orders",
                "",
                "",
                "",
                List.of(readJson("{\"uid\":\"1001\",\"clOrdId\":\"c2\",\"ordId\":\"o2\",\"state\":\"canceled\",\"cancelSource\":\"user\"}")),
                "{}"
        );

        List<OkxWsEventMapper.MappedEvent> mapped = mapper.map(message, "trc-ws-2");

        assertEquals(1, mapped.size());
        assertEquals(TopicNames.ORDER_EVENT_V1, mapped.get(0).topic());
        assertInstanceOf(CancelAck.class, mapped.get(0).envelope().payload());
    }

    /**
     * 验证 orders/order_failed 映射为 OrderReject。
     */
    @Test
    void shouldMapOrderRejectFromOrdersChannel() throws Exception {
        OkxWsEventMapper mapper = new OkxWsEventMapper();
        OkxWsBusinessMessage message = new OkxWsBusinessMessage(
                "orders",
                "",
                "",
                "",
                List.of(readJson("{\"uid\":\"1001\",\"clOrdId\":\"c3\",\"state\":\"order_failed\",\"sCode\":\"51000\",\"sMsg\":\"reject\"}")),
                "{}"
        );

        List<OkxWsEventMapper.MappedEvent> mapped = mapper.map(message, "trc-ws-3");

        assertEquals(1, mapped.size());
        assertInstanceOf(OrderReject.class, mapped.get(0).envelope().payload());
    }

    /**
     * 验证 account 通道映射到 audit.event.v1。
     */
    @Test
    void shouldMapAccountSnapshotToAuditEvent() throws Exception {
        OkxWsEventMapper mapper = new OkxWsEventMapper();
        OkxWsBusinessMessage message = new OkxWsBusinessMessage(
                "account",
                "",
                "",
                "",
                List.of(readJson("{\"uid\":\"9009\",\"uTime\":\"1772668800000\"}")),
                "{}"
        );

        List<OkxWsEventMapper.MappedEvent> mapped = mapper.map(message, "trc-ws-4");

        assertEquals(1, mapped.size());
        assertEquals(TopicNames.AUDIT_EVENT_V1, mapped.get(0).topic());
        assertInstanceOf(AuditRecorded.class, mapped.get(0).envelope().payload());
        assertEquals("9009", mapped.get(0).envelope().key());
    }

    /**
     * 验证 balance_and_position 会映射到 position.event.v1。
     */
    @Test
    void shouldMapBalanceAndPositionToPositionEvent() throws Exception {
        OkxWsEventMapper mapper = new OkxWsEventMapper();
        OkxWsBusinessMessage message = new OkxWsBusinessMessage(
                "balance_and_position",
                "",
                "",
                "",
                List.of(readJson("{\"uid\":\"8008\",\"posData\":[{\"instId\":\"BTC-USDT\",\"pos\":\"0.01\",\"uTime\":\"1772668800000\"}]}")),
                "{}"
        );

        List<OkxWsEventMapper.MappedEvent> mapped = mapper.map(message, "trc-ws-5");

        assertEquals(1, mapped.size());
        assertEquals(TopicNames.POSITION_EVENT_V1, mapped.get(0).topic());
        assertEquals("8008", mapped.get(0).envelope().key());
        assertEquals("OKX_WS", mapped.get(0).envelope().source());
    }

    /**
     * 验证未知 channel 不会产出事件。
     */
    @Test
    void shouldIgnoreUnknownChannel() {
        OkxWsEventMapper mapper = new OkxWsEventMapper();
        OkxWsBusinessMessage message = new OkxWsBusinessMessage("unknown", "", "", "", List.of(), "{}");
        assertTrue(mapper.map(message, "trc-ws-6").isEmpty());
    }

    /**
     * 验证 orders/fill 证据事件能入链，且 type 为 OrderFilled。
     */
    @Test
    void shouldMapFilledEvidence() throws Exception {
        OkxWsEventMapper mapper = new OkxWsEventMapper();
        OkxWsBusinessMessage message = new OkxWsBusinessMessage(
                "orders",
                "",
                "",
                "",
                List.of(readJson("{\"clOrdId\":\"c7\",\"ordId\":\"o7\",\"state\":\"filled\"}")),
                "{}"
        );

        List<OkxWsEventMapper.MappedEvent> mapped = mapper.map(message, "trc-ws-7");
        assertFalse(mapped.isEmpty());
        assertEquals("OrderFilled", mapped.get(0).envelope().type());
    }

    private JsonNode readJson(String raw) throws Exception {
        return MAPPER.readTree(raw);
    }
}
