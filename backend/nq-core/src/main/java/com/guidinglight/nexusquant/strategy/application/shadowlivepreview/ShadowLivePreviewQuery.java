package com.guidinglight.nexusquant.strategy.application.shadowlivepreview;

import java.util.UUID;

/**
 * ShadowLivePreviewQuery 描述 GateQ-3 Shadow Live no-side-effect preview 的查询范围。
 *
 * <p>Why: 本 query 只选择已有 strategy version、dataset、evaluation、publish、Paper 和 Shadow
 * 事实，不创建 shadow run、不启动 runner、不访问外部网络、不读取敏感材料，也不改变任何交易状态。
 */
public record ShadowLivePreviewQuery(
        String strategyId,
        String strategyVersionId,
        UUID datasetId,
        String evaluationId,
        String publishId,
        String paperRunId,
        String shadowRunId
) {
    public ShadowLivePreviewQuery {
        strategyId = normalize(strategyId);
        strategyVersionId = normalize(strategyVersionId);
        evaluationId = normalize(evaluationId);
        publishId = normalize(publishId);
        paperRunId = normalize(paperRunId);
        shadowRunId = normalize(shadowRunId);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
