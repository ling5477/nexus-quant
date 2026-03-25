package com.guidinglight.nexusquant.research.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.research.model.BacktestConfig;
import com.guidinglight.nexusquant.research.model.ResearchConfig;
import com.guidinglight.nexusquant.research.port.BacktestConfigRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * BacktestConfigService 提供 GateF-1 的回测配置管理能力。
 * <p>
 * Why:
 * backtest_config 必须独立于 research_config 固化运行窗口与执行参数，
 * 这样后续回测运行可以复用同一 research_config 派生多个配置，而不是不断修改研究配置本身。
 */
@Service
public class BacktestConfigService {

    private final BacktestConfigRepository backtestConfigRepository;
    private final ResearchConfigService researchConfigService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public BacktestConfigService(
            BacktestConfigRepository backtestConfigRepository,
            ResearchConfigService researchConfigService,
            ObjectMapper objectMapper
    ) {
        this(backtestConfigRepository, researchConfigService, objectMapper, Clock.systemUTC());
    }

    public BacktestConfigService(
            BacktestConfigRepository backtestConfigRepository,
            ResearchConfigService researchConfigService,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.backtestConfigRepository = Objects.requireNonNull(
                backtestConfigRepository,
                "backtestConfigRepository must not be null"
        );
        this.researchConfigService = Objects.requireNonNull(
                researchConfigService,
                "researchConfigService must not be null"
        );
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 创建回测配置。
     * Why:
     * GateF-1 明确要求 backtest_config 归属于 research_config，因此创建时必须先验证 research_config 存在，
     * 避免产生孤儿配置并破坏后续回测运行血缘。
     */
    public BacktestConfig create(BacktestConfigCreateRequest request) {
        validateCreateRequest(request);
        ResearchConfig researchConfig = researchConfigService.getByResearchConfigId(request.researchConfigId());
        Instant now = Instant.now(clock);
        BacktestConfig backtestConfig = new BacktestConfig(
                "bcf-" + UUID.randomUUID(),
                researchConfig.researchConfigId(),
                requireText(request.name(), "name"),
                normalizeNullableText(request.description()),
                request.startTime(),
                request.endTime(),
                request.initialCapital().stripTrailingZeros(),
                normalizeJson(request.executionSpec()),
                normalizeJson(request.evaluationSpec()),
                buildConfigSnapshot(request),
                now,
                now
        );
        backtestConfigRepository.insert(backtestConfig);
        return backtestConfig;
    }

    public BacktestConfig getByBacktestConfigId(String backtestConfigId) {
        return backtestConfigRepository.findByBacktestConfigId(requireText(backtestConfigId, "backtestConfigId"))
                .orElseThrow(() -> new IllegalArgumentException(
                        "backtest config not found: " + backtestConfigId
                ));
    }

    public List<BacktestConfig> listByResearchConfigId(String researchConfigId) {
        researchConfigService.getByResearchConfigId(researchConfigId);
        return backtestConfigRepository.listByResearchConfigId(requireText(researchConfigId, "researchConfigId"));
    }

    private void validateCreateRequest(BacktestConfigCreateRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        requireText(request.researchConfigId(), "researchConfigId");
        requireText(request.name(), "name");
        Objects.requireNonNull(request.startTime(), "startTime must not be null");
        Objects.requireNonNull(request.endTime(), "endTime must not be null");
        if (!request.endTime().isAfter(request.startTime())) {
            throw new IllegalArgumentException("endTime must be after startTime");
        }
        if (request.initialCapital() == null || request.initialCapital().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("initialCapital must be positive");
        }
        normalizeJson(request.executionSpec());
        normalizeJson(request.evaluationSpec());
    }

    private String buildConfigSnapshot(BacktestConfigCreateRequest request) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode snapshot = objectMapper.createObjectNode();
            snapshot.put("startTime", request.startTime().toString());
            snapshot.put("endTime", request.endTime().toString());
            snapshot.put("initialCapital", request.initialCapital().stripTrailingZeros().toPlainString());
            snapshot.set("executionSpec", objectMapper.readTree(normalizeJson(request.executionSpec())));
            snapshot.set("evaluationSpec", objectMapper.readTree(normalizeJson(request.evaluationSpec())));
            return snapshot.toString();
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to build backtest config snapshot", ex);
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
