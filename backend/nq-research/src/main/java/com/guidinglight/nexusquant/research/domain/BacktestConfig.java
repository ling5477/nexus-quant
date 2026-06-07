package com.guidinglight.nexusquant.research.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;

/**
 * BacktestConfig 表示回测配置事实。
 * <p>
 * Why:
 * 回测配置从属于研究配置，但需要独立固化运行窗口、初始资金和执行参数，
 * 这样 GateF-2 以后接入真实运行链时，仍可以围绕同一个配置对象扩展，而不污染执行域对象。
 * GateI-2 在此基础上把 strategy version、参数快照、配置快照和 dataset 快照收口到配置事实，
 * 确保后续 run 创建时可以一次性固化完整输入，而不依赖运行时可变策略定义。
 * V28 以后 status/archive 字段只描述配置元数据生命周期；默认列表隐藏 ARCHIVED，
 * 按 ID 查询仍保留读取能力，以免破坏历史 run、evaluation 和 publish traceability。
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
        Instant updatedAt,
        String status,
        Instant archivedAt,
        String archivedBy,
        String archiveReason
) {
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ARCHIVED = "ARCHIVED";
    public static final String STATUS_DISABLED = "DISABLED";

    public BacktestConfig {
        status = normalizeStatus(status);
        archivedBy = normalizeNullableText(archivedBy);
        archiveReason = normalizeNullableText(archiveReason);
        if (STATUS_ARCHIVED.equals(status)) {
            if (archivedAt == null) {
                throw new IllegalArgumentException("archivedAt must not be null when status is ARCHIVED");
            }
        } else if (archivedAt != null || archivedBy != null || archiveReason != null) {
            throw new IllegalArgumentException("archive metadata is only allowed when status is ARCHIVED");
        }
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
                strategyVersionId,
                strategyVersionSnapshotJson,
                paramSnapshotJson,
                configSnapshotJson,
                datasetId,
                datasetSnapshotJson,
                configSnapshot,
                createdAt,
                updatedAt,
                STATUS_ACTIVE,
                null,
                null,
                null
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
                updatedAt,
                STATUS_ACTIVE,
                null,
                null,
                null
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
                updatedAt,
                STATUS_ACTIVE,
                null,
                null,
                null
        );
    }

    /**
     * 判断配置是否可用于创建新的回测运行。
     * Why:
     * DISABLED 表示配置仍在默认列表中可见但不可用于新运行；ARCHIVED 额外从默认列表隐藏。
     * 统一在 domain 暴露状态判断，可以避免 Service 与 Repository 重复散落状态字符串。
     *
     * @return `true` 表示 status 为 ACTIVE
     */
    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }

    /**
     * 判断配置是否处于归档状态。
     * Why:
     * Repository 默认列表过滤和测试 in-memory 仓储都需要同一语义，避免列表隐藏规则漂移。
     *
     * @return `true` 表示 status 为 ARCHIVED
     */
    public boolean isArchived() {
        return STATUS_ARCHIVED.equals(status);
    }

    private static String normalizeStatus(String value) {
        String normalized = value == null || value.isBlank()
                ? STATUS_ACTIVE
                : value.trim().toUpperCase(Locale.ROOT);
        if (!STATUS_ACTIVE.equals(normalized)
                && !STATUS_ARCHIVED.equals(normalized)
                && !STATUS_DISABLED.equals(normalized)) {
            throw new IllegalArgumentException("unsupported backtest config status: " + value);
        }
        return normalized;
    }

    private static String normalizeNullableText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

