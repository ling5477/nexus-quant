package com.guidinglight.nexusquant.contracts.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.guidinglight.nexusquant.contracts.command.PlaceOrderCommand;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * EventEnvelopeSerializationTest 校验 Gate B 事件序列化字段口径。
 *
 * Why:
 * event_store 回放依赖 JSON key 稳定；若 key 被无意改成驼峰，会直接破坏审计与恢复链路。
 */
class EventEnvelopeSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * 验证 EventEnvelope 与 PlaceOrderCommand 都使用下划线 JSON key。
     */
    @Test
    void shouldSerializeEnvelopeAndPayloadUsingSnakeCaseKeys() throws Exception {
        PlaceOrderCommand command = new PlaceOrderCommand(
                "ord-001",
                1001L,
                "PAPER",
                "BTC-USDT",
                "coid-001",
                "BUY",
                "MARKET",
                null,
                new BigDecimal("0.01000000"),
                "IOC",
                "demo-strategy",
                "trc-001"
        );
        EventEnvelope<PlaceOrderCommand> envelope = new EventEnvelope<>(
                "evt-001",
                "PlaceOrderCommand",
                1,
                Instant.parse("2026-02-25T00:00:00Z"),
                "nq-scheduler",
                "trc-001",
                "coid-001",
                command
        );

        String json = objectMapper.writeValueAsString(envelope);
        JsonNode root = objectMapper.readTree(json);
        JsonNode payload = root.get("payload");

        assertTrue(root.has("event_id"));
        assertTrue(root.has("trace_id"));
        assertTrue(payload.has("client_order_id"));
        assertTrue(payload.has("venue"));
        assertTrue(payload.has("time_in_force"));
        assertFalse(root.has("eventId"));
        assertFalse(payload.has("clientOrderId"));
        assertEquals("evt-001", root.get("event_id").asText());
        assertEquals("coid-001", payload.get("client_order_id").asText());
    }
}
