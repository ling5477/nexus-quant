package com.guidinglight.nexusquant.strategy.domain;

import java.util.List;

/** 页记录和完整总数来自同一只读快照，截断页或错误窗口不能表现成完整目录。 */
public record StrategyVersionPage(
        List<StrategyVersionListItem> items, long total, int limit, int offset) {
    public StrategyVersionPage {
        items = List.copyOf(items);
        if (total < 0
                || limit < 1
                || limit > 100
                || offset < 0
                || items.size() != Math.min(limit, Math.max(0L, total - offset))) {
            throw new IllegalArgumentException("invalid strategy version page result");
        }
    }
}
