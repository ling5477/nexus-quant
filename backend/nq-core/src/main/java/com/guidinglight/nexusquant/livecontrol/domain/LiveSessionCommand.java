package com.guidinglight.nexusquant.livecontrol.domain;

/** LiveSession 状态机命令。 */
public enum LiveSessionCommand {
    APPROVE,
    REJECT,
    APPROVAL_EXPIRED,
    START,
    ACTIVATE,
    PAUSE,
    RESUME,
    STOP,
    BEGIN_RECONCILE,
    RECONCILE_PASS,
    RECONCILE_BLOCK,
    RESOLVE_AND_CLOSE,
    KILL,
    FAIL
}
