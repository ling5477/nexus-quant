package com.guidinglight.nexusquant.strategy.domain.port;

import com.guidinglight.nexusquant.strategy.domain.shadowrun.ShadowRunStatus;

import java.util.UUID;

/**
 * Shadow Run repository list query contract.
 *
 * <p>该 query 只允许 bounded read-only 列表查询。limit 最大 100，避免无分页读取
 * shadow_runs；所有筛选值只用于本地 fact 表查询，不触发 runner、adapter 或交易路径。
 */
public record ShadowRunListQuery(
        ShadowRunStatus status,
        String strategyVersionId,
        UUID datasetId,
        String paperRunId,
        int limit,
        int offset
) {
    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 100;

    public ShadowRunListQuery {
        strategyVersionId = blankToNull(strategyVersionId);
        paperRunId = blankToNull(paperRunId);
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
