package com.guidinglight.nexusquant.core.model;

/**
 * StrategyScheduleStatus 表示计划配置级启停状态。
 */
public enum StrategyScheduleStatus {
    ENABLED,
    DISABLED;

    public static StrategyScheduleStatus fromEnabled(boolean enabled) {
        return enabled ? ENABLED : DISABLED;
    }
}
