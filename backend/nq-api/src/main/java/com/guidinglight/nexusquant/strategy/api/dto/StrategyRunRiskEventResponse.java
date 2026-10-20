package com.guidinglight.nexusquant.strategy.api.dto;

import com.guidinglight.nexusquant.strategy.domain.StrategyRunRiskEventItem;

import java.time.Instant;

/** 保留正式风控决策与原始原因；空列表不解释为整次运行无风险。 */
public record StrategyRunRiskEventResponse(
        String riskEventId,
        String orderId,
        String ruleId,
        String decision,
        String reason,
        String severity,
        String traceId,
        Instant createdAt) {
    public static StrategyRunRiskEventResponse from(StrategyRunRiskEventItem item) {
        return new StrategyRunRiskEventResponse(
                item.riskEventId(),
                item.orderId(),
                item.ruleId(),
                item.decision(),
                item.reason(),
                item.severity(),
                item.traceId(),
                item.createdAt());
    }
}
