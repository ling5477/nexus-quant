package com.guidinglight.nexusquant.validationreview.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Accepted validation review transition 的 append-only event。
 *
 * <p>事件只保存本地、脱敏 lifecycle audit；非法流转、鉴权拒绝和版本冲突不得伪装成 accepted event。
 */
public record ValidationReviewEvent(
        UUID id,
        UUID reviewCaseId,
        String tenantKey,
        ValidationReviewEventType eventType,
        ValidationReviewState fromState,
        ValidationReviewState toState,
        long caseVersion,
        long actorId,
        String idempotencyKey,
        String requestHash,
        String requestId,
        String traceId,
        JsonNode metadata,
        Instant createdAt
) {

    /**
     * 校验 event 的 scope、transition shape、幂等字段、JSONB 和时间不变量。
     */
    public ValidationReviewEvent {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(reviewCaseId, "reviewCaseId must not be null");
        ValidationReviewCase.requireTenant(tenantKey);
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(fromState, "fromState must not be null");
        Objects.requireNonNull(toState, "toState must not be null");
        if (fromState == toState) {
            throw new IllegalArgumentException("accepted transition event must not be a self-loop");
        }
        if (!eventType.name().equals(toState.name())) {
            throw new IllegalArgumentException("eventType must match toState");
        }
        if (caseVersion <= 0) {
            throw new IllegalArgumentException("caseVersion must be positive");
        }
        ValidationReviewCase.requirePositive(actorId, "actorId");
        ValidationReviewCase.requireText(idempotencyKey, "idempotencyKey", 128);
        ValidationReviewCase.requireText(requestHash, "requestHash", 128);
        if (requestId != null && requestId.length() > 128) {
            throw new IllegalArgumentException("requestId exceeds max length 128");
        }
        ValidationReviewCase.requireText(traceId, "traceId", 128);
        ValidationReviewSensitiveDataGuard.validateObject("metadata", metadata);
        metadata = metadata.deepCopy();
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    /**
     * 返回审计 metadata 的防御性副本，保持 accepted event 构造后的不可变语义。
     *
     * @return 与内部 event 隔离的 JSON object
     */
    public JsonNode metadata() {
        return metadata.deepCopy();
    }
}
