package com.guidinglight.nexusquant.app.research;

import com.guidinglight.nexusquant.research.model.ResearchConfig;
import com.guidinglight.nexusquant.research.service.ResearchConfigCreateRequest;
import com.guidinglight.nexusquant.research.service.ResearchConfigService;

import java.util.Objects;

import org.springframework.stereotype.Service;

/**
 * GateFResearchConfigApplicationService 负责把 HTTP 输入映射到研究域服务。
 */
@Service
public class GateFResearchConfigApplicationService {

    private final ResearchConfigService researchConfigService;

    public GateFResearchConfigApplicationService(ResearchConfigService researchConfigService) {
        this.researchConfigService = Objects.requireNonNull(
                researchConfigService,
                "researchConfigService must not be null"
        );
    }

    public ResearchConfig create(String sourceStrategyId, String name, String description,
                                 String parameterSchema, String parameterDefaults, String datasetSpec) {
        return researchConfigService.create(new ResearchConfigCreateRequest(
                sourceStrategyId,
                name,
                description,
                parameterSchema,
                parameterDefaults,
                datasetSpec
        ));
    }
}
