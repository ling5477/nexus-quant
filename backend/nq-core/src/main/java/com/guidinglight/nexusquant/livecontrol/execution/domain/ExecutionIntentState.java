package com.guidinglight.nexusquant.livecontrol.execution.domain;

/** V39 唯一允许的 execution intent 状态；禁止引入近义状态。 */
public enum ExecutionIntentState {
    CREATED,
    CLAIMED,
    SEND_STARTED,
    SEND_SUCCEEDED,
    UNKNOWN,
    FAILED,
    CANCELLED,
    RECONCILED
}
