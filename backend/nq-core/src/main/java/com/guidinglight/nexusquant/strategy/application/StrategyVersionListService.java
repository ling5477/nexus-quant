package com.guidinglight.nexusquant.strategy.application;

import com.guidinglight.nexusquant.strategy.domain.StrategyVersionPage;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyVersionListQuery;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyVersionListQueryPort;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 并发新增版本不能使本次COUNT与页记录错位；读取超时保留为失败，不伪装为空列表。 */
@Service
public class StrategyVersionListService {
    private final StrategyVersionListQueryPort queryPort;

    public StrategyVersionListService(StrategyVersionListQueryPort queryPort) {
        this.queryPort = queryPort;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
    public StrategyVersionPage list(StrategyVersionListQuery query) {
        return queryPort.readPage(query);
    }
}
