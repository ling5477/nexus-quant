package com.guidinglight.nexusquant.validationreview.domain;

import java.util.UUID;

/**
 * Durable validation review 的明确 domain/application failure。
 *
 * <p>错误码供 GateV-2 映射稳定 API 语义；本异常本身不访问数据库或执行任何副作用。
 */
public class ValidationReviewException extends RuntimeException {

    private final String errorCode;
    private final UUID reviewCaseId;
    private final ValidationReviewState fromState;
    private final ValidationReviewState toState;

    /**
     * 创建带稳定错误码和状态上下文的异常。
     *
     * @param errorCode 稳定错误码
     * @param message 脱敏错误说明
     * @param reviewCaseId 可空 case id
     * @param fromState 可空原状态
     * @param toState 可空目标状态
     */
    public ValidationReviewException(
            String errorCode,
            String message,
            UUID reviewCaseId,
            ValidationReviewState fromState,
            ValidationReviewState toState
    ) {
        super(message);
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("errorCode must not be blank");
        }
        this.errorCode = errorCode;
        this.reviewCaseId = reviewCaseId;
        this.fromState = fromState;
        this.toState = toState;
    }

    /** @return 稳定错误码 */
    public String errorCode() {
        return errorCode;
    }

    /** @return 可空 case id */
    public UUID reviewCaseId() {
        return reviewCaseId;
    }

    /** @return 可空原状态 */
    public ValidationReviewState fromState() {
        return fromState;
    }

    /** @return 可空目标状态 */
    public ValidationReviewState toState() {
        return toState;
    }
}
