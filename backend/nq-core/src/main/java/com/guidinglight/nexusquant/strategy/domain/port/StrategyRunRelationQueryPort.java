package com.guidinglight.nexusquant.strategy.domain.port;

import com.guidinglight.nexusquant.strategy.domain.StrategyRunBusinessEventItem;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunLedgerEntryItem;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunRelatedPage;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunRiskEventItem;

/** 已有Order/Trade关系的只读投影；不能借共享trace或模糊文本扩大风险/账务归属。 */
public interface StrategyRunRelationQueryPort {
    StrategyRunRelatedPage<StrategyRunBusinessEventItem> readBusinessEvents(
            long owner, StrategyRunRelatedQuery query);

    boolean visibleRunExists(long owner, String runId);

    StrategyRunRelatedPage<StrategyRunRiskEventItem> readRiskEvents(
            long owner, StrategyRunRelatedQuery query);

    StrategyRunRelatedPage<StrategyRunLedgerEntryItem> readLedgerEntries(
            long owner, StrategyRunRelatedQuery query);
}
