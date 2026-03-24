package com.guidinglight.nexusquant.app.web;

/**
 * GateEStrategyScheduleCreateRequest 描述 GateE-2.1 创建计划配置请求体。
 */
public record GateEStrategyScheduleCreateRequest(
        String scheduleType,
        String cronExpr,
        String timezone,
        boolean enabled,
        String windowConfig,
        String dedupScope
) {
}
