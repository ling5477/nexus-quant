package com.guidinglight.nexusquant.research.application.backtest.command;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * BacktestConfigCreateRequest 描述创建回测配置时需要的最小输入。
 */
public record BacktestConfigCreateRequest(
        String researchConfigId,
        String name,
        String description,
        Instant startTime,
        Instant endTime,
        BigDecimal initialCapital,
        String executionSpec,
        String evaluationSpec
) {
}

