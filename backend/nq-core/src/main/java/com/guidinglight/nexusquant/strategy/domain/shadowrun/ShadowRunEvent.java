package com.guidinglight.nexusquant.strategy.domain.shadowrun;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Shadow Run append-only 审计事件。
 *
 * <p>事件用于记录本地状态流转、阻断、失败和非法流转尝试，不表达交易授权。
 * {@code metadata} 只能保存脱敏上下文，构造时会拒绝 credential、private request/response
 * 和真实账户/订单字段。
 */
public record ShadowRunEvent(
        UUID id,
        UUID shadowRunId,
        ShadowRunEventType eventType,
        ShadowRunStatus fromStatus,
        ShadowRunStatus toStatus,
        String reasonCode,
        String message,
        JsonNode metadata,
        String requestId,
        String traceId,
        Instant createdAt
) {

    public ShadowRunEvent {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(shadowRunId, "shadowRunId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        ShadowRunSensitiveDataGuard.validateJson("metadata", metadata);
        ShadowRunJsonRules.requireObject(metadata, "metadata");
        ShadowRun.requireText(traceId, "traceId");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
