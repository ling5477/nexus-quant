package com.guidinglight.nexusquant.livecontrol.domain;

/** LIVE control-plane 会话状态；这些状态不表示 LIVE 已获授权。 */
public enum LiveSessionState {
    APPROVAL_PENDING,
    APPROVED,
    LIVE_WARMUP,
    LIVE_ACTIVE,
    LIVE_PAUSED,
    LIVE_STOPPED,
    LIVE_RECONCILING,
    RECONCILIATION_BLOCKED,
    REJECTED,
    FAILED,
    KILLED,
    LIVE_RECONCILED;

    public boolean terminal() {
        return this == REJECTED || this == FAILED || this == KILLED || this == LIVE_RECONCILED;
    }
}
