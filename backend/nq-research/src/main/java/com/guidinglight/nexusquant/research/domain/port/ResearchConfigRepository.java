package com.guidinglight.nexusquant.research.domain.port;

import com.guidinglight.nexusquant.research.domain.ResearchConfig;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * ResearchConfigRepository 负责 research_configs 的持久化访问。
 * <p>
 * Why:
 * V28 把研究配置生命周期收口到 status/archive 字段；仓储默认列表必须隐藏 ARCHIVED，
 * 但按 ID 查询和内部 includeArchived 查询仍要保留历史追溯入口。
 */
public interface ResearchConfigRepository {

    void insert(ResearchConfig researchConfig);

    Optional<ResearchConfig> findByResearchConfigId(String researchConfigId);

    /**
     * 把研究配置标记为归档。
     * Why:
     * 归档是配置生命周期命令，不是删除；Repository 只更新 V28 生命周期字段，
     * 不触碰回测运行、评估、发布或任何事实表。已归档记录由 Service 做幂等处理。
     *
     * @param researchConfigId 研究配置 ID
     * @param archivedAt 归档时间，同时作为 updated_at
     * @param archivedBy 归档操作者标识，可空
     * @param archiveReason 归档原因，可空，不得含敏感信息
     * @return 是否更新到一条非 ARCHIVED 记录
     */
    default boolean archive(
            String researchConfigId,
            Instant archivedAt,
            String archivedBy,
            String archiveReason
    ) {
        return false;
    }

    /**
     * 查询默认业务列表。
     * Why:
     * 默认列表用于控制台和业务选择面，应排除 ARCHIVED；DISABLED 仍可见，
     * 方便用户理解配置被停用而非被归档隐藏。
     *
     * @return 默认业务可见的研究配置列表
     */
    List<ResearchConfig> listAll();

    /**
     * 查询包含归档配置的内部列表。
     * Why:
     * 历史追溯、审计或内部测试可能需要读取 ARCHIVED，但本轮不新增外部 API 参数，
     * 因此该方法只作为仓储内部扩展点。
     *
     * @return 包含 ARCHIVED 的研究配置列表
     */
    default List<ResearchConfig> listAllIncludingArchived() {
        return listAll();
    }

    /**
     * 按 sourceStrategyId 过滤研究配置。
     * Why:
     * 控制台联调早期的研究配置列表页至少需要一个稳定的上游策略过滤维度，
     * 同时保留 `null` 表示“查询全部”的最小查询口径。
     *
     * @param sourceStrategyId 上游策略定义 ID，可空
     * @return 满足条件的研究配置列表
     */
    default List<ResearchConfig> list(String sourceStrategyId) {
        return list(sourceStrategyId, false);
    }

    /**
     * 按 sourceStrategyId 过滤研究配置，并允许内部调用显式包含归档记录。
     * Why:
     * 外部 API 本轮不增加 includeArchived 参数；Repository 仍保留内部扩展点，
     * 防止历史追溯查询被默认列表过滤规则误伤。
     *
     * @param sourceStrategyId 上游策略定义 ID，可空
     * @param includeArchived 是否包含 ARCHIVED
     * @return 满足条件的研究配置列表
     */
    default List<ResearchConfig> list(String sourceStrategyId, boolean includeArchived) {
        List<ResearchConfig> source = includeArchived ? listAllIncludingArchived() : listAll();
        if (sourceStrategyId == null || sourceStrategyId.isBlank()) {
            return source;
        }
        String normalizedSourceStrategyId = sourceStrategyId.trim();
        return source.stream()
                .filter(item -> normalizedSourceStrategyId.equals(item.sourceStrategyId()))
                .toList();
    }
}


