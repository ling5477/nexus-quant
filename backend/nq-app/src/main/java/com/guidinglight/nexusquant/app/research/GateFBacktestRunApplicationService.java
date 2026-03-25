package com.guidinglight.nexusquant.app.research;

import com.guidinglight.nexusquant.backtest.model.SimOrder;
import com.guidinglight.nexusquant.backtest.model.SimPnlSnapshot;
import com.guidinglight.nexusquant.backtest.model.SimPosition;
import com.guidinglight.nexusquant.backtest.model.SimTrade;
import com.guidinglight.nexusquant.backtest.service.BacktestFactQueryService;
import com.guidinglight.nexusquant.backtest.service.BacktestExecutionService;
import com.guidinglight.nexusquant.eval.model.BacktestEvaluationReport;
import com.guidinglight.nexusquant.eval.service.BacktestEvaluationService;
import com.guidinglight.nexusquant.research.model.BacktestPublishRecord;
import com.guidinglight.nexusquant.research.model.BacktestPublishRequest;
import com.guidinglight.nexusquant.research.service.BacktestPublishService;
import com.guidinglight.nexusquant.research.model.BacktestRun;
import com.guidinglight.nexusquant.research.service.BacktestRunService;
import com.guidinglight.nexusquant.research.service.BacktestRunStartRequest;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

/**
 * GateFBacktestRunApplicationService 负责把 HTTP 输入映射到回测运行服务。
 */
@Service
public class GateFBacktestRunApplicationService {

    private final BacktestRunService backtestRunService;
    private final BacktestExecutionService backtestExecutionService;
    private final BacktestFactQueryService backtestFactQueryService;
    private final BacktestEvaluationService backtestEvaluationService;
    private final BacktestPublishService backtestPublishService;

    public GateFBacktestRunApplicationService(
            BacktestRunService backtestRunService,
            BacktestExecutionService backtestExecutionService,
            BacktestFactQueryService backtestFactQueryService,
            BacktestEvaluationService backtestEvaluationService,
            BacktestPublishService backtestPublishService
    ) {
        this.backtestRunService = Objects.requireNonNull(
                backtestRunService,
                "backtestRunService must not be null"
        );
        this.backtestExecutionService = Objects.requireNonNull(
                backtestExecutionService,
                "backtestExecutionService must not be null"
        );
        this.backtestFactQueryService = Objects.requireNonNull(
                backtestFactQueryService,
                "backtestFactQueryService must not be null"
        );
        this.backtestEvaluationService = Objects.requireNonNull(
                backtestEvaluationService,
                "backtestEvaluationService must not be null"
        );
        this.backtestPublishService = Objects.requireNonNull(
                backtestPublishService,
                "backtestPublishService must not be null"
        );
    }

    public BacktestRun create(String backtestConfigId) {
        return backtestRunService.create(new BacktestRunStartRequest(backtestConfigId));
    }

    public BacktestRun startExecution(String backtestRunId) {
        backtestExecutionService.startRun(backtestRunId);
        return backtestRunService.getByBacktestRunId(backtestRunId);
    }

    public BacktestRun getByBacktestRunId(String backtestRunId) {
        return backtestRunService.getByBacktestRunId(backtestRunId);
    }

    public List<BacktestRun> list(String researchConfigId, String backtestConfigId) {
        return backtestRunService.list(researchConfigId, backtestConfigId);
    }

    public List<SimOrder> listOrders(String backtestRunId) {
        backtestRunService.getByBacktestRunId(backtestRunId);
        return backtestFactQueryService.listOrders(backtestRunId);
    }

    public List<SimTrade> listTrades(String backtestRunId) {
        backtestRunService.getByBacktestRunId(backtestRunId);
        return backtestFactQueryService.listTrades(backtestRunId);
    }

    public List<SimPosition> listPositions(String backtestRunId) {
        backtestRunService.getByBacktestRunId(backtestRunId);
        return backtestFactQueryService.listPositions(backtestRunId);
    }

    public List<SimPnlSnapshot> listPnlSnapshots(String backtestRunId) {
        backtestRunService.getByBacktestRunId(backtestRunId);
        return backtestFactQueryService.listPnlSnapshots(backtestRunId);
    }

    public BacktestEvaluationReport evaluate(String backtestRunId) {
        backtestRunService.getByBacktestRunId(backtestRunId);
        return backtestEvaluationService.evaluate(backtestRunId);
    }

    public BacktestEvaluationReport getEvaluation(String backtestRunId) {
        backtestRunService.getByBacktestRunId(backtestRunId);
        return backtestEvaluationService.getByBacktestRunId(backtestRunId)
                .orElseThrow(() -> new IllegalArgumentException("evaluation report not found: " + backtestRunId));
    }

    public BacktestEvaluationReport findEvaluationOrNull(String backtestRunId) {
        return backtestEvaluationService.getByBacktestRunId(backtestRunId).orElse(null);
    }

    public BacktestPublishRecord publish(String backtestRunId, String displayName) {
        backtestRunService.getByBacktestRunId(backtestRunId);
        return backtestPublishService.publish(new BacktestPublishRequest(backtestRunId, displayName));
    }

    public BacktestPublishRecord getPublish(String backtestRunId) {
        backtestRunService.getByBacktestRunId(backtestRunId);
        return backtestPublishService.getByBacktestRunId(backtestRunId);
    }

    public BacktestPublishRecord findPublishOrNull(String backtestRunId) {
        return backtestPublishService.findByBacktestRunIdOrNull(backtestRunId);
    }
}
