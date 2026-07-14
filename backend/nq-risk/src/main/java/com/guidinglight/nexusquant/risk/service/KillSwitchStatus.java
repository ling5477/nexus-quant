package com.guidinglight.nexusquant.risk.service;

/**
 * Kill switch 的安全状态。
 *
 * <p>只有 {@link #DISENGAGED} 表示该条 stop control 可以继续下一只读检查；
 * {@link #ENGAGED} 与 {@link #UNKNOWN} 都必须 fail-closed。</p>
 */
public enum KillSwitchStatus {
    ENGAGED,
    DISENGAGED,
    UNKNOWN
}
