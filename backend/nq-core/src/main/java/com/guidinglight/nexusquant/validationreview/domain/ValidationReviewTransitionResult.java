package com.guidinglight.nexusquant.validationreview.domain;

import java.util.Objects;

/**
 * Accepted transition 或幂等 replay 的内部结果。
 *
 * @param reviewCase 首次 accepted event 对应的 case snapshot；幂等 replay 不返回后续状态
 * @param event 首次 accepted request 对应的 append-only event
 * @param idempotentReplay 是否返回既有 event
 */
public record ValidationReviewTransitionResult(
        ValidationReviewCase reviewCase,
        ValidationReviewEvent event,
        boolean idempotentReplay
) {
    public ValidationReviewTransitionResult {
        Objects.requireNonNull(reviewCase, "reviewCase must not be null");
        Objects.requireNonNull(event, "event must not be null");
        if (!reviewCase.id().equals(event.reviewCaseId())
                || reviewCase.state() != event.toState()
                || reviewCase.version() != event.caseVersion()) {
            throw new IllegalArgumentException("reviewCase snapshot must match accepted event");
        }
    }
}
