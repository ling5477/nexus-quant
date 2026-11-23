package com.guidinglight.nexusquant.strategy.application.exception;

/** 不存在的运行须与已存在但无订单/成交的运行区分；只用于读取失败，不触发创建或执行。 */
public class StrategyRunReadNotFoundException extends RuntimeException {
    public StrategyRunReadNotFoundException() {
        super("strategy run not found");
    }
}
