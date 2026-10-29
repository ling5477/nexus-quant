package com.guidinglight.nexusquant.strategy.domain;

import java.time.Instant;

/** 列表只投影配置身份和调度状态，窗口JSON按需读取，避免随整个目录传输。 */
public record StrategyScheduleSummary(
        String scheduleJobId,
        String strategyId,
        String scheduleType,
        String cronExpr,
        String timezone,
        boolean enabled,
        String exchangeCode,
        Long accountId,
        String tradeEnv,
        Instant lastTriggeredAt,
        Instant createdAt,
        Instant updatedAt) {}
