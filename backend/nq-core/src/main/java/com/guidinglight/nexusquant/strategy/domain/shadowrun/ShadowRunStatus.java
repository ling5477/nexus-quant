package com.guidinglight.nexusquant.strategy.domain.shadowrun;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Shadow Run 本地事实状态枚举。
 *
 * <p>这些状态只描述 GateR 本地无真实交易副作用的影子运行生命周期，不表达交易授权、
 * LIVE ready 或真实交易所能力。
 */
public enum ShadowRunStatus {
    CREATED,
    PRECHECKING,
    READY,
    RUNNING,
    STOP_REQUESTED,
    STOPPED,
    COMPLETED,
    BLOCKED,
    FAILED,
    CANCELLED;

    private static final Set<ShadowRunStatus> TERMINAL_STATUSES = EnumSet.of(
            STOPPED,
            COMPLETED,
            BLOCKED,
            FAILED,
            CANCELLED
    );

    public boolean terminal() {
        return TERMINAL_STATUSES.contains(this);
    }

    public static ShadowRunStatus fromDatabase(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("shadow run status must not be blank");
        }
        return ShadowRunStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
