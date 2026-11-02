package com.guidinglight.nexusquant.strategy.domain.port;

import com.guidinglight.nexusquant.strategy.domain.StrategyDefinitionPage;

/** 仅查询既有策略定义事实，不创建另一份策略状态或运行事实源。 */
public interface StrategyDefinitionListQueryPort {
    StrategyDefinitionPage readPage(StrategyDefinitionListQuery query);
}
