package com.guidinglight.nexusquant.strategy.application;

import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunRecoveryRepository;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunExecutionRepository;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunRepository;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyExecutionGateway;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.Objects;
import java.util.List;

/** 扫描前有界恢复；是否仍需阻塞由独立的 active-run 查询决定。 */
@Service
public class StrategyRunRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(StrategyRunRecoveryService.class);
    private final StrategyRunRecoveryRepository repository;
    private final StrategyRunExecutionRepository executions;
    private final StrategyRunRepository runs;
    private final StrategyExecutionGateway gateway;

    public StrategyRunRecoveryService(StrategyRunRecoveryRepository repository) {
        this(repository, null, null, null);
    }

    @Autowired
    public StrategyRunRecoveryService(StrategyRunRecoveryRepository repository, StrategyRunExecutionRepository executions,
            StrategyRunRepository runs, StrategyExecutionGateway gateway) {
        this.repository = Objects.requireNonNull(repository);
        this.executions = executions;
        this.runs = runs;
        this.gateway = gateway;
    }

    public int recover(String strategyId) {
        if (strategyId == null || strategyId.isBlank()) {
            throw new IllegalArgumentException("strategyId must not be blank");
        }
        if (executions == null) return repository.recoverNoSendDispatches(strategyId, 50);
        return recoverCandidates(executions.findCandidates(strategyId, 50));
    }

    public int recoverAll() {
        if (executions == null) return 0;
        return recoverCandidates(executions.reserveCandidates(50));
    }

    private int recoverCandidates(List<String> candidates) {
        int changed = 0;
        RuntimeException firstFailure = null;
        for (String runId : candidates) {
            try {
                boolean projected = executions.project(runId);
                var current = runs.findByStrategyRunId(runId).orElseThrow();
                if (current.status() == StrategyRunStatus.FAILED || current.status() == StrategyRunStatus.SUCCEEDED) {
                    if (projected) changed++;
                    continue;
                }
                var work = executions.findWork(runId);
                if (work.isEmpty() || !"OKX".equals(current.exchangeCode())) {
                    log.warn("STRATEGY_RECOVERY_UNRESOLVED runId={} reason=IMMUTABLE_WORK_OR_MUTATION_PROTOCOL_UNAVAILABLE", runId);
                    continue;
                }
                gateway.resume(runId);
                if (projected || current.status() != runs.findByStrategyRunId(runId).orElseThrow().status()) changed++;
            } catch (RuntimeException ex) {
                log.warn("STRATEGY_RECOVERY_UNRESOLVED runId={} failureType={}", runId, ex.getClass().getSimpleName());
                if (firstFailure == null) firstFailure = ex;
            }
        }
        // 独立 run 可继续检查，但调用方必须看见失败，不能将部分进展报告成完整成功。
        if (firstFailure != null) throw new IllegalStateException("strategy recovery contains unresolved execution failure", firstFailure);
        return changed;
    }
}
