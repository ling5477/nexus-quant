package com.guidinglight.nexusquant.research.application;

import com.guidinglight.nexusquant.research.application.backtest.command.BacktestRunStartRequest;
import com.guidinglight.nexusquant.research.application.config.BacktestConfigService;
import com.guidinglight.nexusquant.research.domain.BacktestConfig;
import com.guidinglight.nexusquant.research.domain.BacktestRun;
import com.guidinglight.nexusquant.research.domain.BacktestRunStatus;
import com.guidinglight.nexusquant.research.domain.ResearchConfig;
import com.guidinglight.nexusquant.research.domain.port.BacktestRunRepository;
import com.guidinglight.nexusquant.research.domain.port.ResearchFeeSourcePort;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * BacktestRunService 提供研究配置 / 回测执行的回测运行创建与查询能力。
 * <p>
 * Why:
 * 研究配置的目标不是执行回测，而是先把 run 级事实和快照链路固化下来，
 * 让回测执行以后接入市场数据与执行主链时，不需要再回头拆表或补血缘字段。
 */
@Service
public class BacktestRunService {

    private final BacktestRunRepository backtestRunRepository;
    private final BacktestConfigService backtestConfigService;
    private final ResearchConfigService researchConfigService;
    private final Clock clock;
    private ResearchFeeSourcePort feeSource = ResearchFeeSourcePort.disabled();

    /** 独立构造默认拒绝私有来源；正式容器才注入认证和不可变观察的解析合同。 */
    @Autowired
    private void bindFeeSource(ObjectProvider<ResearchFeeSourcePort> provider) {
        feeSource = provider.getIfAvailable(ResearchFeeSourcePort::disabled);
    }

    @Autowired
    public BacktestRunService(
            BacktestRunRepository backtestRunRepository,
            BacktestConfigService backtestConfigService,
            ResearchConfigService researchConfigService) {
        this(
                backtestRunRepository,
                backtestConfigService,
                researchConfigService,
                Clock.systemUTC());
    }

    public BacktestRunService(
            BacktestRunRepository backtestRunRepository,
            BacktestConfigService backtestConfigService,
            ResearchConfigService researchConfigService,
            Clock clock) {
        this.backtestRunRepository =
                Objects.requireNonNull(
                        backtestRunRepository, "backtestRunRepository must not be null");
        this.backtestConfigService =
                Objects.requireNonNull(
                        backtestConfigService, "backtestConfigService must not be null");
        this.researchConfigService =
                Objects.requireNonNull(
                        researchConfigService, "researchConfigService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 创建回测运行。
     * Why:
     * 研究与回测契约明确要求“创建 run”和“执行 run”拆成两个动作，因此这里仍只负责创建 CREATED 状态的 run，
     * 后续显式 `start` 动作再推动 PREPARING / RUNNING / SUCCEEDED / FAILED。
     * Batch 4-A 后，新 run 只能从 ACTIVE research/backtest config 创建；DISABLED 与 ARCHIVED
     * 仍可按 ID 查询用于历史追溯，但不能继续产生新的回测事实。
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ, timeout = 10)
    public BacktestRun create(BacktestRunStartRequest request) {
        return createInternal(request, null);
    }

    /** 同一实验沿用原Run的冻结成本与规则；只能引用可访问且父配置、数据完全一致的正式Run。 */
    @Transactional(isolation = Isolation.REPEATABLE_READ, timeout = 10)
    public BacktestRun createWithFrozenAssumptions(BacktestRunStartRequest request, String referenceRunId) {
        return createInternal(request, getByBacktestRunId(referenceRunId));
    }

    private BacktestRun createInternal(BacktestRunStartRequest request, BacktestRun reference) {
        Objects.requireNonNull(request, "request must not be null");
        BacktestConfig backtestConfig =
                backtestConfigService.getByBacktestConfigId(
                        requireText(request.backtestConfigId(), "backtestConfigId"));
        ResearchConfig researchConfig =
                researchConfigService.getByResearchConfigId(backtestConfig.researchConfigId());
        ensureActiveConfig(backtestConfig, researchConfig);
        Instant now = Instant.now(clock);
        // 新运行再次检查来源有效期；完整快照只冻结一次，后续执行和发布不得改用最新费率。
        String frozenConfiguration;
        if (reference == null) {
            frozenConfiguration = feeSource.freezeRunConfiguration(
                        backtestConfig.configSnapshotJson(),
                        backtestConfig.datasetSnapshotJson(),
                        now);
        } else {
            var original = backtestConfigService.getByBacktestConfigId(reference.backtestConfigId());
            try {
                var reader = new com.fasterxml.jackson.databind.ObjectMapper().reader()
                        .with(com.fasterxml.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
                if (!reference.researchConfigId().equals(researchConfig.researchConfigId())
                        || !reader.readTree(original.configSnapshotJson()).equals(reader.readTree(backtestConfig.configSnapshotJson()))
                        || !reader.readTree(reference.datasetSnapshotJson()).equals(reader.readTree(backtestConfig.datasetSnapshotJson())))
                    throw new IllegalArgumentException("FROZEN_RUN_ASSUMPTIONS_MISMATCH");
                frozenConfiguration = reference.configSnapshotJson();
                feeSource.validateFrozenExecution(reader.readTree(frozenConfiguration).path("executionSpec").toString(),
                        reference.datasetSnapshotJson());
            } catch (java.io.IOException failure) {
                throw new IllegalArgumentException("invalid frozen reference run", failure);
            }
        }
        BacktestRun backtestRun =
                new BacktestRun(
                        "brn-" + UUID.randomUUID(),
                        backtestConfig.backtestConfigId(),
                        researchConfig.researchConfigId(),
                        researchConfig.sourceStrategyId(),
                        researchConfig.strategySnapshot(),
                        backtestConfig.strategyVersionId(),
                        backtestConfig.strategyVersionSnapshotJson(),
                        backtestConfig.paramSnapshotJson(),
                        frozenConfiguration,
                        frozenConfiguration,
                        backtestConfig.datasetSnapshotJson(),
                        BacktestRunStatus.CREATED,
                        now,
                        null,
                        null,
                        null,
                        null,
                        "{}",
                        now,
                        now);
        backtestRunRepository.insert(backtestRun);
        return backtestRun;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ, timeout = 10)
    public BacktestRun start(BacktestRunStartRequest request) {
        return create(request);
    }

    public BacktestRun getByBacktestRunId(String backtestRunId) {
        return backtestRunRepository
                .findByBacktestRunId(requireText(backtestRunId, "backtestRunId"))
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "backtest run not found: " + backtestRunId));
    }

    /**
     * 列出回测运行。
     * Why:
     * 研究与回测契约只提供最小查询面，因此允许按 researchConfigId 或 backtestConfigId 做轻量过滤，
     * 不提前引入分页、复杂筛选和读侧聚合工程。
     */
    public List<BacktestRun> list(String researchConfigId, String backtestConfigId) {
        String normalizedResearchConfigId = normalizeOptionalText(researchConfigId);
        String normalizedBacktestConfigId = normalizeOptionalText(backtestConfigId);
        if (normalizedResearchConfigId != null) {
            researchConfigService.getByResearchConfigId(normalizedResearchConfigId);
        }
        if (normalizedBacktestConfigId != null) {
            backtestConfigService.getByBacktestConfigId(normalizedBacktestConfigId);
        }
        return backtestRunRepository.list(normalizedResearchConfigId, normalizedBacktestConfigId);
    }

    private String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void ensureActiveConfig(BacktestConfig backtestConfig, ResearchConfig researchConfig) {
        if (!backtestConfig.isActive()) {
            throw new IllegalStateException(
                    "backtest config is not active: " + backtestConfig.backtestConfigId());
        }
        if (!researchConfig.isActive()) {
            throw new IllegalStateException(
                    "research config is not active: " + researchConfig.researchConfigId());
        }
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
