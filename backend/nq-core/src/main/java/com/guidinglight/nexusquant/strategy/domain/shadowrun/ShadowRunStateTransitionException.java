package com.guidinglight.nexusquant.strategy.domain.shadowrun;

/**
 * Shadow Run 状态机非法流转异常。
 *
 * <p>Repository / service 捕获该异常时应记录 `ILLEGAL_STATE_TRANSITION_ATTEMPT`
 * 事件，并保持 `shadow_runs.status` 不变。
 */
public class ShadowRunStateTransitionException extends RuntimeException {

    private final ShadowRunStatus fromStatus;
    private final ShadowRunStatus toStatus;
    private final String reasonCode;

    public ShadowRunStateTransitionException(ShadowRunStatus fromStatus, ShadowRunStatus toStatus, String reasonCode) {
        super("invalid shadow run transition: " + fromStatus + " -> " + toStatus + " (" + reasonCode + ")");
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reasonCode = reasonCode;
    }

    public ShadowRunStatus fromStatus() {
        return fromStatus;
    }

    public ShadowRunStatus toStatus() {
        return toStatus;
    }

    public String reasonCode() {
        return reasonCode;
    }
}
