package com.guidinglight.nexusquant.strategy.application;

import com.guidinglight.nexusquant.strategy.domain.StrategySchedulePage;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyScheduleListQuery;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyScheduleListQueryPort;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 启停或创建并发发生时，本次总数与页内容使用同一只读快照。 */
@Service
public class StrategyScheduleListService {
    private final StrategyScheduleListQueryPort queryPort;

    public StrategyScheduleListService(StrategyScheduleListQueryPort queryPort) {
        this.queryPort = queryPort;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
    public StrategySchedulePage list(StrategyScheduleListQuery query) {
        return queryPort.readPage(query);
    }
}
