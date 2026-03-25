package com.guidinglight.nexusquant.research.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.research.model.ResearchConfig;
import com.guidinglight.nexusquant.research.model.SourceStrategySnapshot;
import com.guidinglight.nexusquant.research.port.ResearchConfigRepository;
import com.guidinglight.nexusquant.research.port.SourceStrategySnapshotRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * ResearchConfigService 提供 GateF-1 的研究配置管理能力。
 * <p>
 * Why:
 * GateF-1 需要先把“研究配置引用哪一个已冻结策略定义”固化下来，
 * 这样 GateF-2 开始建设回测执行链时，才能基于稳定快照而不是运行时可变定义继续扩展。
 */
@Service
public class ResearchConfigService {

    private final ResearchConfigRepository researchConfigRepository;
    private final SourceStrategySnapshotRepository sourceStrategySnapshotRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public ResearchConfigService(
            ResearchConfigRepository researchConfigRepository,
            SourceStrategySnapshotRepository sourceStrategySnapshotRepository,
            ObjectMapper objectMapper
    ) {
        this(researchConfigRepository, sourceStrategySnapshotRepository, objectMapper, Clock.systemUTC());
    }

    public ResearchConfigService(
            ResearchConfigRepository researchConfigRepository,
            SourceStrategySnapshotRepository sourceStrategySnapshotRepository,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.researchConfigRepository = Objects.requireNonNull(
                researchConfigRepository,
                "researchConfigRepository must not be null"
        );
        this.sourceStrategySnapshotRepository = Objects.requireNonNull(
                sourceStrategySnapshotRepository,
                "sourceStrategySnapshotRepository must not be null"
        );
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 创建研究配置。
     * Why:
     * 创建时必须复制 source strategy 快照，避免 research_config 在后续读取时隐式依赖 strategy_definitions 的可变内容。
     */
    public ResearchConfig create(ResearchConfigCreateRequest request) {
        validateCreateRequest(request);
        SourceStrategySnapshot sourceStrategy = sourceStrategySnapshotRepository.findByStrategyId(
                        requireText(request.sourceStrategyId(), "sourceStrategyId")
                )
                .orElseThrow(() -> new IllegalArgumentException(
                        "source strategy definition not found: " + request.sourceStrategyId()
                ));
        Instant now = Instant.now(clock);
        ResearchConfig researchConfig = new ResearchConfig(
                "rcf-" + UUID.randomUUID(),
                sourceStrategy.strategyId(),
                serializeStrategySnapshot(sourceStrategy),
                requireText(request.name(), "name"),
                normalizeNullableText(request.description()),
                normalizeJson(request.parameterSchema()),
                normalizeJson(request.parameterDefaults()),
                normalizeJson(request.datasetSpec()),
                now,
                now
        );
        researchConfigRepository.insert(researchConfig);
        return researchConfig;
    }

    /**
     * 查询单个研究配置。
     * Why:
     * HTTP 查询和回测配置创建都需要以 researchConfigId 为稳定入口，而不是重新回查 strategy_definitions。
     */
    public ResearchConfig getByResearchConfigId(String researchConfigId) {
        return researchConfigRepository.findByResearchConfigId(requireText(researchConfigId, "researchConfigId"))
                .orElseThrow(() -> new IllegalArgumentException(
                        "research config not found: " + researchConfigId
                ));
    }

    public List<ResearchConfig> listAll() {
        return researchConfigRepository.listAll();
    }

    private void validateCreateRequest(ResearchConfigCreateRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        requireText(request.sourceStrategyId(), "sourceStrategyId");
        requireText(request.name(), "name");
        normalizeJson(request.parameterSchema());
        normalizeJson(request.parameterDefaults());
        normalizeJson(request.datasetSpec());
    }

    private String serializeStrategySnapshot(SourceStrategySnapshot sourceStrategySnapshot) {
        try {
            return objectMapper.writeValueAsString(sourceStrategySnapshot);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to serialize source strategy snapshot", ex);
        }
    }

    private String normalizeJson(String value) {
        if (value == null || value.isBlank()) {
            return "{}";
        }
        try {
            return objectMapper.readTree(value.trim()).toString();
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("invalid json payload", ex);
        }
    }

    private String normalizeNullableText(String value) {
        return value == null ? null : value.trim();
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
