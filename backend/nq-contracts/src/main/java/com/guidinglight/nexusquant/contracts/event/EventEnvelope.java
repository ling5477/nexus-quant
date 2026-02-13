package com.guidinglight.nexusquant.contracts.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * EventEnvelope 是所有领域事件的统一外壳。
 *
 * Why:
 * Gate A 先冻结事件边界，确保 trace_id、version、type 等审计与演进关键字段强制存在。
 *
 * @param eventId 全局事件 ID（推荐 UUID）
 * @param type 事件类型，例如 Order.StatusChanged
 * @param version 契约版本号
 * @param ts 事件时间（UTC Instant）
 * @param source 事件来源模块
 * @param traceId 链路追踪 ID
 * @param key 分区键
 * @param payload 事件正文
 */
public record EventEnvelope<T>(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("type") String type,
        @JsonProperty("version") int version,
        @JsonProperty("ts") Instant ts,
        @JsonProperty("source") String source,
        @JsonProperty("trace_id") String traceId,
        @JsonProperty("key") String key,
        @JsonProperty("payload") T payload
) {
}
