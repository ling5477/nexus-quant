package com.guidinglight.nexusquant.strategy.api.dto;

import com.guidinglight.nexusquant.strategy.domain.StrategyRunBusinessEventItem;

import java.time.Instant;

/** 账户身份用字符串传输；三个时间分别是业务发生、发布和写入时间，不相互伪造回填。 */
public record StrategyRunBusinessEventResponse(
        String eventId,
        String orderId,
        String tradeId,
        String accountId,
        String eventType,
        String topic,
        String source,
        String association,
        String status,
        String decision,
        String result,
        String rejectCode,
        Instant occurredAt,
        Instant publishedAt,
        Instant recordedAt,
        String traceId) {
    public static StrategyRunBusinessEventResponse from(StrategyRunBusinessEventItem value) {
        return new StrategyRunBusinessEventResponse(
                value.eventId(),
                value.orderId(),
                value.tradeId(),
                Long.toString(value.accountId()),
                value.eventType(),
                value.topic(),
                value.source(),
                value.association(),
                value.status(),
                value.decision(),
                value.result(),
                value.rejectCode(),
                value.occurredAt(),
                value.publishedAt(),
                value.recordedAt(),
                value.traceId());
    }
}
