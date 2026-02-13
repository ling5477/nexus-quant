package com.guidinglight.nexusquant.scheduler.service;

/**
 * StrategyScheduler 定义策略实例生命周期编排接口。
 */
public interface StrategyScheduler {

    /**
     * 启动指定策略实例。
     */
    void start(String strategyId, String traceId);

    /**
     * 停止指定策略实例。
     */
    void stop(String strategyId, String traceId);

    /**
     * 重启指定策略实例。
     */
    void restart(String strategyId, String traceId);
}
