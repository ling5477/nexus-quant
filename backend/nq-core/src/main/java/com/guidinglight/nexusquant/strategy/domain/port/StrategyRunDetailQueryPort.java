package com.guidinglight.nexusquant.strategy.domain.port;

import com.guidinglight.nexusquant.strategy.domain.StrategyRunOrderItem;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunOverview;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunRelatedPage;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunTradeItem;

import java.util.Optional;

/** 使用canonical运行、Order和Trade；不依赖配置快照或不稳定的间接账务关联。 */
public interface StrategyRunDetailQueryPort {
    Optional<StrategyRunOverview> findOverview(String strategyRunId);

    boolean runExists(String strategyRunId);

    StrategyRunRelatedPage<StrategyRunOrderItem> readOrders(StrategyRunRelatedQuery query);

    StrategyRunRelatedPage<StrategyRunTradeItem> readTrades(StrategyRunRelatedQuery query);
}
