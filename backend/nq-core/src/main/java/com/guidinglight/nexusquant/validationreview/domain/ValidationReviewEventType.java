package com.guidinglight.nexusquant.validationreview.domain;

/**
 * Accepted review transition 的 append-only event 类型。
 */
public enum ValidationReviewEventType {
    ACKNOWLEDGED,
    ESCALATED,
    RESOLVED,
    CLOSED;

    /**
     * 将目标状态映射为同名 lifecycle event。
     *
     * @param state 已通过状态机校验的目标状态
     * @return 对应 append-only event 类型
     * @throws ValidationReviewException 当目标仍为 OPEN 时抛出
     */
    public static ValidationReviewEventType fromTargetState(ValidationReviewState state) {
        if (state == null || state == ValidationReviewState.OPEN) {
            throw new ValidationReviewException(
                    "REVIEW_EVENT_TARGET_INVALID",
                    "accepted transition event target must not be OPEN",
                    null,
                    null,
                    state
            );
        }
        return valueOf(state.name());
    }
}
