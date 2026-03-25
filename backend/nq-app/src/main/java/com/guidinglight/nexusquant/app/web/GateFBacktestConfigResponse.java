package com.guidinglight.nexusquant.app.web;

import com.guidinglight.nexusquant.research.model.BacktestConfig;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * GateFBacktestConfigResponse 是回测配置响应体。
 */
public record GateFBacktestConfigResponse(
        String backtestConfigId,
        String researchConfigId,
        String name,
        String description,
        Instant startTime,
        Instant endTime,
        BigDecimal initialCapital,
        String executionSpec,
        String evaluationSpec,
        String configSnapshot,
        Instant createdAt,
        Instant updatedAt
) {
    public static GateFBacktestConfigResponse from(BacktestConfig backtestConfig) {
        return new GateFBacktestConfigResponse(
                backtestConfig.backtestConfigId(),
                backtestConfig.researchConfigId(),
                backtestConfig.name(),
                backtestConfig.description(),
                backtestConfig.startTime(),
                backtestConfig.endTime(),
                backtestConfig.initialCapital(),
                backtestConfig.executionSpec(),
                backtestConfig.evaluationSpec(),
                backtestConfig.configSnapshot(),
                backtestConfig.createdAt(),
                backtestConfig.updatedAt()
        );
    }
}
