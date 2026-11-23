package com.guidinglight.nexusquant.strategy.domain.port;

import com.guidinglight.nexusquant.strategy.domain.StrategySchedulePage;

/** 仅供页面的有界读取，不替代内部扫描需要的全部计划清单。 */
public interface StrategyScheduleListQueryPort {
    StrategySchedulePage readPage(StrategyScheduleListQuery query);
}
