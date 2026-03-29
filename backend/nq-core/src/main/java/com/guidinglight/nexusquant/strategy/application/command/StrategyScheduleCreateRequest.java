package com.guidinglight.nexusquant.strategy.application;

/**
 * StrategyScheduleCreateRequest 表示创建计划配置的最小入口参数。
 */
public record StrategyScheduleCreateRequest(
        String strategyId,
        String scheduleType,
        String cronExpr,
        String timezone,
        boolean enabled,
        String windowConfig,
        String dedupScope
) {
}


