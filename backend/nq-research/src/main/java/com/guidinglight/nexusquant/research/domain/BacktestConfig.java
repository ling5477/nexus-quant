package com.guidinglight.nexusquant.research.domain;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * BacktestConfig 表示回测配置事实。
 * <p>
 * Why:
 * 回测配置从属于研究配置，但需要独立固化运行窗口、初始资金和执行参数，
 * 这样 GateF-2 以后接入真实运行链时，仍可以围绕同一个配置对象扩展，而不污染执行域对象。
 * GateI-2 在此基础上把 strategy version、参数快照、配置快照和 dataset 快照收口到配置事实，
 * 确保后续 run 创建时可以一次性固化完整输入，而不依赖运行时可变策略定义。
 */
public record BacktestConfig(
        String backtestConfigId,
        String researchConfigId,
        String name,
        String description,
        Instant startTime,
        Instant endTime,
        BigDecimal initialCapital,
        String executionSpec,
        String evaluationSpec,
        String strategyVersionId,
        String strategyVersionSnapshotJson,
        String paramSnapshotJson,
        String configSnapshotJson,
        String datasetId,
        String datasetSnapshotJson,
        String configSnapshot,
        Instant createdAt,
        Instant updatedAt
) {
    public BacktestConfig(
            String backtestConfigId,
            String researchConfigId,
            String name,
            String description,
            Instant startTime,
            Instant endTime,
            BigDecimal initialCapital,
            String executionSpec,
            String evaluationSpec,
            String configSnapshot,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(
                backtestConfigId,
                researchConfigId,
                name,
                description,
                startTime,
                endTime,
                initialCapital,
                executionSpec,
                evaluationSpec,
                null,
                "{}",
                "{}",
                configSnapshot,
                null,
                "{}",
                configSnapshot,
                createdAt,
                updatedAt
        );
    }

    public BacktestConfig(
            String backtestConfigId,
            String researchConfigId,
            String name,
            String description,
            Instant startTime,
            Instant endTime,
            BigDecimal initialCapital,
            String executionSpec,
            String evaluationSpec,
            String datasetId,
            String datasetSnapshotJson,
            String configSnapshot,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(
                backtestConfigId,
                researchConfigId,
                name,
                description,
                startTime,
                endTime,
                initialCapital,
                executionSpec,
                evaluationSpec,
                null,
                "{}",
                "{}",
                configSnapshot,
                datasetId,
                datasetSnapshotJson,
                configSnapshot,
                createdAt,
                updatedAt
        );
    }
}

