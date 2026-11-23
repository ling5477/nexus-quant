package com.guidinglight.nexusquant.strategy.domain.port;

/** 只消费持久化的无发送终态，不授予或重置外部发送资格。 */
public interface StrategyRunRecoveryRepository {
    int recoverNoSendDispatches(String strategyId, int limit);
}
