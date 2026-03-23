package com.guidinglight.nexusquant.core.model;

/**
 * StrategyDefinitionStatus 表示策略定义级启停状态。
 */
public enum StrategyDefinitionStatus {
    ENABLED,
    DISABLED;

    public static StrategyDefinitionStatus fromEnabled(boolean enabled) {
        return enabled ? ENABLED : DISABLED;
    }
}
