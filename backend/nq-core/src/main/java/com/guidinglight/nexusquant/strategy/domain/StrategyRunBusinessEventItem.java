package com.guidinglight.nexusquant.strategy.domain;

import java.time.Instant;

/** 历史回报仅表达事件快照；不代表当前订单、结算或运行状态，也不把整包载荷传出读取边界。 */
public record StrategyRunBusinessEventItem(
        String eventId,
        String orderId,
        String tradeId,
        long accountId,
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
        String traceId) {}
