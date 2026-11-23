package com.guidinglight.nexusquant.research.domain;

/**
 * BacktestRunStatus 定义研究与回测契约回测运行状态。
 * <p>
 * Why:
 * 研究配置只创建最小运行骨架，因此状态枚举先保留后续扩展口，
 * 但不提前引入复杂状态机实现，避免把回测执行/模拟成交需求偷跑到本批。
 */
public enum BacktestRunStatus {
    CREATED,
    PREPARING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED
}

