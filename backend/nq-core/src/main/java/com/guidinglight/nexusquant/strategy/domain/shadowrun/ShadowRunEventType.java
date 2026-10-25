package com.guidinglight.nexusquant.strategy.domain.shadowrun;

/**
 * Shadow Run append-only 审计事件类型。
 *
 * <p>事件只描述本地 fact lifecycle 和审计记录，不代表交易授权或真实交易执行。
 */
public enum ShadowRunEventType {
    CREATED,
    PRECHECK_STARTED,
    PRECHECK_PASSED,
    PRECHECK_BLOCKED,
    RUN_STARTED,
    STOP_REQUESTED,
    STOPPED,
    COMPLETED,
    FAILED,
    CANCELLED,
    ILLEGAL_STATE_TRANSITION_ATTEMPT,
    SNAPSHOT_CAPTURED,
    CONSISTENCY_REPORT_GENERATED
}
