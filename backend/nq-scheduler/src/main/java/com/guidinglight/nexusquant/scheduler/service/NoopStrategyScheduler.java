package com.guidinglight.nexusquant.scheduler.service;

/**
 * NoopStrategyScheduler 是无副作用调度占位实现。
 */
public class NoopStrategyScheduler implements StrategyScheduler {

    @Override
    public void start(String strategyId, String traceId) {
        // Gate A 占位：不执行真实调度。
    }

    @Override
    public void stop(String strategyId, String traceId) {
        // Gate A 占位：不执行真实调度。
    }

    @Override
    public void restart(String strategyId, String traceId) {
        // Gate A 占位：不执行真实调度。
    }
}
