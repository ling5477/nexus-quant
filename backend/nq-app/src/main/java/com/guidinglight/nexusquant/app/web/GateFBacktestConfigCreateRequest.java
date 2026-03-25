package com.guidinglight.nexusquant.app.web;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * GateFBacktestConfigCreateRequest 是创建回测配置的 HTTP 请求体。
 */
public record GateFBacktestConfigCreateRequest(
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
