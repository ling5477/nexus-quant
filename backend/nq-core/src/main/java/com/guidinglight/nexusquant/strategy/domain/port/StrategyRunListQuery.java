package com.guidinglight.nexusquant.strategy.domain.port;

import com.guidinglight.nexusquant.strategy.domain.StrategyRunStatus;

import java.util.Locale;
import java.util.Set;

/** 沿用单一策略或计划的读取范围；状态和触发方式作用于全部历史，不能在截断窗口后再过滤。 */
public record StrategyRunListQuery(
        String strategyId,
        String scheduleId,
        String status,
        String triggerType,
        int limit,
        int offset) {
    private static final Set<String> TRIGGER_TYPES =
            Set.of("MANUAL", "SCHEDULED", "SCHEDULER", "RECOVERY");

    public StrategyRunListQuery {
        strategyId = normalize(strategyId, 128);
        scheduleId = normalize(scheduleId, 128);
        if ((strategyId == null) == (scheduleId == null)) {
            throw new IllegalArgumentException(
                    "exactly one of strategyId or scheduleId must be provided");
        }
        status = normalize(status, 32);
        if (status != null)
            status = StrategyRunStatus.valueOf(status.toUpperCase(Locale.ROOT)).name();
        triggerType = normalize(triggerType, 32);
        if (triggerType != null) {
            triggerType = triggerType.toUpperCase(Locale.ROOT);
            if (!TRIGGER_TYPES.contains(triggerType))
                throw new IllegalArgumentException("invalid run trigger type");
        }
        if (limit < 1 || limit > 100 || offset < 0)
            throw new IllegalArgumentException("invalid run page window");
    }

    private static String normalize(String value, int maximum) {
        if (value == null) return null;
        // 先拒绝控制字符，不能把无效范围静默变成无条件读取。
        if (value.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("invalid run filter");
        if (value.isBlank()) return null;
        String result = value.trim();
        if (result.length() > maximum) throw new IllegalArgumentException("invalid run filter");
        return result;
    }
}
