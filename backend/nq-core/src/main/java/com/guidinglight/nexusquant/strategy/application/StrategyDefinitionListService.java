package com.guidinglight.nexusquant.strategy.application;

import com.guidinglight.nexusquant.strategy.domain.StrategyDefinitionPage;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyDefinitionListQuery;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyDefinitionListQueryPort;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 两次SQL共用只读快照，避免并发启停让总数和页记录口径不一致。 */
@Service
public class StrategyDefinitionListService {
    private final StrategyDefinitionListQueryPort queryPort;

    public StrategyDefinitionListService(StrategyDefinitionListQueryPort queryPort) {
        this.queryPort = queryPort;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
    public StrategyDefinitionPage list(StrategyDefinitionListQuery query) {
        return queryPort.readPage(query);
    }
}
