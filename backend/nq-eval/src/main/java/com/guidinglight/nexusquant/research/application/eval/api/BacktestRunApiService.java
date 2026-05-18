package com.guidinglight.nexusquant.research.application.eval.api;

import com.guidinglight.nexusquant.research.domain.backtest.SimOrder;
import com.guidinglight.nexusquant.research.domain.backtest.SimPnlSnapshot;
import com.guidinglight.nexusquant.research.domain.backtest.SimPosition;
import com.guidinglight.nexusquant.research.domain.backtest.SimTrade;
import com.guidinglight.nexusquant.research.application.backtest.BacktestFactQueryService;
import com.guidinglight.nexusquant.research.application.backtest.BacktestExecutionService;
import com.guidinglight.nexusquant.research.domain.eval.BacktestEvaluationReport;
import com.guidinglight.nexusquant.research.application.eval.BacktestEvaluationService;
import com.guidinglight.nexusquant.research.domain.BacktestPublishRecord;
import com.guidinglight.nexusquant.research.application.command.BacktestPublishRequest;
import com.guidinglight.nexusquant.research.application.BacktestPublishService;
import com.guidinglight.nexusquant.research.domain.BacktestRun;
import com.guidinglight.nexusquant.research.application.BacktestRunService;
import com.guidinglight.nexusquant.research.application.backtest.command.BacktestRunStartRequest;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * BacktestRunApiService 负责把 HTTP 请求映射到回测运行、评估和发布主链。
 *
 * Why:
 * 回测运行 API 需要组合 `nq-research`、`nq-backtest` 和 `nq-eval` 的读取、评估与发布能力。
 * PRE-CLEAN-2 后，这类跨研究链路编排收回到 eval application owner，避免 `nq-api` 继续演化成业务 façade。
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
        try {
            backtestExecutionService.startRun(backtestRunId);
            return backtestRunService.getByBacktestRunId(backtestRunId);
        } catch (IllegalArgumentException ex) {
            throw toNotFound(ex);
        }
    }

    public BacktestRun getByBacktestRunId(String backtestRunId) {
        try {
            return backtestRunService.getByBacktestRunId(backtestRunId);
        } catch (IllegalArgumentException ex) {
            throw toNotFound(ex);
        }
    }

    /**
     * 按研究配置或回测配置筛选运行列表。
     *
     * @param researchConfigId 研究配置 ID，可空
     * @param backtestConfigId 回测配置 ID，可空
     * @return 运行列表
     */
    public List<BacktestRun> list(String researchConfigId, String backtestConfigId) {
        try {
            return backtestRunService.list(researchConfigId, backtestConfigId);
        } catch (IllegalArgumentException ex) {
            throw toNotFound(ex);
        }
    }

    public List<SimOrder> listOrders(String backtestRunId) {
        getByBacktestRunId(backtestRunId);
        return backtestFactQueryService.listOrders(backtestRunId);
    }

    public List<SimTrade> listTrades(String backtestRunId) {
        getByBacktestRunId(backtestRunId);
        return backtestFactQueryService.listTrades(backtestRunId);
    }

    public List<SimPosition> listPositions(String backtestRunId) {
        getByBacktestRunId(backtestRunId);
        return backtestFactQueryService.listPositions(backtestRunId);
    }

    public List<SimPnlSnapshot> listPnlSnapshots(String backtestRunId) {
        getByBacktestRunId(backtestRunId);
        return backtestFactQueryService.listPnlSnapshots(backtestRunId);
    }

    public BacktestEvaluationReport evaluate(String backtestRunId) {
        getByBacktestRunId(backtestRunId);
        return backtestEvaluationService.evaluate(backtestRunId);
    }

    public BacktestEvaluationReport getEvaluation(String backtestRunId) {
        getByBacktestRunId(backtestRunId);
        return backtestEvaluationService.getByBacktestRunId(backtestRunId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "evaluation report not found: " + backtestRunId
                ));
    }

    public BacktestEvaluationReport findEvaluationOrNull(String backtestRunId) {
        return backtestEvaluationService.getByBacktestRunId(backtestRunId).orElse(null);
    }

    public BacktestPublishRecord publish(String backtestRunId, String displayName) {
        return publish(backtestRunId, displayName, null);
    }

    /**
     * 发布回测结果，并可选绑定 GateI-1 策略版本。
     *
     * @param backtestRunId 回测运行 ID
     * @param displayName 发布展示名，可空
     * @param strategyVersionId 策略版本 ID，可空；非空时 publish record 会固化 version snapshot
     * @return 发布记录
     */
    public BacktestPublishRecord publish(String backtestRunId, String displayName, String strategyVersionId) {
        getByBacktestRunId(backtestRunId);
        return backtestPublishService.publish(new BacktestPublishRequest(backtestRunId, displayName, strategyVersionId));
    }

    public BacktestPublishRecord getPublish(String backtestRunId) {
        getByBacktestRunId(backtestRunId);
        try {
            return backtestPublishService.getByBacktestRunId(backtestRunId);
        } catch (IllegalArgumentException ex) {
            throw toNotFound(ex);
        }
    }

    public BacktestPublishRecord findPublishOrNull(String backtestRunId) {
        return backtestPublishService.findByBacktestRunIdOrNull(backtestRunId);
    }

    public List<BacktestPublishRecord> listPublishes() {
        return backtestPublishService.listAll();
    }

    public BacktestPublishRecord getPublishById(String publishRecordId) {
        try {
            return backtestPublishService.getByPublishRecordId(publishRecordId);
        } catch (IllegalArgumentException ex) {
            throw toNotFound(ex);
        }
    }

    private ResponseStatusException toNotFound(IllegalArgumentException ex) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
    }
}



