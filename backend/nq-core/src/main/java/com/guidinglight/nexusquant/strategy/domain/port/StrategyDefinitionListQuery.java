package com.guidinglight.nexusquant.strategy.domain.port;

/** 复用原策略读取范围，ID精确定位，其余文字筛选保持原页面的字面子串含义。 */
public record StrategyDefinitionListQuery(
        String strategyId,
        String strategyCode,
        String strategyName,
        String strategyType,
        String exchangeCode,
        String tradeEnv,
        Boolean enabled,
        int limit,
        int offset) {
    public StrategyDefinitionListQuery {
        strategyId = normalize(strategyId, 128);
        strategyCode = normalize(strategyCode, 128);
        strategyName = normalize(strategyName, 255);
        strategyType = normalize(strategyType, 64);
        exchangeCode = normalize(exchangeCode, 64);
        tradeEnv = normalize(tradeEnv, 64);
        if (limit < 1 || limit > 100 || offset < 0) {
            throw new IllegalArgumentException("invalid strategy definition page window");
        }
    }

    private static String normalize(String value, int maximum) {
        if (value == null) return null;
        // 原始控制字符在trim前拒绝，避免把无效输入静默解释成无过滤条件。
        if (value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("invalid strategy definition filter");
        }
        if (value.isBlank()) return null;
        String result = value.trim();
        if (result.length() > maximum) {
            throw new IllegalArgumentException("invalid strategy definition filter");
        }
        return result;
    }
}
