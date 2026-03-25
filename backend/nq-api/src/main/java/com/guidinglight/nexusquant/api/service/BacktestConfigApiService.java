package com.guidinglight.nexusquant.api.service;

import com.guidinglight.nexusquant.research.model.BacktestConfig;
import com.guidinglight.nexusquant.research.service.BacktestConfigCreateRequest;
import com.guidinglight.nexusquant.research.service.BacktestConfigService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import org.springframework.stereotype.Service;

/**
 * BacktestConfigApiService 负责把 HTTP 请求映射到回测配置领域服务。
 *
 * Why:
 * 回测配置仍属于研究域事实，但 controller 侧的参数编排应该收口到 `nq-api`，
 * 这样 `nq-app` 可以只保留启动与 wiring 职责。
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
}
