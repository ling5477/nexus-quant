package com.guidinglight.nexusquant.validationreview.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * GateV-2 可复用的内部 transition command。
 *
 * <p>tenant/owner 由可信 application context 构造；本轮没有 Controller，也不接受客户端 tenant override。
 */
public record ValidationReviewTransitionCommand(
        UUID reviewCaseId,
        String tenantKey,
        long ownerId,
        ValidationReviewState targetState,
        long expectedVersion,
        long actorId,
        String idempotencyKey,
        String requestHash,
        String requestId,
        String traceId,
        JsonNode metadata,
        Instant occurredAt
) {

    /** 校验 scope、乐观锁、幂等和脱敏 metadata 输入。 */
    public ValidationReviewTransitionCommand {
        Objects.requireNonNull(reviewCaseId, "reviewCaseId must not be null");
        ValidationReviewCase.requireTenant(tenantKey);
        ValidationReviewCase.requirePositive(ownerId, "ownerId");
        Objects.requireNonNull(targetState, "targetState must not be null");
        if (targetState == ValidationReviewState.OPEN) {
            throw new IllegalArgumentException("targetState must not be OPEN");
        }
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must not be negative");
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
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }

    /**
     * 返回 transition metadata 的防御性副本，避免校验通过后发生引用侧变更。
     *
     * @return 与内部 command 隔离的 JSON object
     */
    public JsonNode metadata() {
        return metadata.deepCopy();
    }
}
