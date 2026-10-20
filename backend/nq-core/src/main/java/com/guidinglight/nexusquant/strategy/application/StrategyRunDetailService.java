package com.guidinglight.nexusquant.strategy.application;

import com.guidinglight.nexusquant.strategy.application.exception.StrategyRunReadNotFoundException;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunOrderItem;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunOverview;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunRelatedPage;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunTradeItem;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunDetailQueryPort;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunRelatedQuery;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 子页的存在性、总数和记录使用同一只读快照；错误不能降级为空事实。 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
public class StrategyRunDetailService {
    private final StrategyRunDetailQueryPort queryPort;

    public StrategyRunDetailService(StrategyRunDetailQueryPort queryPort) {
        this.queryPort = queryPort;
    }

    public StrategyRunOverview overview(String runId) {
        return queryPort
                .findOverview(StrategyRunRelatedQuery.requireRunId(runId))
                .orElseThrow(StrategyRunReadNotFoundException::new);
    }

    public StrategyRunRelatedPage<StrategyRunOrderItem> orders(StrategyRunRelatedQuery query) {
        requireRun(query.strategyRunId());
        return queryPort.readOrders(query);
    }

    public StrategyRunRelatedPage<StrategyRunTradeItem> trades(StrategyRunRelatedQuery query) {
        requireRun(query.strategyRunId());
        return queryPort.readTrades(query);
    }

    private void requireRun(String runId) {
        if (!queryPort.runExists(runId)) throw new StrategyRunReadNotFoundException();
    }
}
