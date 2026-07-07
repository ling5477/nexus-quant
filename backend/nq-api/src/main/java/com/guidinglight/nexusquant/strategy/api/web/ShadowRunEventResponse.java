package com.guidinglight.nexusquant.strategy.api.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunEvent;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunSensitiveDataGuard;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * ShadowRunEventResponse 是 Shadow Run append-only event 的只读 DTO。
 *
 * <p>{@code metadata} 必须已经过敏感字段 guard；DTO 不暴露 private payload、credential 或真实交易字段。
 */
@Schema(name = "ShadowRunEventResponse", description = "GateR-6 read-only Shadow Run event")
public record ShadowRunEventResponse(
        String eventType,
        String fromStatus,
        String toStatus,
        String reasonCode,
        String message,
        JsonNode metadata,
        String requestId,
        String traceId,
        Instant createdAt
) {
    public static ShadowRunEventResponse from(ShadowRunEvent event) {
        ShadowRunSensitiveDataGuard.validateJson("metadata", event.metadata());
        return new ShadowRunEventResponse(
                event.eventType().name(),
                event.fromStatus() == null ? null : event.fromStatus().name(),
                event.toStatus() == null ? null : event.toStatus().name(),
                event.reasonCode(),
                event.message(),
                event.metadata(),
                event.requestId(),
                event.traceId(),
                event.createdAt()
        );
    }
}
