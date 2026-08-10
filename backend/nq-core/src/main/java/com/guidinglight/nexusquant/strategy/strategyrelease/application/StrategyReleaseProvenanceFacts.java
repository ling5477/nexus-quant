package com.guidinglight.nexusquant.strategy.strategyrelease.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Strategy Release production service 所需的最小本地只读 provenance 事实。
 *
 * <p>该 record 隔离 core 与 JDBC/nq-research 写侧模型；所有字段只来自一次有界 SELECT，
 * 不包含 snapshot JSON、credential、账户、订单、风险或交易数据。
 */
public record StrategyReleaseProvenanceFacts(
        boolean present,
        String publishRecordId,
        String backtestRunId,
        String publishStrategyVersionId,
        String runStrategyVersionId,
        UUID datasetId,
        String evaluationId,
        String evaluationBacktestRunId,
        String publishStatus,
        String evaluationStatus,
        boolean strategyVersionPresent,
        boolean datasetPresent,
        Instant createdAt,
        Instant publishedAt,
        String artifactStorageKey,
        String manifestStorageKey
) {
    public static StrategyReleaseProvenanceFacts missing(String publishRecordId) {
        return new StrategyReleaseProvenanceFacts(
                false,
                publishRecordId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                null,
                null,
                null,
                null
        );
    }
}
