package com.guidinglight.nexusquant.strategy.api.dto;

import com.guidinglight.nexusquant.strategy.domain.StrategySchedule;
import com.guidinglight.nexusquant.strategy.domain.StrategyScheduleCreation;
import com.guidinglight.nexusquant.strategy.domain.StrategyScheduleCreationSpec;
import com.guidinglight.nexusquant.strategy.domain.StrategyScheduleStatus;

import java.time.Instant;
import java.util.UUID;

/** 此响应是原创建回执；当前计划状态必须另读详情，账户身份始终保持字符串。 */
public record StrategyScheduleCreationResponse(
        UUID creationId,
        String status,
        int requestVersion,
        Input request,
        Snapshot result,
        String traceId,
        Instant completedAt) {
    public static StrategyScheduleCreationResponse from(StrategyScheduleCreation value) {
        return new StrategyScheduleCreationResponse(
                value.creationId(),
                "COMPLETED",
                value.requestVersion(),
                Input.from(value.request()),
                Snapshot.from(value.result()),
                value.traceId(),
                value.completedAt());
    }

    /** 原始窗口文本与规范化字段共同描述最初意图，不根据当前表单重新拼装。 */
    public record Input(
            String strategyId,
            String scheduleType,
            String cronExpr,
            String timezone,
            boolean enabled,
            String windowConfig,
            String dedupScope) {
        static Input from(StrategyScheduleCreationSpec value) {
            return new Input(
                    value.strategyId(),
                    value.scheduleType(),
                    value.cronExpr(),
                    value.timezone(),
                    value.enabled(),
                    value.windowConfig(),
                    value.dedupScope());
        }
    }

    /** 原计划持久化快照；后续启停、策略修改或时区变化均不得改写。 */
    public record Snapshot(
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
            String accountId,
            String tradeEnv,
            Instant lastTriggeredAt,
            Instant createdAt,
            Instant updatedAt) {
        static Snapshot from(StrategySchedule value) {
            return new Snapshot(
                    value.scheduleJobId(),
                    value.strategyId(),
                    value.scheduleType(),
                    value.cronExpr(),
                    value.timezone(),
                    value.enabled(),
                    value.status(),
                    value.windowConfig(),
                    value.dedupScope(),
                    value.exchangeCode(),
                    value.accountId() == null ? null : value.accountId().toString(),
                    value.tradeEnv(),
                    value.lastTriggeredAt(),
                    value.createdAt(),
                    value.updatedAt());
        }
    }
}
