package com.guidinglight.nexusquant.strategy.application;

import com.guidinglight.nexusquant.strategy.application.exception.StrategyRunReadNotFoundException;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunBusinessEventItem;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunLedgerEntryItem;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunRelatedPage;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunRiskEventItem;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunRelatedQuery;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunRelationQueryPort;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 存在性、总数与页记录使用同次只读快照，超时或缺失不能变成“无风险/无账务”。 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
public class StrategyRunRelationService {
    private final StrategyRunRelationQueryPort port;

    public StrategyRunRelationService(StrategyRunRelationQueryPort port) {
        this.port = port;
    }

    public StrategyRunRelatedPage<StrategyRunRiskEventItem> riskEvents(
            long owner, StrategyRunRelatedQuery query) {
        requireRun(owner, query.strategyRunId());
        return port.readRiskEvents(owner, query);
    }

    public StrategyRunRelatedPage<StrategyRunLedgerEntryItem> ledgerEntries(
            long owner, StrategyRunRelatedQuery query) {
        requireRun(owner, query.strategyRunId());
        return port.readLedgerEntries(owner, query);
    }

    /** 系统消息若没有正式对象归属不会被猜测纳入本运行。 */
    public StrategyRunRelatedPage<StrategyRunBusinessEventItem> businessEvents(
            long owner, StrategyRunRelatedQuery query) {
        requireRun(owner, query.strategyRunId());
        return port.readBusinessEvents(owner, query);
    }

    private void requireRun(long owner, String id) {
        if (owner <= 0) throw new IllegalArgumentException("positive server owner required");
        if (!port.visibleRunExists(owner, id)) throw new StrategyRunReadNotFoundException();
    }
}
