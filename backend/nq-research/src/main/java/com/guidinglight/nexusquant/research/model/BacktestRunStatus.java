package com.guidinglight.nexusquant.research.model;

/**
 * BacktestRunStatus 定义 GateF-1 回测运行状态。
 * <p>
 * Why:
 * GateF-1 只创建最小运行骨架，因此状态枚举先保留后续扩展口，
 * 但不提前引入复杂状态机实现，避免把 GateF-2/GateF-3 需求偷跑到本批。
 */
public enum BacktestRunStatus {
    CREATED,
    PREPARING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
