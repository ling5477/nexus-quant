package com.guidinglight.nexusquant.strategy.domain.port;

import com.guidinglight.nexusquant.strategy.domain.StrategyRunPage;

/** 仅返回运行元信息；不加载配置快照、订单、成交或另一套运行事实。 */
public interface StrategyRunListQueryPort {
    StrategyRunPage readPage(StrategyRunListQuery query);
}
