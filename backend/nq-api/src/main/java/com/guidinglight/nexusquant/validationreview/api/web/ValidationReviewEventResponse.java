package com.guidinglight.nexusquant.validationreview.api.web;

import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only lifecycle event 的最小安全响应。
 *
 * <p>不暴露 request hash、idempotency key、raw metadata、header 或 credential-like 字段。
 */
public record ValidationReviewEventResponse(
        UUID id,
        UUID caseId,
        String eventType,
        String fromState,
        String toState,
        long caseVersion,
        long actorId,
        Instant createdAt
) {
    /** @return lifecycle fact 的最小公开字段 */
    public static ValidationReviewEventResponse from(ValidationReviewEvent event) {
        return new ValidationReviewEventResponse(
                event.id(),
                event.reviewCaseId(),
                event.eventType().name(),
                event.fromState().name(),
                event.toState().name(),
                event.caseVersion(),
                event.actorId(),
                event.createdAt()
        );
    }
}
