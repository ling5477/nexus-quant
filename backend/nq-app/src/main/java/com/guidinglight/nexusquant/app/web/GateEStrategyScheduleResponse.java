package com.guidinglight.nexusquant.app.web;

import com.guidinglight.nexusquant.core.model.StrategySchedule;
import com.guidinglight.nexusquant.core.model.StrategyScheduleStatus;

import java.time.Instant;

/**
 * GateEStrategyScheduleResponse 描述 GateE-2.1 计划配置最小返回。
 */
public record GateEStrategyScheduleResponse(
        String scheduleJobId,
        String strategyId,
        String scheduleType,
        String cronExpr,
        String timezone,
        boolean enabled,
        StrategyScheduleStatus status,
        String windowConfig,
        String dedupScope,
        String exchangeCode,
        Long accountId,
        String tradeEnv,
        Instant lastTriggeredAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static GateEStrategyScheduleResponse from(StrategySchedule schedule) {
        return new GateEStrategyScheduleResponse(
                schedule.scheduleJobId(),
                schedule.strategyId(),
                schedule.scheduleType(),
                schedule.cronExpr(),
                schedule.timezone(),
                schedule.enabled(),
                schedule.status(),
                schedule.windowConfig(),
                schedule.dedupScope(),
                schedule.exchangeCode(),
                schedule.accountId(),
                schedule.tradeEnv(),
                schedule.lastTriggeredAt(),
                schedule.createdAt(),
                schedule.updatedAt()
        );
    }
}
