package com.guidinglight.nexusquant.strategy.domain;

import java.util.List;

/** 总数和窗口来自同一只读快照；缺行或超窗不能伪装成完整历史。 */
public record StrategyRunPage(List<StrategyRunSummary> items, long total, int limit, int offset) {
    public StrategyRunPage {
        items = List.copyOf(items);
        if (total < 0
                || limit < 1
                || limit > 100
                || offset < 0
                || items.size() != Math.min(limit, Math.max(0L, total - offset))) {
            throw new IllegalArgumentException("invalid run page result");
        }
    }
}
