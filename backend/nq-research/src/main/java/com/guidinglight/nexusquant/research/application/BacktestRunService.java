package com.guidinglight.nexusquant.research.application;

import com.guidinglight.nexusquant.research.domain.BacktestConfig;
import com.guidinglight.nexusquant.research.domain.BacktestRun;
import com.guidinglight.nexusquant.research.domain.BacktestRunStatus;
import com.guidinglight.nexusquant.research.domain.ResearchConfig;
import com.guidinglight.nexusquant.research.domain.port.BacktestRunRepository;
import com.guidinglight.nexusquant.research.application.config.BacktestConfigService;
import com.guidinglight.nexusquant.research.application.backtest.command.BacktestRunStartRequest;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * BacktestRunService 提供 GateF-1 / GateF-2 的回测运行创建与查询能力。
 * <p>
 * Why:
 * GateF-1 的目标不是执行回测，而是先把 run 级事实和快照链路固化下来，
 * 让 GateF-2 以后接入市场数据与执行主链时，不需要再回头拆表或补血缘字段。
 */
@Service
public class BacktestRunService {

    private final BacktestRunRepository backtestRunRepository;
    private final BacktestConfigService backtestConfigService;
    private final ResearchConfigService researchConfigService;
    private final Clock clock;

    @Autowired
    public BacktestRunService(
            BacktestRunRepository backtestRunRepository,
            BacktestConfigService backtestConfigService,
            ResearchConfigService researchConfigService
    ) {
        this(backtestRunRepository, backtestConfigService, researchConfigService, Clock.systemUTC());
    }

    public BacktestRunService(
            BacktestRunRepository backtestRunRepository,
            BacktestConfigService backtestConfigService,
            ResearchConfigService researchConfigService,
            Clock clock
    ) {
        this.backtestRunRepository = Objects.requireNonNull(
                backtestRunRepository,
                "backtestRunRepository must not be null"
        );
        this.backtestConfigService = Objects.requireNonNull(
                backtestConfigService,
                "backtestConfigService must not be null"
        );
        this.researchConfigService = Objects.requireNonNull(
                researchConfigService,
                "researchConfigService must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 创建回测运行。
     * Why:
     * GateF-2 明确要求“创建 run”和“执行 run”拆成两个动作，因此这里仍只负责创建 CREATED 状态的 run，
     * 后续显式 `start` 动作再推动 PREPARING / RUNNING / SUCCEEDED / FAILED。
     */
    public BacktestRun create(BacktestRunStartRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        BacktestConfig backtestConfig = backtestConfigService.getByBacktestConfigId(
                requireText(request.backtestConfigId(), "backtestConfigId")
        );
        ResearchConfig researchConfig = researchConfigService.getByResearchConfigId(backtestConfig.researchConfigId());
        Instant now = Instant.now(clock);
        BacktestRun backtestRun = new BacktestRun(
                "brn-" + UUID.randomUUID(),
                backtestConfig.backtestConfigId(),
                researchConfig.researchConfigId(),
                researchConfig.sourceStrategyId(),
                researchConfig.strategySnapshot(),
                backtestConfig.configSnapshot(),
                BacktestRunStatus.CREATED,
                now,
                null,
                null,
                null,
                null,
                "{}",
                now,
                now
        );
        backtestRunRepository.insert(backtestRun);
        return backtestRun;
    }

    public BacktestRun start(BacktestRunStartRequest request) {
        return create(request);
    }

    public BacktestRun getByBacktestRunId(String backtestRunId) {
        return backtestRunRepository.findByBacktestRunId(requireText(backtestRunId, "backtestRunId"))
                .orElseThrow(() -> new IllegalArgumentException("backtest run not found: " + backtestRunId));
    }

    /**
     * 列出回测运行。
     * Why:
     * GateF-1 只提供最小查询面，因此允许按 researchConfigId 或 backtestConfigId 做轻量过滤，
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

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}



