package com.guidinglight.nexusquant.api.service;

import com.guidinglight.nexusquant.research.model.ResearchConfig;
import com.guidinglight.nexusquant.research.service.ResearchConfigCreateRequest;
import com.guidinglight.nexusquant.research.service.ResearchConfigService;

import java.util.Objects;

import org.springframework.stereotype.Service;

/**
 * ResearchConfigApiService 负责把 HTTP 请求映射到研究配置领域服务。
 *
 * Why:
 * `nq-api` 现在承接 HTTP API 层，因此控制器专用的入参编排也必须落在该模块，
 * 避免 `nq-app` 继续承担 API application service 的职责。
 */
@Service
public class ResearchConfigApiService {

    private final ResearchConfigService researchConfigService;

    public ResearchConfigApiService(ResearchConfigService researchConfigService) {
        this.researchConfigService = Objects.requireNonNull(
                researchConfigService,
                "researchConfigService must not be null"
        );
    }

    /**
     * 创建研究配置。
     *
     * @param sourceStrategyId 上游策略定义 ID
     * @param name 研究配置名称
     * @param description 研究配置描述
     * @param parameterSchema 参数 schema JSON
     * @param parameterDefaults 参数默认值 JSON
     * @param datasetSpec 数据集约束 JSON
     * @return 已持久化的研究配置事实
     */
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
