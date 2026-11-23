package com.guidinglight.nexusquant.strategy.domain;

import java.util.List;

/** 每个子页明确所属运行；错误窗口和缺行不能伪装成空订单或空成交。 */
public record StrategyRunRelatedPage<T>(
        String strategyRunId, List<T> items, long total, int limit, int offset) {
    public StrategyRunRelatedPage {
        items = List.copyOf(items);
        if (strategyRunId == null
                || strategyRunId.isBlank()
                || total < 0
                || limit < 1
                || limit > 100
                || offset < 0
                || items.size() != Math.min(limit, Math.max(0L, total - offset))) {
            throw new IllegalArgumentException("invalid run related page");
        }
    }
}
