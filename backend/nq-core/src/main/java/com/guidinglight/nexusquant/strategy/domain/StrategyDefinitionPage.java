package com.guidinglight.nexusquant.strategy.domain;

import java.util.List;

/** 总数和当前窗口来自同一读取快照，截断或错误窗口不能伪装为完整结果。 */
public record StrategyDefinitionPage(
        List<StrategyDefinition> items, long total, int limit, int offset) {
    public StrategyDefinitionPage {
        items = List.copyOf(items);
        if (total < 0
                || limit < 1
                || limit > 100
                || offset < 0
                || items.size() != Math.min(limit, Math.max(0L, total - offset))) {
            throw new IllegalArgumentException("invalid strategy definition page result");
        }
    }
}
