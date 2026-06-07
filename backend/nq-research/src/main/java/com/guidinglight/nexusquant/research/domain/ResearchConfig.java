package com.guidinglight.nexusquant.research.domain;

import java.time.Instant;
import java.util.Locale;

/**
 * ResearchConfig 表示研究配置事实。
 * <p>
 * Why:
 * 研究域必须保存 `sourceStrategyId + strategySnapshot` 的双重信息，
 * 这样后续 strategy_definitions 继续演进时，历史研究配置仍能保持可复盘、可审计。
 * V28 以后 status/archive 字段只描述配置元数据生命周期；默认业务列表可隐藏 ARCHIVED，
 * 但按 ID 查询仍需要保留 archived 配置，用于历史回测、评估和发布记录追溯。
 */
public record ResearchConfig(
        String researchConfigId,
        String sourceStrategyId,
        String strategySnapshot,
        String name,
        String description,
        String parameterSchema,
        String parameterDefaults,
        String datasetSpec,
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

    public ResearchConfig {
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

    public ResearchConfig(
            String researchConfigId,
            String sourceStrategyId,
            String strategySnapshot,
            String name,
            String description,
            String parameterSchema,
            String parameterDefaults,
            String datasetSpec,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(
                researchConfigId,
                sourceStrategyId,
                strategySnapshot,
                name,
                description,
                parameterSchema,
                parameterDefaults,
                datasetSpec,
                createdAt,
                updatedAt,
                STATUS_ACTIVE,
                null,
                null,
                null
        );
    }

    /**
     * 判断配置是否可用于创建新的回测配置或运行。
     * Why:
     * DISABLED 与 ARCHIVED 都不应启动新的业务运行；ARCHIVED 还会从默认列表隐藏，
     * 但这两个状态都必须允许按 ID 读取，以支持历史追溯。
     *
     * @return `true` 表示 status 为 ACTIVE
     */
    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }

    /**
     * 判断配置是否处于归档状态。
     * Why:
     * Repository 默认列表需要统一识别 ARCHIVED，而不是各调用方重复硬编码字符串。
     *
     * @return `true` 表示 status 为 ARCHIVED
     */
    public boolean isArchived() {
        return STATUS_ARCHIVED.equals(status);
    }

    /**
     * 构造归档后的研究配置快照。
     * Why:
     * Archive command 必须保持配置本身可追溯，只改变生命周期元数据；
     * 这个方法让 in-memory 测试仓储和未来读模型更新复用同一份字段拷贝语义。
     *
     * @param archivedAt 归档时间，必须非空
     * @param archivedBy 归档操作者标识，可空但通常由 API 层解析当前用户
     * @param archiveReason 归档原因，可空，不得包含敏感信息
     * @return status 为 ARCHIVED 的研究配置快照
     */
    public ResearchConfig archive(Instant archivedAt, String archivedBy, String archiveReason) {
        return new ResearchConfig(
                researchConfigId,
                sourceStrategyId,
                strategySnapshot,
                name,
                description,
                parameterSchema,
                parameterDefaults,
                datasetSpec,
                createdAt,
                archivedAt,
                STATUS_ARCHIVED,
                archivedAt,
                archivedBy,
                archiveReason
        );
    }

    private static String normalizeStatus(String value) {
        String normalized = value == null || value.isBlank()
                ? STATUS_ACTIVE
                : value.trim().toUpperCase(Locale.ROOT);
        if (!STATUS_ACTIVE.equals(normalized)
                && !STATUS_ARCHIVED.equals(normalized)
                && !STATUS_DISABLED.equals(normalized)) {
            throw new IllegalArgumentException("unsupported research config status: " + value);
        }
        return normalized;
    }

    private static String normalizeNullableText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

