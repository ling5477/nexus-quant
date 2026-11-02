package com.guidinglight.nexusquant.strategy.domain.port;

/** 精确run身份和有界窗口是必需条件，禁止无范围加载全站订单或成交。 */
public record StrategyRunRelatedQuery(String strategyRunId, int limit, int offset) {
    public StrategyRunRelatedQuery {
        strategyRunId = requireRunId(strategyRunId);
        if (limit < 1 || limit > 100 || offset < 0)
            throw new IllegalArgumentException("invalid run related window");
    }

    public static String requireRunId(String value) {
        if (value == null
                || value.codePoints().anyMatch(Character::isISOControl)
                || value.isBlank()
                || value.trim().length() > 64)
            throw new IllegalArgumentException("invalid strategy run identity");
        return value.trim();
    }
}
