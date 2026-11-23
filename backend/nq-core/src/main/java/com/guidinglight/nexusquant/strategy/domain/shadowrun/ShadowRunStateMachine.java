package com.guidinglight.nexusquant.strategy.domain.shadowrun;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Shadow Run 最小状态机。
 *
 * <p>状态机只保护本地 Shadow Run fact lifecycle。终态不可回写运行态，避免复盘事实被后续
 * runner 或 repository 更新覆盖；后续如需 retry，应创建新的 Shadow Run 或增加显式 retry fact。
 */
public final class ShadowRunStateMachine {

    private final Map<ShadowRunStatus, Set<ShadowRunStatus>> transitions = new EnumMap<>(ShadowRunStatus.class);

    public ShadowRunStateMachine() {
        register(ShadowRunStatus.CREATED, ShadowRunStatus.PRECHECKING, ShadowRunStatus.FAILED, ShadowRunStatus.CANCELLED);
        register(ShadowRunStatus.PRECHECKING, ShadowRunStatus.READY, ShadowRunStatus.BLOCKED, ShadowRunStatus.FAILED);
        register(ShadowRunStatus.READY, ShadowRunStatus.RUNNING, ShadowRunStatus.FAILED, ShadowRunStatus.CANCELLED);
        register(
                ShadowRunStatus.RUNNING,
                ShadowRunStatus.STOP_REQUESTED,
                ShadowRunStatus.COMPLETED,
                ShadowRunStatus.BLOCKED,
                ShadowRunStatus.FAILED
        );
        register(ShadowRunStatus.STOP_REQUESTED, ShadowRunStatus.STOPPED, ShadowRunStatus.FAILED);
    }

    /**
     * 判断本地状态流转是否合法；该判断只用于 Shadow Run fact lifecycle，不产生任何副作用。
     */
    public boolean canTransition(ShadowRunStatus fromStatus, ShadowRunStatus toStatus) {
        return transitions.getOrDefault(fromStatus, EnumSet.noneOf(ShadowRunStatus.class)).contains(toStatus);
    }

    /**
     * 执行状态流转校验；非法流转抛出带 reason code 的 domain exception，终态不可回写运行态。
     */
    public ShadowRunStatus transition(ShadowRunStatus fromStatus, ShadowRunStatus toStatus) {
        if (!canTransition(fromStatus, toStatus)) {
            String reason = fromStatus != null && fromStatus.terminal()
                    ? "SHADOW_RUN_TERMINAL_STATE_LOCKED"
                    : "SHADOW_RUN_ILLEGAL_STATE_TRANSITION";
            throw new ShadowRunStateTransitionException(fromStatus, toStatus, reason);
        }
        return toStatus;
    }

    private void register(ShadowRunStatus currentStatus, ShadowRunStatus... nextStatuses) {
        EnumSet<ShadowRunStatus> allowedStatuses = EnumSet.noneOf(ShadowRunStatus.class);
        allowedStatuses.addAll(Arrays.asList(nextStatuses));
        transitions.put(currentStatus, allowedStatuses);
    }
}
