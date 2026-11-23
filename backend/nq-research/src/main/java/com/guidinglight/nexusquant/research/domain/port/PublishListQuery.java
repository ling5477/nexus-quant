package com.guidinglight.nexusquant.research.domain.port;

import com.guidinglight.nexusquant.research.domain.PublishStatus;

import java.util.Locale;

/** 发布页保留父配置/源策略子串与版本精确过滤语义；所有筛选先在SQL执行。 */
public record PublishListQuery(
        String researchConfigId,
        String backtestConfigId,
        String sourceStrategyId,
        String strategyVersionId,
        String publishStatus,
        int limit,
        int offset) {
    public PublishListQuery {
        researchConfigId = normalize(researchConfigId, 128);
        backtestConfigId = normalize(backtestConfigId, 128);
        sourceStrategyId = normalize(sourceStrategyId, 128);
        strategyVersionId = normalize(strategyVersionId, 128);
        publishStatus = normalize(publishStatus, 32);
        if (publishStatus != null)
            publishStatus = PublishStatus.valueOf(publishStatus.toUpperCase(Locale.ROOT)).name();
        if (limit < 1 || limit > 100 || offset < 0)
            throw new IllegalArgumentException("invalid publish page window");
    }

    private static String normalize(String value, int maximum) {
        if (value == null) return null;
        if (value.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("invalid publish filter");
        if (value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > maximum)
            throw new IllegalArgumentException("invalid publish filter");
        return normalized;
    }
}
