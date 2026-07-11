package com.guidinglight.nexusquant.validationreview.domain;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Durable validation review 的纯 domain 状态机。
 *
 * <p>状态机不访问数据库、不调用外部系统，只保护人工复核 lifecycle 的合法有向流转。
 */
public final class ValidationReviewStateMachine {

    private final Map<ValidationReviewState, Set<ValidationReviewState>> transitions =
            new EnumMap<>(ValidationReviewState.class);

    /** 创建固定 GateV-1 合法流转图。 */
    public ValidationReviewStateMachine() {
        transitions.put(
                ValidationReviewState.OPEN,
                EnumSet.of(ValidationReviewState.ACKNOWLEDGED, ValidationReviewState.ESCALATED)
        );
        transitions.put(
                ValidationReviewState.ACKNOWLEDGED,
                EnumSet.of(ValidationReviewState.ESCALATED, ValidationReviewState.RESOLVED)
        );
        transitions.put(ValidationReviewState.ESCALATED, EnumSet.of(ValidationReviewState.RESOLVED));
        transitions.put(ValidationReviewState.RESOLVED, EnumSet.of(ValidationReviewState.CLOSED));
    }

    /**
     * 判断非交易 review lifecycle 流转是否合法。
     *
     * @param fromState 原状态
     * @param toState 目标状态
     * @return 仅固定允许边返回 true
     */
    public boolean canTransition(ValidationReviewState fromState, ValidationReviewState toState) {
        if (fromState == null || toState == null) {
            return false;
        }
        return transitions.getOrDefault(fromState, EnumSet.noneOf(ValidationReviewState.class)).contains(toState);
    }

    /**
     * 校验并生成递增 version 的新 case snapshot。
     *
     * @param current 当前持久 case
     * @param target 目标状态
     * @param actorId 执行动作的本地用户 ID
     * @param occurredAt accepted transition 时间
     * @return 新状态 case；不会修改输入对象或访问数据库
     * @throws ValidationReviewException 非法、自循环或 terminal transition
     */
    public ValidationReviewCase transition(
            ValidationReviewCase current,
            ValidationReviewState target,
            long actorId,
            Instant occurredAt
    ) {
        if (current == null) {
            throw new IllegalArgumentException("current case must not be null");
        }
        ValidationReviewCase.requirePositive(actorId, "actorId");
        if (occurredAt == null || occurredAt.isBefore(current.updatedAt())) {
            throw new IllegalArgumentException("occurredAt must not be before current updatedAt");
        }
        if (!canTransition(current.state(), target)) {
            String code = current.state().terminal()
                    ? "REVIEW_CASE_TERMINAL_STATE_LOCKED"
                    : "REVIEW_STATE_TRANSITION_INVALID";
            throw new ValidationReviewException(
                    code,
                    "invalid validation review transition: " + current.state() + " -> " + target,
                    current.id(),
                    current.state(),
                    target
            );
        }
        return current.transitionedTo(target, actorId, occurredAt);
    }
}
