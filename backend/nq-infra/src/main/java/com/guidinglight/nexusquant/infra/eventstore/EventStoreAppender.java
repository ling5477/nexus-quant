package com.guidinglight.nexusquant.infra.eventstore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.contracts.event.EventEnvelope;

import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * EventStoreAppender 负责把关键命令/事件持久化到 event_store。
 * <p>
 * Why:
 * Gate B 需要把命令与关键业务事件写入事实表，才能在恢复场景下回放并追踪副作用来源。
 * <p>
 * 约束:
 * 1) topic 必须使用 TopicNames 常量，避免散装字符串；
 * 2) event_type 必须来自 EventEnvelope.type（简单类名）；
 * 3) payload_json 存整包 envelope，方便离线复盘时一次拿到全部上下文。
 */
@Repository
public class EventStoreAppender {

    // Why: PostgreSQL JSONB 列不能直接接收 VARCHAR 参数，必须在 SQL 层显式 cast 才能避免运行态类型推断错误。
    private static final String INSERT_SQL = """
            INSERT INTO event_store (event_id, topic, schema_version, event_type, payload_json, key_value, trace_id)
            VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    /**
     * @param jdbcTemplate JDBC 执行器
     * @param objectMapper JSON 序列化器
     */
    public EventStoreAppender(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * 追加一条事件到 event_store。
     * <p>
     * Why:
     * 所有命令与关键事件都先写入事实源，可在重启或补偿时按 trace_id + key_value 回放链路。
     *
     * @param topic    TopicNames 常量值
     * @param envelope 统一事件外壳，必须包含 event_id/type/version/trace_id/key
     * @throws IllegalArgumentException topic 或 envelope 非法时抛出
     * @throws IllegalStateException    序列化失败时抛出
     */
    public void append(String topic, EventEnvelope<?> envelope) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic must not be blank");
        }
        if (envelope == null) {
            throw new IllegalArgumentException("envelope must not be null");
        }
        if (envelope.eventId() == null || envelope.eventId().isBlank()) {
            throw new IllegalArgumentException("envelope.eventId must not be blank");
        }
        if (envelope.type() == null || envelope.type().isBlank()) {
            throw new IllegalArgumentException("envelope.type must not be blank");
        }
        if (envelope.traceId() == null || envelope.traceId().isBlank()) {
            throw new IllegalArgumentException("envelope.traceId must not be blank");
        }
        if (envelope.key() == null || envelope.key().isBlank()) {
            throw new IllegalArgumentException("envelope.key must not be blank");
        }

        String payloadJson = serializeEnvelope(envelope);
        jdbcTemplate.update(
                INSERT_SQL,
                envelope.eventId(),
                topic,
                envelope.version(),
                envelope.type(),
                payloadJson,
                envelope.key(),
                envelope.traceId()
        );
    }

    private String serializeEnvelope(EventEnvelope<?> envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize event envelope, eventId=" + envelope.eventId(), ex);
        }
    }
}
