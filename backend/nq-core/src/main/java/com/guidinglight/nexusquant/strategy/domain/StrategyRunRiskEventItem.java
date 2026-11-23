package com.guidinglight.nexusquant.strategy.domain;

import java.time.Instant;

/** 只返回已明确绑定本运行Order的风控事实，不把账户或全局风险归入本次运行。 */
public record StrategyRunRiskEventItem(
        String riskEventId,
        String orderId,
        String ruleId,
        String decision,
        String reason,
        String severity,
        String traceId,
        Instant createdAt) {}
