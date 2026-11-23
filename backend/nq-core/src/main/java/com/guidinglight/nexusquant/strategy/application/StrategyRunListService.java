package com.guidinglight.nexusquant.strategy.application;

import com.guidinglight.nexusquant.strategy.domain.StrategyRunPage;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunListQuery;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunListQueryPort;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 并发产生新运行时保持本次总数与页记录一致；超时仍以失败交给调用方处理。 */
@Service
public class StrategyRunListService {
    private final StrategyRunListQueryPort queryPort;

    public StrategyRunListService(StrategyRunListQueryPort queryPort) {
        this.queryPort = queryPort;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
    public StrategyRunPage list(StrategyRunListQuery query) {
        return queryPort.readPage(query);
    }
}
