package com.guidinglight.nexusquant.strategy.domain.port;

import com.guidinglight.nexusquant.strategy.domain.StrategyScheduleStatus;

import java.util.Locale;
import java.util.Set;

/** 保留原策略范围；计划身份是精确值，状态筛选覆盖全部记录而非当前窗口。 */
public record StrategyScheduleListQuery(
        String strategyId,
        String scheduleId,
        String status,
        String scheduleType,
        int limit,
        int offset) {
    public StrategyScheduleListQuery {
        strategyId = normalize(strategyId);
        if (strategyId == null) throw new IllegalArgumentException("strategyId is required");
        scheduleId = normalize(scheduleId);
        status = normalize(status);
        if (status != null)
            status = StrategyScheduleStatus.valueOf(status.toUpperCase(Locale.ROOT)).name();
        scheduleType = normalize(scheduleType);
        if (scheduleType != null) {
            scheduleType = scheduleType.toUpperCase(Locale.ROOT);
            // 历史类型仅恢复读取筛选；当前正式创建与扫描能力不随目录选项扩大。
            if (!Set.of("CRON", "INTERVAL", "MANUAL").contains(scheduleType))
                throw new IllegalArgumentException("invalid schedule type filter");
        }
        if (limit < 1 || limit > 100 || offset < 0)
            throw new IllegalArgumentException("invalid schedule page window");
    }

    /** 保留既有调用方的无类型筛选语义。 */
    public StrategyScheduleListQuery(
            String strategyId, String scheduleId, String status, int limit, int offset) {
        this(strategyId, scheduleId, status, null, limit, offset);
    }

    private static String normalize(String value) {
        if (value == null) return null;
        if (value.length() > 128 || value.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("invalid schedule filter");
        return value.isBlank() ? null : value.trim();
    }
}
