package com.guidinglight.nexusquant.research.application.api;

import com.guidinglight.nexusquant.research.domain.ResearchConfig;
import com.guidinglight.nexusquant.research.application.command.ResearchConfigCreateRequest;
import com.guidinglight.nexusquant.research.application.ResearchConfigService;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * ResearchConfigApiService 负责把 HTTP 请求映射到研究配置领域服务。
 *
 * Why:
 * PRE-CLEAN-2 后，HTTP API 层只保留 controller/dto/web adapter，
 * 研究配置的控制器专用入参编排回到 `nq-research` application owner，避免 `nq-api` 演化成业务 façade。
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

    /**
     * 查询研究配置详情。
     *
     * @param researchConfigId 研究配置 ID
     * @return 研究配置详情
     */
    public ResearchConfig getByResearchConfigId(String researchConfigId) {
        try {
            return researchConfigService.getByResearchConfigId(researchConfigId);
        } catch (IllegalArgumentException ex) {
            throw toNotFound(ex);
        }
    }

    /**
     * 查询研究配置列表。
     *
     * @param sourceStrategyId 上游策略定义 ID，可空
     * @return 研究配置列表
     */
    public List<ResearchConfig> list(String sourceStrategyId) {
        return researchConfigService.list(sourceStrategyId);
    }

    private ResponseStatusException toNotFound(IllegalArgumentException ex) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
    }
}



