package com.guidinglight.nexusquant.research.domain;

import java.util.List;

/** 总数与当前窗口属于同一读取快照，不把一页条数当作全部结果数量。 */
public record ResearchConfigPage(List<ResearchConfig> items, long total, int limit, int offset) {
    public ResearchConfigPage {
        items = List.copyOf(items);
        if (total < 0
                || limit < 1
                || limit > 100
                || offset < 0
                || items.size() > limit
                || items.size() != Math.min(limit, Math.max(0L, total - offset))) {
            throw new IllegalArgumentException("invalid research configuration page result");
        }
    }
}
