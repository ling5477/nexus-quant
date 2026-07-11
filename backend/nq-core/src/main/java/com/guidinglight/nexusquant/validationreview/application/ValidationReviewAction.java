package com.guidinglight.nexusquant.validationreview.application;

import com.guidinglight.nexusquant.validationreview.domain.ValidationReviewState;

/**
 * GateV-2 对外允许的四个有限人工复核动作。
 *
 * <p>枚举只映射 review lifecycle target，不包含 create/reopen/approve/authorize/trade 等越界动作。
 */
public enum ValidationReviewAction {
    ACKNOWLEDGE(ValidationReviewState.ACKNOWLEDGED),
    ESCALATE(ValidationReviewState.ESCALATED),
    RESOLVE(ValidationReviewState.RESOLVED),
    CLOSE(ValidationReviewState.CLOSED);

    private final ValidationReviewState targetState;

    ValidationReviewAction(ValidationReviewState targetState) {
        this.targetState = targetState;
    }

    /** @return 交给 GateV-1 状态机验证的目标状态 */
    public ValidationReviewState targetState() {
        return targetState;
    }
}
