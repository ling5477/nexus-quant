package com.guidinglight.nexusquant.strategy.api.dto;

import com.guidinglight.nexusquant.strategy.domain.StrategySchedulePage;
import com.guidinglight.nexusquant.strategy.domain.StrategyScheduleStatus;
import com.guidinglight.nexusquant.strategy.domain.StrategyScheduleSummary;

import java.time.Instant;
import java.util.List;

/** 列表不包含窗口JSON；账户身份用字符串传输，避免浏览器整数精度损失。 */
public record StrategySchedulePageResponse(
        List<ScheduleItem> items, long total, int limit, int offset) {
    public static StrategySchedulePageResponse from(StrategySchedulePage page) {
        return new StrategySchedulePageResponse(
                page.items().stream().map(ScheduleItem::from).toList(),
                page.total(),
                page.limit(),
                page.offset());
    }

    /** 调度状态由正式enabled字段派生，不代表策略执行状态或LIVE权限。 */
    public record ScheduleItem(
            String scheduleJobId,
            String strategyId,
            String scheduleType,
            String cronExpr,
            String timezone,
            boolean enabled,
            StrategyScheduleStatus status,
            String exchangeCode,
            String accountId,
            String tradeEnv,
            Instant lastTriggeredAt,
            Instant createdAt,
            Instant updatedAt) {
        static ScheduleItem from(StrategyScheduleSummary item) {
            return new ScheduleItem(
                    item.scheduleJobId(),
                    item.strategyId(),
                    item.scheduleType(),
                    item.cronExpr(),
                    item.timezone(),
                    item.enabled(),
                    StrategyScheduleStatus.fromEnabled(item.enabled()),
                    item.exchangeCode(),
                    item.accountId() == null ? null : item.accountId().toString(),
                    item.tradeEnv(),
                    item.lastTriggeredAt(),
                    item.createdAt(),
                    item.updatedAt());
        }
    }
}
