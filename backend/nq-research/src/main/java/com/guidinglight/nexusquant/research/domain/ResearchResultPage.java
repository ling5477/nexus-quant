package com.guidinglight.nexusquant.research.domain;

import java.util.List;

/** 研究结果总数与当前窗口必须同口径，拒绝把缺失行或截断窗口伪装成完整分页。 */
public record ResearchResultPage<T>(List<T> items, long total, int limit, int offset) {
    public ResearchResultPage {
        items = List.copyOf(items);
        if (total < 0
                || limit < 1
                || limit > 100
                || offset < 0
                || items.size() != Math.min(limit, Math.max(0L, total - offset))) {
            throw new IllegalArgumentException("invalid research result page");
        }
    }
}
