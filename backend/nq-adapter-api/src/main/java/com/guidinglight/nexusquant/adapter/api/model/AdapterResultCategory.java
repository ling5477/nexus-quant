package com.guidinglight.nexusquant.adapter.api.model;

/**
 * AdapterResultCategory 统一描述 adapter 返回结果的规范化分类。
 * <p>
 * Why:
 * GateE-0.3 需要让 place/cancel/query-confirm/reconcile/recovery 使用同一套解释口径，
 * 因此必须把“成功”“暂时不可见”“可重试失败”“远端不可用”等分类提升到 adapter-api。
 */
public enum AdapterResultCategory {

    SUCCESS,
    ACCEPTED,
    NOT_FOUND,
    DEFERRED,
    RETRYABLE_FAILURE,
    FATAL_FAILURE,
    THROTTLED,
    AUTH_FAILURE,
    REMOTE_UNAVAILABLE;

    public boolean isFailure() {
        return this == RETRYABLE_FAILURE
                || this == FATAL_FAILURE
                || this == THROTTLED
                || this == AUTH_FAILURE
                || this == REMOTE_UNAVAILABLE;
    }

    public boolean isTerminalFailure() {
        return this == FATAL_FAILURE || this == AUTH_FAILURE;
    }
}
