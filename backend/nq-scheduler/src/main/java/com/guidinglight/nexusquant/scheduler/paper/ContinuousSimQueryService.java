package com.guidinglight.nexusquant.scheduler.paper;

import com.guidinglight.nexusquant.research.application.paper.service.PaperTradingRunService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** 执行服务关闭后仍可读取原游标；先校验原运行可见性，不能用关闭执行绕过来源所有者。 */
@Service
@ConditionalOnProperty(prefix = "nq.strategy-sim", name = "enabled", havingValue = "true")
public class ContinuousSimQueryService {
    private final ContinuousSimRepository progress;
    private final StrategySimRunService sim;
    private final PaperTradingRunService runs;

    public ContinuousSimQueryService(
            ContinuousSimRepository progress,
            StrategySimRunService sim,
            PaperTradingRunService runs) {
        this.progress = Objects.requireNonNull(progress);
        this.sim = Objects.requireNonNull(sim);
        this.runs = Objects.requireNonNull(runs);
    }

    /** 同一快照读取存在性、游标和最近决策；不存在连续游标与读取失败分开。 */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 10)
    public ContinuousSimRunService.Status find(String runId) {
        runs.getById(runId);
        if (!progress.exists(runId)) return null;
        var recent = sim.decisions(runId);
        return ContinuousSimRunService.describe(
                progress.get(runId), recent.isEmpty() ? null : recent.getFirst());
    }
}
