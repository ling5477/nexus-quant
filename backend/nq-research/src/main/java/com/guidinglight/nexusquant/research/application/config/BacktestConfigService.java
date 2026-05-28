package com.guidinglight.nexusquant.research.application.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.research.domain.BacktestConfig;
import com.guidinglight.nexusquant.research.domain.ResearchConfig;
import com.guidinglight.nexusquant.research.domain.StrategyVersionSnapshotView;
import com.guidinglight.nexusquant.research.domain.port.BacktestConfigRepository;
import com.guidinglight.nexusquant.research.domain.port.StrategyVersionSnapshotQueryPort;
import com.guidinglight.nexusquant.research.application.ResearchConfigService;
import com.guidinglight.nexusquant.research.application.backtest.command.BacktestConfigCreateRequest;

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
    private final StrategyVersionSnapshotQueryPort strategyVersionSnapshotQueryPort;
    private final ResearchConfigService researchConfigService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public BacktestConfigService(
            BacktestConfigRepository backtestConfigRepository,
            StrategyVersionSnapshotQueryPort strategyVersionSnapshotQueryPort,
            ResearchConfigService researchConfigService,
            ObjectMapper objectMapper
    ) {
        this(backtestConfigRepository, strategyVersionSnapshotQueryPort, researchConfigService, objectMapper, Clock.systemUTC());
    }

    public BacktestConfigService(
            BacktestConfigRepository backtestConfigRepository,
            ResearchConfigService researchConfigService,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this(backtestConfigRepository, id -> java.util.Optional.empty(), researchConfigService, objectMapper, clock);
    }

    public BacktestConfigService(
            BacktestConfigRepository backtestConfigRepository,
            StrategyVersionSnapshotQueryPort strategyVersionSnapshotQueryPort,
            ResearchConfigService researchConfigService,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.backtestConfigRepository = Objects.requireNonNull(
                backtestConfigRepository,
                "backtestConfigRepository must not be null"
        );
        this.strategyVersionSnapshotQueryPort = Objects.requireNonNull(
                strategyVersionSnapshotQueryPort,
                "strategyVersionSnapshotQueryPort must not be null"
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
                null,
                "{}",
                "{}",
                buildConfigSnapshot(request),
                null,
                "{}",
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

    /**
     * 查询回测配置列表。
     * Why:
     * 控制台联调早期的回测配置页面既需要全量列表，也需要 researchConfig 维度的关联子列表，
     * 这里仅保留 parent 过滤，不提前扩展复杂搜索协议。
     *
     * @param researchConfigId 研究配置 ID，可空
     * @return 满足条件的回测配置列表
     */
    public List<BacktestConfig> list(String researchConfigId) {
        String normalizedResearchConfigId = normalizeOptionalText(researchConfigId);
        if (normalizedResearchConfigId != null) {
            researchConfigService.getByResearchConfigId(normalizedResearchConfigId);
        }
        return backtestConfigRepository.list(normalizedResearchConfigId);
    }

    public List<BacktestConfig> listByResearchConfigId(String researchConfigId) {
        return list(requireText(researchConfigId, "researchConfigId"));
    }

    /**
     * 绑定 marketdata dataset 到回测配置。
     * Why:
     * GateH-3 需要把“配置选择的数据集”和“当时的数据集状态”同时固化；
     * 此方法只更新 backtest_config，不启动回测，也不改变 executionSpec，避免把 dataset 绑定扩展成回测算法变更。
     *
     * @param backtestConfigId 回测配置 ID
     * @param datasetId marketdata dataset ID
     * @param datasetSnapshotJson 绑定时的数据集快照 JSON
     * @return 更新后的回测配置
     */
    public BacktestConfig bindDataset(String backtestConfigId, String datasetId, String datasetSnapshotJson) {
        String normalizedConfigId = requireText(backtestConfigId, "backtestConfigId");
        getByBacktestConfigId(normalizedConfigId);
        String normalizedDatasetId = requireText(datasetId, "datasetId");
        String normalizedSnapshot = normalizeJson(datasetSnapshotJson);
        boolean updated = backtestConfigRepository.bindDataset(
                normalizedConfigId,
                normalizedDatasetId,
                normalizedSnapshot,
                Instant.now(clock)
        );
        if (!updated) {
            throw new IllegalArgumentException("backtest config not found: " + normalizedConfigId);
        }
        return getByBacktestConfigId(normalizedConfigId);
    }

    /**
     * 绑定策略版本到回测配置，并固化版本快照与参数快照。
     * Why:
     * GateI-2 要求回测配置成为 backtest run 的稳定输入边界；这里仅更新配置事实，
     * 不启动回测、不改策略算法、不改回测算法。run 创建时会复制这些快照，保证历史运行可复盘。
     *
     * @param backtestConfigId 回测配置 ID
     * @param strategyVersionId 策略版本 ID
     * @return 更新后的回测配置
     */
    public BacktestConfig bindStrategyVersion(String backtestConfigId, String strategyVersionId) {
        String normalizedConfigId = requireText(backtestConfigId, "backtestConfigId");
        getByBacktestConfigId(normalizedConfigId);
        String normalizedVersionId = requireText(strategyVersionId, "strategyVersionId");
        StrategyVersionSnapshotView snapshot = strategyVersionSnapshotQueryPort.findById(normalizedVersionId)
                .orElseThrow(() -> new IllegalArgumentException("strategy version not found: " + normalizedVersionId));
        boolean updated = backtestConfigRepository.bindStrategyVersion(
                normalizedConfigId,
                normalizedVersionId,
                strategyVersionSnapshotJson(snapshot),
                normalizeJson(snapshot.paramSnapshotJson()),
                Instant.now(clock)
        );
        if (!updated) {
            throw new IllegalArgumentException("backtest config not found: " + normalizedConfigId);
        }
        return getByBacktestConfigId(normalizedConfigId);
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

    private String strategyVersionSnapshotJson(StrategyVersionSnapshotView snapshot) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode node = objectMapper.createObjectNode();
            node.put("strategyVersionId", snapshot.strategyVersionId());
            node.put("strategyCode", snapshot.strategyCode());
            node.put("version", snapshot.version());
            node.put("versionName", snapshot.versionName());
            node.put("status", snapshot.status());
            node.set("paramSnapshotJson", objectMapper.readTree(normalizeJson(snapshot.paramSnapshotJson())));
            node.set("configSnapshotJson", objectMapper.readTree(normalizeJson(snapshot.configSnapshotJson())));
            node.set("sourceSnapshotJson", objectMapper.readTree(normalizeJson(snapshot.sourceSnapshotJson())));
            node.put("checksum", snapshot.checksum());
            return node.toString();
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("failed to build strategy version snapshot", ex);
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

    private String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}



