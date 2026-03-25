package com.guidinglight.nexusquant.research.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * BacktestConfig 表示 GateF-1 的回测配置事实。
 * <p>
 * Why:
 * 回测配置从属于研究配置，但需要独立固化运行窗口、初始资金和执行参数，
 * 这样 GateF-2 以后接入真实运行链时，仍可以围绕同一个配置对象扩展，而不污染执行域对象。
 */
public record BacktestConfig(
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
}
