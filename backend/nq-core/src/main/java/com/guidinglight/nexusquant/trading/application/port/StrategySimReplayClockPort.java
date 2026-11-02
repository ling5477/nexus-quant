package com.guidinglight.nexusquant.trading.application.port;

import java.time.Instant;

/** 仅从已冻结且持久化的策略 SIM 决策读取订单准入时钟。 */
public interface StrategySimReplayClockPort {
    Instant executionTime(long accountId, String clientOrderId);
}
