package com.guidinglight.nexusquant.strategy.domain;

import java.util.List;

/** 总数对应完整筛选范围，不能将当前窗口长度当作计划总数。 */
public record StrategySchedulePage(
        List<StrategyScheduleSummary> items, long total, int limit, int offset) {
    public StrategySchedulePage {
        items = List.copyOf(items);
    }
}
