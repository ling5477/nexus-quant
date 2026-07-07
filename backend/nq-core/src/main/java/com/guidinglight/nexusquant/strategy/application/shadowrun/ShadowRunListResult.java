package com.guidinglight.nexusquant.strategy.application.shadowrun;

import com.guidinglight.nexusquant.strategy.domain.port.ShadowRunListQuery;
import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRun;

import java.util.List;
import java.util.Objects;

/**
 * Shadow Run list result.
 *
 * <p>items 仅为本地 Shadow Run 主事实；total 是同一筛选条件下的本地表计数，
 * 用于前端列表分页提示，不表达交易授权或运行放行。
 */
public record ShadowRunListResult(
        List<ShadowRun> items,
        int limit,
        int offset,
        long total
) {
    public ShadowRunListResult {
        items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
        if (limit < 1 || limit > ShadowRunListQuery.MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + ShadowRunListQuery.MAX_LIMIT);
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        if (total < 0) {
            throw new IllegalArgumentException("total must not be negative");
        }
    }
}
