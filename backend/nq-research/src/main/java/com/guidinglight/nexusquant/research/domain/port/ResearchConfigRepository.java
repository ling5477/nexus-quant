package com.guidinglight.nexusquant.research.domain.port;

import com.guidinglight.nexusquant.research.domain.ResearchConfig;

import java.util.List;
import java.util.Optional;

/**
 * ResearchConfigRepository 负责 research_configs 的持久化访问。
 */
public interface ResearchConfigRepository {

    void insert(ResearchConfig researchConfig);

    Optional<ResearchConfig> findByResearchConfigId(String researchConfigId);

    List<ResearchConfig> listAll();

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
        if (sourceStrategyId == null || sourceStrategyId.isBlank()) {
            return listAll();
        }
        String normalizedSourceStrategyId = sourceStrategyId.trim();
        return listAll().stream()
                .filter(item -> normalizedSourceStrategyId.equals(item.sourceStrategyId()))
                .toList();
    }
}


