package com.guidinglight.nexusquant.eventstore.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.guidinglight.nexusquant.contracts.event.EventEnvelope;
import com.guidinglight.nexusquant.contracts.event.OrderCreated;
import com.guidinglight.nexusquant.contracts.event.TopicNames;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * EventStoreAppenderTest 验证 event_store 事实写入行为。
 *
 * Why:
 * Gate B 的恢复与回放都依赖 event_store，必须保证 topic/type/version/key_value/trace_id 可稳定落库并可查回。
 */
class EventStoreAppenderTest {

    private final RecordingJdbcTemplate jdbcTemplate = new RecordingJdbcTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final EventStoreAppender appender = new EventStoreAppender(jdbcTemplate, objectMapper);

    /**
     * 验证追加后可按 event_id 查回关键字段与 envelope JSON。
     */
    @Test
    void shouldInsertEnvelopeIntoEventStore() throws Exception {
        EventEnvelope<OrderCreated> envelope = new EventEnvelope<>(
                "evt-100",
                "OrderCreated",
                1,
                Instant.parse("2026-02-25T01:02:03Z"),
                "nq-core",
                "trc-100",
                "coid-100",
                new OrderCreated(
                        "ord-100",
                        1001L,
                        "run-100",
                        "BTC-USDT",
                        "coid-100",
                        "BUY",
                        "MARKET",
                        null,
                        new BigDecimal("0.01000000"),
                        "NEW",
                        "CREATED",
                        Instant.parse("2026-02-25T01:02:03Z")
                )
        );

        appender.append(TopicNames.ORDER_EVENT_V1, envelope);

        Map<String, Object> row = jdbcTemplate.findByEventId("evt-100");
        String payloadJson = (String) row.get("payload_json");
        JsonNode payloadNode = objectMapper.readTree(payloadJson);

        assertNotNull(row);
        assertEquals(TopicNames.ORDER_EVENT_V1, row.get("topic"));
        assertEquals(1, ((Number) row.get("schema_version")).intValue());
        assertEquals("OrderCreated", row.get("event_type"));
        assertEquals("coid-100", row.get("key_value"));
        assertEquals("trc-100", row.get("trace_id"));
        assertNotNull(payloadJson);
        assertTrue(payloadJson.contains("\"event_id\":\"evt-100\""));
        assertEquals("evt-100", payloadNode.get("event_id").asText());
        assertEquals("OrderCreated", payloadNode.get("type").asText());
    }

    /**
     * RecordingJdbcTemplate 作为测试替身，仅捕获 update 参数并提供按 event_id 查询能力。
     *
     * Why:
     * PR-1 的目标是验证 appender 映射字段正确，不依赖外部数据库即可稳定回归。
     */
    private static final class RecordingJdbcTemplate extends JdbcTemplate {

        private final Map<String, Map<String, Object>> rows = new HashMap<>();

        @Override
        public int update(String sql, Object... args) {
            rows.put((String) args[0], Map.of(
                    "topic", args[1],
                    "schema_version", args[2],
                    "event_type", args[3],
                    "payload_json", args[4],
                    "key_value", args[5],
                    "trace_id", args[6]
            ));
            return 1;
        }

        Map<String, Object> findByEventId(String eventId) {
            return rows.get(eventId);
        }
    }
}

