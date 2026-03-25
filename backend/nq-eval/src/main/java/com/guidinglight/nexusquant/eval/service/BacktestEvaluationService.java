package com.guidinglight.nexusquant.eval.service;

import com.guidinglight.nexusquant.eval.model.BacktestEvaluationReport;
import com.guidinglight.nexusquant.eval.model.EvaluationStatus;
import com.guidinglight.nexusquant.eval.port.BacktestEvaluationReportRepository;
import com.guidinglight.nexusquant.eval.port.SimOrderQueryRepository;
import com.guidinglight.nexusquant.eval.port.SimPnlSnapshotQueryRepository;
import com.guidinglight.nexusquant.eval.port.SimPositionQueryRepository;
import com.guidinglight.nexusquant.eval.port.SimTradeQueryRepository;
import com.guidinglight.nexusquant.research.model.BacktestRunStatus;
import com.guidinglight.nexusquant.research.service.BacktestConfigService;
import com.guidinglight.nexusquant.research.service.BacktestRunService;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

/**
 * BacktestEvaluationService 提供 GateF-4 的显式 evaluate 主链。
 */
@Service
public class BacktestEvaluationService {

    private final BacktestRunService backtestRunService;
    private final BacktestConfigService backtestConfigService;
    private final SimOrderQueryRepository simOrderQueryRepository;
    private final SimTradeQueryRepository simTradeQueryRepository;
    private final SimPositionQueryRepository simPositionQueryRepository;
    private final SimPnlSnapshotQueryRepository simPnlSnapshotQueryRepository;
    private final EvaluationMetricCalculator evaluationMetricCalculator;
    private final BacktestEvaluationReportRepository backtestEvaluationReportRepository;
    private final Clock clock;

    public BacktestEvaluationService(
            BacktestRunService backtestRunService,
            BacktestConfigService backtestConfigService,
            SimOrderQueryRepository simOrderQueryRepository,
            SimTradeQueryRepository simTradeQueryRepository,
            SimPositionQueryRepository simPositionQueryRepository,
            SimPnlSnapshotQueryRepository simPnlSnapshotQueryRepository,
            EvaluationMetricCalculator evaluationMetricCalculator,
            BacktestEvaluationReportRepository backtestEvaluationReportRepository
    ) {
        this(
                backtestRunService,
                backtestConfigService,
                simOrderQueryRepository,
                simTradeQueryRepository,
                simPositionQueryRepository,
                simPnlSnapshotQueryRepository,
                evaluationMetricCalculator,
                backtestEvaluationReportRepository,
                Clock.systemUTC()
        );
    }

    BacktestEvaluationService(
            BacktestRunService backtestRunService,
            BacktestConfigService backtestConfigService,
            SimOrderQueryRepository simOrderQueryRepository,
            SimTradeQueryRepository simTradeQueryRepository,
            SimPositionQueryRepository simPositionQueryRepository,
            SimPnlSnapshotQueryRepository simPnlSnapshotQueryRepository,
            EvaluationMetricCalculator evaluationMetricCalculator,
            BacktestEvaluationReportRepository backtestEvaluationReportRepository,
            Clock clock
    ) {
        this.backtestRunService = Objects.requireNonNull(backtestRunService, "backtestRunService must not be null");
        this.backtestConfigService = Objects.requireNonNull(
                backtestConfigService,
                "backtestConfigService must not be null"
        );
        this.simOrderQueryRepository = Objects.requireNonNull(
                simOrderQueryRepository,
                "simOrderQueryRepository must not be null"
        );
        this.simTradeQueryRepository = Objects.requireNonNull(
                simTradeQueryRepository,
                "simTradeQueryRepository must not be null"
        );
        this.simPositionQueryRepository = Objects.requireNonNull(
                simPositionQueryRepository,
                "simPositionQueryRepository must not be null"
        );
        this.simPnlSnapshotQueryRepository = Objects.requireNonNull(
                simPnlSnapshotQueryRepository,
                "simPnlSnapshotQueryRepository must not be null"
        );
        this.evaluationMetricCalculator = Objects.requireNonNull(
                evaluationMetricCalculator,
                "evaluationMetricCalculator must not be null"
        );
        this.backtestEvaluationReportRepository = Objects.requireNonNull(
                backtestEvaluationReportRepository,
                "backtestEvaluationReportRepository must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public BacktestEvaluationReport evaluate(String backtestRunId) {
        Instant evaluatedAt = Instant.now(clock);
        try {
            var backtestRun = backtestRunService.getByBacktestRunId(backtestRunId);
            if (backtestRun.status() != BacktestRunStatus.SUCCEEDED) {
                throw new IllegalStateException("only SUCCEEDED runs can be evaluated");
            }
            var backtestConfig = backtestConfigService.getByBacktestConfigId(backtestRun.backtestConfigId());
            if (backtestConfig.initialCapital().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException("initialCapital must be positive for evaluation");
            }
            var simOrders = simOrderQueryRepository.listByBacktestRunId(backtestRunId);
            var simTrades = simTradeQueryRepository.listByBacktestRunId(backtestRunId);
            var simPositions = simPositionQueryRepository.listByBacktestRunId(backtestRunId);
            var simPnlSnapshots = simPnlSnapshotQueryRepository.listByBacktestRunId(backtestRunId);
            if (simPnlSnapshots.isEmpty()) {
                throw new IllegalStateException("sim_pnl_snapshots must exist for evaluation");
            }
            BacktestEvaluationReport report = evaluationMetricCalculator.calculate(
                    backtestRunId,
                    backtestConfig.initialCapital(),
                    simOrders,
                    simTrades,
                    simPositions,
                    simPnlSnapshots,
                    evaluatedAt
            );
            backtestEvaluationReportRepository.upsert(report);
            return report;
        } catch (RuntimeException ex) {
            BacktestEvaluationReport failedReport = new BacktestEvaluationReport(
                    "eval-" + UUID.randomUUID(),
                    backtestRunId,
                    EvaluationStatus.FAILED,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "{}",
                    "EVALUATION_FAILED",
                    safeMessage(ex),
                    evaluatedAt,
                    evaluatedAt,
                    evaluatedAt
            );
            backtestEvaluationReportRepository.upsert(failedReport);
            throw new IllegalStateException("backtest evaluation failed: " + safeMessage(ex), ex);
        }
    }

    public Optional<BacktestEvaluationReport> getByBacktestRunId(String backtestRunId) {
        return backtestEvaluationReportRepository.findByBacktestRunId(backtestRunId);
    }

    private String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
