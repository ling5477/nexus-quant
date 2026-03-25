package com.guidinglight.nexusquant.research.port;

import com.guidinglight.nexusquant.research.model.ResearchConfig;

import java.util.List;
import java.util.Optional;

/**
 * ResearchConfigRepository 负责 research_configs 的持久化访问。
 */
public interface ResearchConfigRepository {

    void insert(ResearchConfig researchConfig);

    Optional<ResearchConfig> findByResearchConfigId(String researchConfigId);

    List<ResearchConfig> listAll();
}
