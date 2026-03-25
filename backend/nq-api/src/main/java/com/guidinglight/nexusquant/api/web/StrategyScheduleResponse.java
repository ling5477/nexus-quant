package com.guidinglight.nexusquant.api.web;

import com.guidinglight.nexusquant.core.model.StrategySchedule;
import com.guidinglight.nexusquant.core.model.StrategyScheduleStatus;

import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * StrategyScheduleResponse 描述接口响应体。
 */
@Schema(name = "StrategyScheduleResponse", description = "接口响应体")
public record StrategyScheduleResponse(
        @Schema(description = "scheduleJobId")
        String scheduleJobId,
        @Schema(description = "strategyId")
        String strategyId,
        @Schema(description = "scheduleType")
        String scheduleType,
        @Schema(description = "cronExpr")
        String cronExpr,
        @Schema(description = "timezone")
        String timezone,
        @Schema(description = "enabled")
        boolean enabled,
        @Schema(description = "status")
        StrategyScheduleStatus status,
        @Schema(description = "windowConfig")
        String windowConfig,
        @Schema(description = "dedupScope")
        String dedupScope,
        @Schema(description = "exchangeCode")
        String exchangeCode,
        @Schema(description = "accountId")
        Long accountId,
        @Schema(description = "tradeEnv")
        String tradeEnv,
        @Schema(description = "lastTriggeredAt")
        Instant lastTriggeredAt,
        @Schema(description = "createdAt")
        Instant createdAt,
        @Schema(description = "updatedAt")
        Instant updatedAt
) {
    public static StrategyScheduleResponse from(StrategySchedule schedule) {
        return new StrategyScheduleResponse(
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
