package com.guidinglight.nexusquant.strategy.domain;

/**
 * StrategyRunStatus 表示执行与补偿契约当前最小运行状态。
 */
public enum StrategyRunStatus {
    CREATED,
    DISPATCHING,
    RUNNING,
    SUCCEEDED,
    FAILED
}

