package com.guidinglight.nexusquant.research.application.api.backtest;

import com.guidinglight.nexusquant.research.domain.BacktestConfig;
import com.guidinglight.nexusquant.research.application.backtest.command.BacktestConfigCreateRequest;
import com.guidinglight.nexusquant.research.application.config.BacktestConfigService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * BacktestConfigApiService 负责把 HTTP 请求映射到回测配置领域服务。
 *
 * Why:
 * 回测配置仍属于研究域事实。PRE-CLEAN-2 后，controller 侧的参数编排收回到 `nq-research`，
 * 这样 `nq-api` 只保留 HTTP 边界，`nq-app` 只保留启动与 wiring 职责。
 */
@Service
public class BacktestConfigApiService {

    private final BacktestConfigService backtestConfigService;

    public BacktestConfigApiService(BacktestConfigService backtestConfigService) {
        this.backtestConfigService = Objects.requireNonNull(
                backtestConfigService,
                "backtestConfigService must not be null"
        );
    }

    /**
     * 创建回测配置。
     *
     * @param researchConfigId 关联研究配置 ID
     * @param name 回测配置名称
     * @param description 回测配置描述
     * @param startTime 回测起始时间
     * @param endTime 回测结束时间
     * @param initialCapital 初始资金
     * @param executionSpec 执行规则 JSON
     * @param evaluationSpec 评估规则 JSON
     * @return 已持久化的回测配置事实
     */
    public BacktestConfig create(String researchConfigId, String name, String description,
                                 Instant startTime, Instant endTime, BigDecimal initialCapital,
                                 String executionSpec, String evaluationSpec) {
        return backtestConfigService.create(new BacktestConfigCreateRequest(
                researchConfigId,
                name,
                description,
                startTime,
                endTime,
                initialCapital,
                executionSpec,
                evaluationSpec
        ));
    }

    /**
     * 查询回测配置详情。
     *
     * @param backtestConfigId 回测配置 ID
     * @return 回测配置详情
     */
    public BacktestConfig getByBacktestConfigId(String backtestConfigId) {
        try {
            return backtestConfigService.getByBacktestConfigId(backtestConfigId);
        } catch (IllegalArgumentException ex) {
            throw toNotFound(ex);
        }
    }

    /**
     * 查询回测配置列表。
     *
     * @param researchConfigId 研究配置 ID，可空
     * @return 回测配置列表
     */
    public List<BacktestConfig> list(String researchConfigId) {
        try {
            return backtestConfigService.list(researchConfigId);
        } catch (IllegalArgumentException ex) {
            throw toNotFound(ex);
        }
    }

    /**
     * 绑定 GateH-3 marketdata dataset 到回测配置。
     *
     * @param backtestConfigId 回测配置 ID
     * @param datasetId dataset ID
     * @param datasetSnapshotJson dataset 快照 JSON
     * @return 更新后的回测配置
     */
    public BacktestConfig bindDataset(String backtestConfigId, String datasetId, String datasetSnapshotJson) {
        try {
            return backtestConfigService.bindDataset(backtestConfigId, datasetId, datasetSnapshotJson);
        } catch (IllegalArgumentException ex) {
            throw toNotFound(ex);
        }
    }

    /**
     * 绑定 GateI-2 策略版本到回测配置。
     *
     * @param backtestConfigId 回测配置 ID
     * @param strategyVersionId 策略版本 ID
     * @return 更新后的回测配置
     */
    public BacktestConfig bindStrategyVersion(String backtestConfigId, String strategyVersionId) {
        try {
            return backtestConfigService.bindStrategyVersion(backtestConfigId, strategyVersionId);
        } catch (IllegalArgumentException ex) {
            throw toNotFound(ex);
        }
    }

    private ResponseStatusException toNotFound(IllegalArgumentException ex) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
    }
}



