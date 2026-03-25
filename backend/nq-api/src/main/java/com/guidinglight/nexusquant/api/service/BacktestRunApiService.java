package com.guidinglight.nexusquant.api.service;

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
 * BacktestRunApiService 负责把 HTTP 请求映射到回测运行、评估和发布主链。
 *
 * Why:
 * 回测运行 API 需要组合 `nq-research`、`nq-backtest` 和 `nq-eval` 的读取与触发能力，
 * 这些仅服务于 controller 的编排逻辑应收敛在 `nq-api`，避免 `nq-app` 继续演化成业务层。
 */
@Service
public class BacktestRunApiService {

    private final BacktestRunService backtestRunService;
    private final BacktestExecutionService backtestExecutionService;
    private final BacktestFactQueryService backtestFactQueryService;
    private final BacktestEvaluationService backtestEvaluationService;
    private final BacktestPublishService backtestPublishService;

    public BacktestRunApiService(
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

    /**
     * 启动一次回测执行。
     *
     * @param backtestRunId 回测运行 ID
     * @return 启动后的最新运行事实
     */
    public BacktestRun startExecution(String backtestRunId) {
        backtestExecutionService.startRun(backtestRunId);
        return backtestRunService.getByBacktestRunId(backtestRunId);
    }

    public BacktestRun getByBacktestRunId(String backtestRunId) {
        return backtestRunService.getByBacktestRunId(backtestRunId);
    }

    /**
     * 按研究配置或回测配置筛选运行列表。
     *
     * @param researchConfigId 研究配置 ID，可空
     * @param backtestConfigId 回测配置 ID，可空
     * @return 运行列表
     */
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
