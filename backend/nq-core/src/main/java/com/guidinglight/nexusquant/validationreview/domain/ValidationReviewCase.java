package com.guidinglight.nexusquant.validationreview.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Durable operator review 的本地 case 主事实。
 *
 * <p>该模型只表达诊断证据的人工复核生命周期；不修改策略、Paper、Shadow、risk、account、
 * order 或 ledger，也不表示交易授权。Owner/tenant 由 repository 查询条件强制隔离。
 */
public record ValidationReviewCase(
        UUID id,
        String tenantKey,
        long ownerId,
        String evidenceType,
        String evidenceSource,
        JsonNode evidenceAnchor,
        ValidationReviewSeverity severity,
        ValidationReviewState state,
        String title,
        String summary,
        long version,
        long createdBy,
        Instant createdAt,
        Instant updatedAt,
        Long acknowledgedBy,
        Instant acknowledgedAt,
        Long escalatedBy,
        Instant escalatedAt,
        Long resolvedBy,
        Instant resolvedAt,
        Long closedBy,
        Instant closedAt,
        Instant retentionUntil
) {

    public static final String LOCAL_TENANT_KEY = "NQ_LOCAL";

    /**
     * 校验 case 的身份、JSONB、乐观锁和状态时间不变量。
     */
    public ValidationReviewCase {
        Objects.requireNonNull(id, "id must not be null");
        requireTenant(tenantKey);
        requirePositive(ownerId, "ownerId");
        requireText(evidenceType, "evidenceType", 64);
        requireText(evidenceSource, "evidenceSource", 256);
        ValidationReviewSensitiveDataGuard.validateText("evidenceType", evidenceType);
        ValidationReviewSensitiveDataGuard.validateText("evidenceSource", evidenceSource);
        ValidationReviewSensitiveDataGuard.validateObject("evidenceAnchor", evidenceAnchor);
        evidenceAnchor = evidenceAnchor.deepCopy();
        Objects.requireNonNull(severity, "severity must not be null");
        Objects.requireNonNull(state, "state must not be null");
        requireText(title, "title", 256);
        ValidationReviewSensitiveDataGuard.validateText("title", title);
        ValidationReviewSensitiveDataGuard.validateText("summary", summary);
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        requirePositive(createdBy, "createdBy");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
        validateActorTimePair(acknowledgedBy, acknowledgedAt, "acknowledged");
        validateActorTimePair(escalatedBy, escalatedAt, "escalated");
        validateActorTimePair(resolvedBy, resolvedAt, "resolved");
        validateActorTimePair(closedBy, closedAt, "closed");
        validateStateTimes(
                state,
                createdAt,
                acknowledgedAt,
                escalatedAt,
                resolvedAt,
                closedAt,
                retentionUntil
        );
    }

    /**
     * 返回证据锚点的防御性副本，防止调用方在构造校验后注入敏感字段。
     *
     * @return 与内部事实隔离的 JSON object
     */
    public JsonNode evidenceAnchor() {
        return evidenceAnchor.deepCopy();
    }

    ValidationReviewCase transitionedTo(ValidationReviewState target, long actorId, Instant occurredAt) {
        Long nextAcknowledgedBy = acknowledgedBy;
        Instant nextAcknowledgedAt = acknowledgedAt;
        Long nextEscalatedBy = escalatedBy;
        Instant nextEscalatedAt = escalatedAt;
        Long nextResolvedBy = resolvedBy;
        Instant nextResolvedAt = resolvedAt;
        Long nextClosedBy = closedBy;
        Instant nextClosedAt = closedAt;
        switch (target) {
            case ACKNOWLEDGED -> {
                nextAcknowledgedBy = actorId;
                nextAcknowledgedAt = occurredAt;
            }
            case ESCALATED -> {
                nextEscalatedBy = actorId;
                nextEscalatedAt = occurredAt;
            }
            case RESOLVED -> {
                nextResolvedBy = actorId;
                nextResolvedAt = occurredAt;
            }
            case CLOSED -> {
                nextClosedBy = actorId;
                nextClosedAt = occurredAt;
            }
            case OPEN -> throw new IllegalArgumentException("transition target must not be OPEN");
        }
        return new ValidationReviewCase(
                id,
                tenantKey,
                ownerId,
                evidenceType,
                evidenceSource,
                evidenceAnchor,
                severity,
                target,
                title,
                summary,
                version + 1,
                createdBy,
                createdAt,
                occurredAt,
                nextAcknowledgedBy,
                nextAcknowledgedAt,
                nextEscalatedBy,
                nextEscalatedAt,
                nextResolvedBy,
                nextResolvedAt,
                nextClosedBy,
                nextClosedAt,
                retentionUntil
        );
    }

    /**
     * 校验 GateV 当前服务端单租户 scope。
     *
     * @param tenantKey 可信 application context 提供的 tenant key
     */
    public static void requireTenant(String tenantKey) {
        if (!LOCAL_TENANT_KEY.equals(tenantKey)) {
            throw new IllegalArgumentException("tenantKey must be server-controlled NQ_LOCAL");
        }
    }

    /**
     * 校验现有 users BIGINT identity 必须为正数。
     *
     * @param value identity value
     * @param fieldName 脱敏字段名
     */
    public static void requirePositive(long value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    /**
     * 校验写入 VARCHAR 的文本非空且不超过 schema 长度。
     *
     * @param value 待校验文本
     * @param fieldName 脱敏字段名
     * @param maxLength schema 最大长度
     */
    public static void requireText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " exceeds max length " + maxLength);
        }
    }

    private static void validateActorTimePair(Long actor, Instant time, String fieldName) {
        if ((actor == null) != (time == null)) {
            throw new IllegalArgumentException(fieldName + " actor/time must both be null or non-null");
        }
        if (actor != null) {
            requirePositive(actor, fieldName + "By");
        }
    }

    private static void validateStateTimes(
            ValidationReviewState state,
            Instant createdAt,
            Instant acknowledgedAt,
            Instant escalatedAt,
            Instant resolvedAt,
            Instant closedAt,
            Instant retentionUntil
    ) {
        if (acknowledgedAt != null && acknowledgedAt.isBefore(createdAt)
                || escalatedAt != null && escalatedAt.isBefore(acknowledgedAt == null ? createdAt : acknowledgedAt)
                || resolvedAt != null && resolvedAt.isBefore(
                        escalatedAt != null ? escalatedAt : acknowledgedAt != null ? acknowledgedAt : createdAt)
                || closedAt != null && (resolvedAt == null || closedAt.isBefore(resolvedAt))
                || retentionUntil != null && retentionUntil.isBefore(closedAt == null ? createdAt : closedAt)) {
            throw new IllegalArgumentException("review lifecycle timestamps are out of order");
        }
        boolean valid = switch (state) {
            case OPEN -> acknowledgedAt == null && escalatedAt == null && resolvedAt == null && closedAt == null;
            case ACKNOWLEDGED -> acknowledgedAt != null && escalatedAt == null && resolvedAt == null && closedAt == null;
            case ESCALATED -> escalatedAt != null && resolvedAt == null && closedAt == null;
            case RESOLVED -> resolvedAt != null && closedAt == null
                    && (acknowledgedAt != null || escalatedAt != null);
            case CLOSED -> resolvedAt != null && closedAt != null;
        };
        if (!valid) {
            throw new IllegalArgumentException("review lifecycle timestamps contradict state " + state);
        }
    }
}
