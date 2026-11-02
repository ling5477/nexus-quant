package com.guidinglight.nexusquant.research.domain;

import java.util.List;

/** 总数和当前窗口来自同一权限及数据库快照，不能把少一页记录伪装成正常分页。 */
public record BacktestConfigPage(List<BacktestConfig> items, long total, int limit, int offset) {
    public BacktestConfigPage {
        items = List.copyOf(items);
        if (total < 0
                || limit < 1
                || limit > 100
                || offset < 0
                || items.size() != Math.min(limit, Math.max(0L, total - offset))) {
            throw new IllegalArgumentException("invalid backtest configuration page result");
        }
    }
}
