package com.guidinglight.nexusquant.strategy.domain;

/** 精确运行身份、关联数量及诊断身份来自同次读取；数量不是当前页长度。 */
public record StrategyRunOverview(
        StrategyRunSummary run, long orderCount, long tradeCount, String traceId) {
    public StrategyRunOverview {
        if (run == null || orderCount < 0 || tradeCount < 0)
            throw new IllegalArgumentException("invalid run overview");
    }
}
